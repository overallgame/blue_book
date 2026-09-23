package com.example.blue_book.data.remote.video

import java.io.Closeable

/**
 * 待上传的内容源。
 *
 * 把"怎么取字节"（平台相关，实现只有 [UriUploadSource]）与"怎么传"分开，
 * 于是 [ChunkedUploader] 的编排逻辑可以在纯 JVM 上测：测试塞一个字节数组实现的源即可。
 */
interface UploadSource {

	/** 展示用文件名（决定服务端合并后的扩展名） */
	val name: String

	/** 本地账本按这个键索引（`content://` 源就用 URI 字符串）：同一个 key 即同一个文件 */
	val key: String

	/** 字节数。**必须能读到**：读不到就不能按 -1 继续（会只传首片，得到一个残缺视频） */
	val size: Long

	/** 整文件指纹（MD5），流式计算。它决定服务端的秒传与续传命中 */
	suspend fun digest(): String

	/**
	 * 打开一个**独立的**读取器。
	 *
	 * 每个并发 worker 各持一个：`FileChannel` 的位置是它自己的状态，
	 * 多个协程共用一个 reader 会互相把读位置拽走。
	 */
	fun openReader(): ChunkReader
}

/**
 * 按偏移读取内容。
 *
 * 实现**不必是线程安全的**——约定是"一个 reader 同一时刻只被一个 worker 使用"，
 * 这条约定由 [ChunkedUploader] 的工作池保证（每 worker 一个 reader，不是每片一个）。
 */
interface ChunkReader : Closeable {

	/**
	 * 能否任意定位。
	 *
	 * `false` 表示只能向前顺序读——分片上传会退化成**单流顺序**（并发度 1），
	 * 这正是老实现里那段"只开一次流、偏移单调递增"的路径。
	 * 退化的原因见 [UriUploadSource]：pipe 型 provider 的 `FileChannel.position` 会抛异常。
	 */
	val supportsRandomAccess: Boolean

	/**
	 * 读出 `[offset, offset + size)`。末尾分片可以短于 [size]（返回实际读到的字节）。
	 *
	 * 返回空数组表示已经读到文件末尾。
	 */
	fun read(offset: Long, size: Int): ByteArray
}
