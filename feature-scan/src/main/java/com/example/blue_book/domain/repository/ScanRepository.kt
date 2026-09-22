package com.example.blue_book.domain.repository

import com.example.blue_book.domain.model.ScannedContent

/**
 * 站内码的校验：把扫码得到的原始字符串交给服务端裁定，换回"该跳到哪里、怎么展示"。
 *
 * ## 契约上的两个要点
 *
 * 1. **入参是原始字符串，不是解析出来的 id**。客户端已经在
 *    [com.example.blue_book.scan.ScanCodeFormat.parse] 判过一次"这是哪一类"，
 *    但那次结论只用来决定**要不要调这个接口**，不用来决定参数——判据仍然在服务端。
 *    理由：后端将来加码格式时，落后的 App 版本（改不动）不会给出错误结论。
 * 2. **失败是分类过的**（[ScanResolveFailure]），不是裸异常：调用方据此决定给"重试"还是只提示。
 */
interface ScanRepository {

	/** @param payload 扫码得到的原始字符串（未做任何截取、改写） */
	suspend fun resolve(payload: String): Result<ScannedContent>
}
