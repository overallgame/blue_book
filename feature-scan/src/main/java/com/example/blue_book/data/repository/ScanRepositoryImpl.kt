package com.example.blue_book.data.repository

import com.example.blue_book.data.mapper.toScannedContent
import com.example.blue_book.data.remote.ScanRemoteDataSource
import com.example.blue_book.domain.model.ScannedContent
import com.example.blue_book.domain.repository.ScanRepository
import com.example.blue_book.domain.repository.ScanResolveFailure
import com.example.blue_book.network.ApiGateway
import com.example.blue_book.network.exception.isRetryableNetworkFailure
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepositoryImpl @Inject constructor(
	private val remote: ScanRemoteDataSource
) : ScanRepository {

	override suspend fun resolve(payload: String): Result<ScannedContent> {
		val dto = remote.resolve(payload).getOrElse { return Result.failure(it.toScanResolveFailure()) }
		val content = dto.toScannedContent(ApiGateway.BASE_URL)
			?: return Result.failure(
				// 服务端给了我们看不懂的东西：多半是后端先上了新码类型。
				// 这是**不可重试**的失败，文案要指向"升级"而不是"重试"。
				ScanResolveFailure.Rejected("暂不支持该二维码，请升级 App 后重试")
			)
		return Result.success(content)
	}
}

/**
 * 把数据层的异常翻译成领域分类。
 *
 * **抽成顶层纯函数是为了能测**：`ScanRemoteDataSource` 是具体类且需要 Android 环境，
 * 挂在它后面的仓库在纯 JVM 上构造不出来。而这里是"给不给重试"的**全部判据**，
 * 是失败矩阵里唯一由代码（而不是文案）决定的分支，必须能被穷举测试。
 *
 * 两条规则：
 * 1. **只有网络类与 5xx 才可重试**。其余（400/403/404/410、业务码 15001…）都是
 *    "这个码或这条内容本身有问题"，重试必然同样的结果，给"重试"按钮等于骗用户多按一次。
 * 2. **认不出的异常当可重试**：宁可让用户重试，也不要给一句"未知错误"且无路可走。
 *
 * `NetworkException.code` 同时承载两种来源：HTTP 状态码（`httpFailure`）与业务码
 * （2xx 响应体里的 `code != 0`，如 15001）——所以判据既有等值比较也有区间比较。
 */
internal fun Throwable.toScanResolveFailure(): ScanResolveFailure {
	val message = message?.takeIf { it.isNotBlank() } ?: "校验失败，请重试"
	// 判据在 core-network（`isRetryableNetworkFailure`）：它现在有两个消费方
	// （这里与分片上传的分片重试），放在一处才不会各自演化
	return if (isRetryableNetworkFailure()) ScanResolveFailure.Network(message)
	else ScanResolveFailure.Rejected(message)
}
