package com.example.blue_book.data.remote.video

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 由 `content://` 构造 [UploadSource]。
 *
 * ## 为什么是"工厂"而不是让上传器直接收 Uri
 *
 * 上传器整个是**平台无关**的（所以能在纯 JVM 上测并发、续传、退化），它只认 [UploadSource]。
 * 而"怎么把 Uri 变成字节"必须是平台代码（[UriUploadSource]）。这个工厂就是两者的接缝：
 * 谁手里有 Uri（发布页）谁负责构造源，上传器永远不认识 Android 类型。
 *
 * 抽成接口的直接收益：消费方（发布页的 ViewModel）不必拿着 `Context` 来 new 一个
 * [UriUploadSource]，它的构造签名里因此没有平台类型，编排逻辑可以纯 JVM 单测。
 */
fun interface UploadSourceFactory {

	/**
	 * @param uri 内容 URI 的**字符串形式**。收字符串而不是 [Uri]：这样消费方
	 *   （[com.example.blue_book.ui.publish.PublishViewModel]）的签名与状态里就没有平台类型，
	 *   `Uri.parse` 这一步留在实现里——而纯 JVM 测试是没法调它的（android.jar 的桩会抛）。
	 *
	 * **是 suspend 而不是普通函数**：构造 [UriUploadSource] 会向 `ContentResolver` 查文件名、
	 * 大小与改动时间，每次都是一轮跨进程调用。声明成 suspend 让"这里会碰 IO"成为**类型的一部分**：
	 * 切换线程由实现负责，调用方（必然在协程里）不可能在主线程上误调。
	 */
	suspend fun create(uri: String): UploadSource
}

/** 真实实现：在 IO 线程上构造 [UriUploadSource]（它的构造里有 ContentResolver 查询）。 */
class AndroidUploadSourceFactory @Inject constructor(
	@ApplicationContext private val appContext: Context
) : UploadSourceFactory {

	override suspend fun create(uri: String): UploadSource =
		withContext(Dispatchers.IO) { UriUploadSource(appContext, Uri.parse(uri)) }
}
