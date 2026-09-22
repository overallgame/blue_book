package com.example.blue_book.ui.scan

import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.domain.repository.ScanRepository
import com.example.blue_book.domain.repository.ScanResolveFailure

/**
 * 手写的测试替身。
 *
 * **为什么不用 Mockito**：这里要断言的东西里有两件是"行为"而不是"调用次数"——
 * ① 外部链接/纯文本**不能**打扰服务端（[calls] 必须为空），
 * ② 失败要按领域分类透传出去。手写替身把这两件事写成一眼能看懂的字段，
 * 而 mock 的 `verify(never())` 在失败时的报错信息远不如"calls = [https://…]"直观。
 *
 * [onResolve] 是**可编程的**（默认成功）：测试直接改它就能造出网络失败、被拒、
 * 认不出的异常三条路径，不需要为每种场景再写一个 Fake 子类。
 */
class FakeScanRepository : ScanRepository {

	/** 记录被送去服务端的 payload，用来断言"该调的调了、不该调的没调" */
	val calls = mutableListOf<String>()

	/** 测试用例按需要替换；默认成功返回一条视频内容 */
	var onResolve: suspend (String) -> Result<ScannedContent> = { payload ->
		Result.success(defaultContent(payload))
	}

	override suspend fun resolve(payload: String): Result<ScannedContent> {
		calls += payload
		return onResolve(payload)
	}

	companion object {

		fun defaultContent(title: String = "标题"): ScannedContent = ScannedContent(
			kind = ContentKind.VIDEO,
			targetId = 42L,
			title = title,
			subtitle = "作者",
			cover = "http://host/hls/a.jpg"
		)

		/** 造一个"网络类"失败（可重试） */
		fun networkFailure(): Result<ScannedContent> =
			Result.failure(ScanResolveFailure.Network("网络连接失败，请检查网络设置"))

		/** 造一个"被服务端拒绝"的失败（不可重试，文案直接用服务端的中文） */
		fun rejectedFailure(message: String = "这不是小蓝书的二维码"): Result<ScannedContent> =
			Result.failure(ScanResolveFailure.Rejected(message))
	}
}
