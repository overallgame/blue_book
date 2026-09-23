package com.example.blue_book.data.remote.video

import java.io.InputStream
import java.security.MessageDigest

/**
 * 字节数组实现的内容源，用于纯 JVM 测上传编排。
 *
 * 它替掉的是"从 `content://` 读文件"那一段——那一段在真机上不可控（provider 行为、权限、
 * 管道 vs 文件），而**上传编排**（并发、续传、进度、重试）才是逻辑所在。
 * 这与 `feature-scan` 把相机与解析拆开是同一个理由。
 *
 * [randomAccess] 用来模拟不可随机定位的 provider（管道型）：为 false 时读取器只能向前，
 * 回退会抛异常——这样"退化路径"就是被真的走到了，而不是只改了个标志位。
 */
class ByteArrayUploadSource(
    private val bytes: ByteArray,
    override val name: String = "a.mp4",
    private val randomAccess: Boolean = true,
    override val lastModified: Long? = 1_000L,
    /** 每次读取少给这么多字节，用来模拟"读短了"（文件在传输中被改小/读取出错） */
    private val shortenBy: Int = 0,
    private val digestValue: String = sha256Of(bytes)
) : UploadSource {

    override val key: String = "test://${name}"

    override val size: Long = bytes.size.toLong()

    /** 被打开过多少次读取器：用来断言"退化时只开一个流" */
    var readerOpens: Int = 0
        private set

    /** digest 被算过几次：阶段 3 的"指纹缓存"会用到（命中缓存时应当为 0） */
    var digestCalls: Int = 0
        private set

    override suspend fun digest(): String {
        digestCalls++
        return digestValue
    }

    override fun openReader(): ChunkReader {
        readerOpens++
        return if (randomAccess) RandomAccessReader(bytes, shortenBy) else SequentialReader(bytes, shortenBy)
    }

    fun slice(offset: Long, size: Int): ByteArray {
        val from = offset.toInt()
        val to = minOf(from + size, bytes.size)
        return bytes.copyOfRange(from, to)
    }

    private class RandomAccessReader(private val bytes: ByteArray, private val shortenBy: Int) : ChunkReader {
        override val supportsRandomAccess: Boolean = true

        override fun read(offset: Long, size: Int): ByteArray {
            val from = offset.toInt()
            if (from >= bytes.size) return ByteArray(0)
            val to = (minOf(from + size, bytes.size) - shortenBy).coerceAtLeast(from)
            return bytes.copyOfRange(from, to)
        }

        override fun close() = Unit
    }

    /** 只能向前：回退直接抛，避免"退化路径其实没被走到"这种假绿 */
    private class SequentialReader(private val bytes: ByteArray, private val shortenBy: Int) : ChunkReader {
        private var position = 0

        override val supportsRandomAccess: Boolean = false

        override fun read(offset: Long, size: Int): ByteArray {
            check(offset.toInt() >= position) {
                "顺序读取不支持回退：请求 offset=$offset，当前位置=$position"
            }
            position = offset.toInt()
            if (position >= bytes.size) return ByteArray(0)
            val to = (minOf(position + size, bytes.size) - shortenBy).coerceAtLeast(position)
            val slice = bytes.copyOfRange(position, to)
            position = to
            return slice
        }

        override fun close() = Unit
    }

    private companion object {
        fun sha256Of(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** 让"不可随机读"的源也能被上传：它本身不需要 InputStream，这里只是保持未用导入不报错 */
@Suppress("unused")
private fun unusedInputStreamHint(input: InputStream) = input
