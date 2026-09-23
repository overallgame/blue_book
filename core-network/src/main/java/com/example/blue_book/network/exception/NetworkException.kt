package com.example.blue_book.network.exception

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
* 网络异常分类体系，将各种 Throwable 映射为用户友好的中文消息。
*/
class NetworkException(
	val code: Int,
	override val message: String,
	cause: Throwable? = null,
	/**
	 * 服务端响应体里的**业务码**（`ApiResponse.code`），只要拿得到就填。
	 *
	 * 与 [code] 的区别：[code] 在非 2xx 时是 HTTP 状态码（400/404/5xx），
	 * 而业务码藏在响应体里（例如 400 + `{"code":13006}`）。绝大多数场景只看 HTTP 状态就够了，
	 * 但"同一个状态码下要区分不同业务原因"时（如 13006 分片校验不符要重传该片、
	 * 而 13005 参数错重试无用，两者都是 400）就必须看它。
	 */
	val businessCode: Int? = null
) : RuntimeException(message, cause) {

	companion object {
		/** 网络连接失败 */
		const val CODE_NET_ERROR = 4000

		/** 请求超时 */
		const val CODE_TIMEOUT = 4080

		/** 数据解析错误 */
		const val CODE_PARSE_ERROR = 4010

		/** 服务器错误 */
		const val CODE_SERVER_ERROR = 5000

		/** 登录已过期 */
		const val CODE_AUTH_INVALID = 401

		/** 未知错误 */
		const val CODE_UNKNOWN = -1

		fun from(throwable: Throwable): NetworkException {
			return when (throwable) {
				is NetworkException -> throwable
				is HttpException -> {
					val code = throwable.code()
					when {
						code == 401 || code == 403 -> NetworkException(
							CODE_AUTH_INVALID, "登录已过期，请重新登录", throwable
						)
						code in 500..599 -> NetworkException(
							CODE_SERVER_ERROR, "服务器繁忙，请稍后重试", throwable
						)
						else -> NetworkException(
							CODE_SERVER_ERROR, "服务器错误($code)", throwable
						)
					}
				}
				is SocketTimeoutException -> NetworkException(
					CODE_TIMEOUT, "请求超时，请检查网络后重试", throwable
				)
				// 连接被拒绝/无法建立：这不是超时，文案要区分开，
				// 否则"服务器没启动"会被说成"超时"，误导排查方向
				is ConnectException -> NetworkException(
					CODE_NET_ERROR, "无法连接到服务器，请检查网络或服务是否可用", throwable
				)
				is UnknownHostException -> NetworkException(
					CODE_NET_ERROR, "网络连接失败，请检查网络设置", throwable
				)
				// IOException 兜底：包含平台网络策略拦截（cleartext 被禁时抛
				// UnknownServiceException）等场景，这些原始英文文本不能直接给用户看
				is IOException -> NetworkException(
					CODE_NET_ERROR, "网络连接失败，请检查网络设置", throwable
				)
				is JsonParseException,
				is IllegalStateException -> NetworkException(
					CODE_PARSE_ERROR, "数据解析错误", throwable
				)
				else -> NetworkException(
					CODE_UNKNOWN, throwable.message ?: "未知错误", throwable
				)
			}
		}
	}
}

/**
 * 这次失败**值不值得重试**。
 *
 * 判据只有一条：只有网络类与 5xx 值得重试。其余（400/403/404，以及业务码如 15001「不是小蓝书的码」、
 * 13002「分片缺失」）都是"请求本身有问题"——重试必然还是同样结果，
 * 给用户一个「重试」按钮等于骗他多按一次。
 *
 * 认不出的 Throwable **按可重试处理**：与其丢下一句"未知错误"又没有出路，
 * 不如让他重试一次（重试的成本远低于"卡在这一步没法动"）。
 *
 * 放在这里而不是各模块自己实现，是因为消费方已经有两个了（扫码校验的失败分类、
 * 分片上传的分片重试），而"哪些错误该重试"恰恰是最容易被抄成两份、然后慢慢分叉的判断——
 * 扫码那边为它写了 8 条用例，抄一份就等于那 8 条只保护了一半。
 *
 * 注意 `NetworkException.code` 同时承载两种来源：**HTTP 状态码**（`httpFailure`）
 * 与**业务码**（2xx 响应体里 `code != 0`），所以下面既有等值比较也有区间比较。
 */
fun Throwable.isRetryableNetworkFailure(): Boolean {
	val code = (this as? NetworkException)?.code ?: return true
	return code == NetworkException.CODE_NET_ERROR ||
		code == NetworkException.CODE_TIMEOUT ||
		code == NetworkException.CODE_SERVER_ERROR ||
		code in 500..599
}
