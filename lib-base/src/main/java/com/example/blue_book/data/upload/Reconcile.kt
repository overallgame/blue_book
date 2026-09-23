package com.example.blue_book.data.upload

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus

/**
 * 服务端权威列表 vs 本地分片账本 → 对账后的本地列表。
 *
 * ★ **纯函数**，所以能穷举测试：这是分片表存在的意义所在，也是"以服务端为准"这条规矩的
 * 唯一落地点。它不碰 Room、不碰网络，输入两个集合、输出一串状态修正。
 *
 * ## 四条规则（每条都对应一个真实的坏情况）
 *
 * | 情况 | 修正 | 为什么 |
 * |---|---|---|
 * | 服务端有、本地没有 | 补一条 DONE | 换设备/清了数据后回来续传；本地账本不全不代表服务端没有 |
 * | 服务端有、本地不是 DONE | 改成 DONE | 本地"传失败了"但服务端其实收到了（响应丢包就是这样） |
 * | 服务端没有、本地是 DONE | 打回 PENDING | **本地"传成功"不代表服务端存住了**——请求成功而响应丢失、服务端清理过，都会造成这种不一致。只有服务端是真相 |
 * | 本地是 UPLOADING | 打回 PENDING | 进程被杀时那次传输没有结论，不能当它成功也不能当它失败 |
 *
 * 前两条是"补记"，后两条是"打回"。
 * [UploadPartStatus.FAILED] 保持不变：它只影响退避计数，不改变"这片要重传"的结论。
 *
 * ★ **返回值永远覆盖整个文件的分片区间**（`0 until ceil(fileSize/chunkSize)`）。
 * 这一点是踩过才明白的：早先写成"本地 ∪ 服务端的并集"，于是首次上传（两边都空）会得到一个
 * 空账本——一片都不传、直接去合并，然后报"分片缺失"。账本的语义是
 * "这个文件的每一片各是什么状态"，不是"我见过哪些片"。
 *
 * @param local 本地账本（可能是空、可能不全、可能过时）
 * @param serverChunks 服务端权威的已落盘分片序号
 * @param chunkSize 本会话的分片大小；用于给"本地没有"的片补上 offset/size
 * @param fileSize 文件总大小；用于算出分片总数与末片的实际长度
 */
fun reconcile(
	local: List<UploadPartRecord>,
	serverChunks: Set<Int>,
	chunkSize: Long,
	fileSize: Long
): List<UploadPartRecord> {
	val byIndex = local.associateBy { it.index }
	val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
	// 骨架就是整个文件的分片区间：本地账本里多出来的、超出区间的行会被自然丢掉
	// （那说明它是上一次用不同分片契约留下的，已经没意义了）
	val indexes = (0 until totalChunks).toList()

	return indexes.map { index ->
		val existing = byIndex[index]
		val onServer = index in serverChunks
		val offset = existing?.offset ?: (index * chunkSize)
		val size = existing?.size ?: minOf(chunkSize, fileSize - offset)

		val status = when {
			// 服务端有 ⇒ 就是 DONE，本地说什么都以服务端为准
			onServer -> UploadPartStatus.DONE
			// 服务端没有 ⇒ 必须重传。本地 FAILED 保留（它只影响退避），其余一律打回 PENDING
			existing?.status == UploadPartStatus.FAILED -> UploadPartStatus.FAILED
			else -> UploadPartStatus.PENDING
		}

		UploadPartRecord(
			uri = existing?.uri ?: local.firstOrNull()?.uri.orEmpty(),
			index = index,
			offset = offset,
			size = size,
			status = status,
			retryCount = existing?.retryCount ?: 0
		)
	}
}
