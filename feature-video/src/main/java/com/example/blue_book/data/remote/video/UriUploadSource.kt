package com.example.blue_book.data.remote.video

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject

/**
 * `content://` 的 [UploadSource]。这是整条上传链里**唯一**碰 Android 文件 API 的地方。
 *
 * ## 随机读 vs 顺序读：为什么要探测
 *
 * 分片上传的理想形态是"每个 worker 按偏移取一片"（[ChannelChunkReader]）。但它**不是所有
 * provider 都支持**：`AssetFileDescriptor` 没有 `createInputStream(offset)` 这种 API
 * （只有 `createInputStream()` + `getStartOffset()`，已用 `javap` 核过 android-34），
 * 所以定位只能靠 `FileChannel.position()`，而**管道型**（pipe）的文件描述符上它会抛 `IOException`。
 * 这类 provider 真实存在（某些云盘/相册客户端用管道吐数据）。
 *
 * 所以这里**先探测、探测不过就退化**成单流顺序读（[StreamChunkReader]）：
 * 只开一次流、偏移单调递增。不做这个探测的后果不是"慢"，
 * 而是**一部分用户的视频根本传不上去**。
 */
class UriUploadSource(
	private val appContext: Context,
	private val uri: Uri
) : UploadSource {

	override val name: String = queryName()

	override val key: String = uri.toString()

	override val lastModified: Long? = queryLastModified()

	override val size: Long = querySize() ?: error("无法读取视频文件信息，请重新选择")

	/** MD5 只算一次：它决定服务端的续传/秒传命中，重算一次就要多读一遍整个文件 */
	override suspend fun digest(): String {
		val digest = MessageDigest.getInstance("MD5")
		appContext.contentResolver.openInputStream(uri)?.use { input ->
			val buffer = ByteArray(DIGEST_BUFFER)
			while (true) {
				val read = input.read(buffer)
				if (read <= 0) break
				digest.update(buffer, 0, read)
			}
		}
		return digest.digest().toHex()
	}

	override fun openReader(): ChunkReader = seekableReader() ?: sequentialReader()

	/**
	 * 尝试拿到可随机定位的读取器，拿不到返回 null（由调用方退化）。
	 *
	 * 两道判断都要：`statSize > 0` 是廉价的预筛（管道返回 -1），
	 * 而 `position()` 才是权威（有 provider 报了大小却仍不可定位）。
	 */
	private fun seekableReader(): ChunkReader? {
		val descriptor = try {
			appContext.contentResolver.openAssetFileDescriptor(uri, "r")
		} catch (_: IOException) {
			null
		} catch (_: SecurityException) {
			null
		} ?: return null

		if (descriptor.parcelFileDescriptor?.statSize?.let { it > 0 } != true) {
			descriptor.close()
			return null
		}
		return try {
			val stream = descriptor.createInputStream()
			// 探测：不可定位时这里会抛。position 是**绝对**定位，所以不必关心
			// createInputStream 是否已经按 startOffset 预跳过——两种实现都对
			stream.channel.position(descriptor.startOffset)
			ChannelChunkReader(stream, descriptor.startOffset)
		} catch (_: IOException) {
			descriptor.close()
			null
		}
	}

	private fun sequentialReader(): ChunkReader {
		val input = appContext.contentResolver.openInputStream(uri)
			?: error("无法读取视频文件")
		return StreamChunkReader(input)
	}

	private fun querySize(): Long? {
		val length = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: return null
		return length.takeIf { it > 0 }
	}

	/**
	 * 改动时间：`MediaStore` 用 `DATE_MODIFIED`（秒），SAF/DocumentsProvider 用
	 * `COLUMN_LAST_MODIFIED`（毫秒）。两者只需要"变了没变"，单位不一致无妨。
	 *
	 * 两个列名依次试，都不认就返回 null——调用方据此**不信缓存**而不是拿旧指纹去比对。
	 */
	private fun queryLastModified(): Long? {
		val columns = listOf(
			MediaStore.MediaColumns.DATE_MODIFIED,
			DocumentsContract.Document.COLUMN_LAST_MODIFIED
		)
		for (column in columns) {
			val value = try {
				appContext.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
					if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
				}
			} catch (_: IllegalArgumentException) {
				// provider 不认这一列
				null
			} catch (_: SecurityException) {
				null
			}
			if (value != null && value > 0) return value
		}
		return null
	}

	private fun queryName(): String {
		appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val name = cursor.getString(0)
				if (!name.isNullOrBlank()) return name
			}
		}
		return "video_${System.currentTimeMillis()}.mp4"
	}

	private companion object {
		const val DIGEST_BUFFER = 64 * 1024
	}
}

/** 可随机定位：每次读之前把 channel 挪到绝对偏移。真机上的相册/文件一般走这条 */
private class ChannelChunkReader(
	private val stream: FileInputStream,
	/** AssetFileDescriptor 的起始偏移（整文件描述符时为 0，切片时非 0） */
	private val base: Long
) : ChunkReader {

	override val supportsRandomAccess: Boolean = true

	override fun read(offset: Long, size: Int): ByteArray {
		val channel = stream.channel
		channel.position(base + offset)
		val buffer = ByteBuffer.allocate(size)
		while (buffer.hasRemaining()) {
			if (channel.read(buffer) < 0) break
		}
		// 刚好读满时直接交出内部数组（免一次 1MB 级拷贝）
		return if (buffer.position() == size) buffer.array() else buffer.array().copyOf(buffer.position())
	}

	override fun close() {
		stream.close()
	}
}

/**
 * 只能向前：跳过到目标偏移再读。**不能回退**——回退意味着要从头重读，那正是老实现
 * 特意避开的 O(n²)；真要回退应当换一个源，而不是在这里偷偷重开流。
 */
private class StreamChunkReader(private val input: InputStream) : ChunkReader {

	private var position = 0L

	override val supportsRandomAccess: Boolean = false

	override fun read(offset: Long, size: Int): ByteArray {
		check(offset >= position) { "顺序读取不支持回退：请求 offset=$offset，当前位置=$position" }
		skipFully(input, offset - position)
		position = offset
		val bytes = readFully(input, size)
		position += bytes.size
		return bytes
	}

	override fun close() {
		input.close()
	}
}

/** 跳过量：`skip` 的返回值可能小于请求量，循环补齐；不支持 skip 时退化为读取丢弃 */
private fun skipFully(input: InputStream, target: Long) {
	var skipped = 0L
	while (skipped < target) {
		val n = input.skip(target - skipped)
		if (n > 0) {
			skipped += n
			continue
		}
		if (input.read() < 0) return
		skipped++
	}
}

/** 读取最多 max 字节（末尾分片允许不足） */
private fun readFully(input: InputStream, max: Int): ByteArray {
	val buffer = ByteArray(max)
	var offset = 0
	while (offset < max) {
		val read = input.read(buffer, offset, max - offset)
		if (read <= 0) break
		offset += read
	}
	return if (offset == max) buffer else buffer.copyOf(offset)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
