package com.example.blue_book.data.remote.video

/**
 * 分片上传的对外契约（实现见 [ChunkedUploader]）。
 *
 * 抽成接口是为了发布页的编排可测：取消、续传、进度这些逻辑只有在 uploader
 * 能被替换成假实现时才谈得上单测。与 `feature-scan` 的 `BarcodeScanner` 同一套做法。
 */
interface ChunkUploader {

	/**
	 * 把 [source] 分片上传，返回服务端确认的 filePath。
	 *
	 * [onProgress] 回调 0-100。**可能因续传回填而重复上报**，且并发上传时回调来自多个协程，
	 * 调用方应取 `maxOf` 保持只增不减（`setState` 本身是线程安全的）。
	 *
	 * 失败时抛异常（取消异常原样透传）；可重入：同一文件会命中服务端已落盘的分片。
	 */
	suspend fun upload(source: UploadSource, onProgress: (Int) -> Unit): String

	/**
	 * 放弃一个服务端会话（立刻释放它的分片磁盘，不必等 24 小时的过期清理）。
	 *
	 * 失败不抛给调用方：放弃上传是"我已经不要了"，为了清理失败而报错只会打扰用户。
	 */
	suspend fun abort(uploadId: String)

	/**
	 * **只读**地问服务端：这条（本地记着的）会话传到哪了，返回 0-100。
	 *
	 * 返回 null 表示"问不出来"——没有本地会话、会话还没有 uploadId、网络失败、或没有权限。
	 * 调用方据此**回退到本地账本的数字**，所以它是个纯增益的查询而不是必答的依赖。
	 *
	 * 为什么单独要这个而不是复用 `init`：`init` **有副作用**（可能新建会话、可能作废旧会话），
	 * 而"进发布页时把进度刷新成服务端的真实值"这件事不该有副作用。
	 */
	suspend fun serverProgress(uri: String): Int?
}
