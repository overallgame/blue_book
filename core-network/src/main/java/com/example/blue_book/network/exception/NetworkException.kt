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
    cause: Throwable? = null
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
                is SocketTimeoutException,
                is ConnectException -> NetworkException(
                    CODE_TIMEOUT, "请求超时，请检查网络后重试", throwable
                )
                is UnknownHostException -> NetworkException(
                    CODE_NET_ERROR, "网络连接失败，请检查网络设置", throwable
                )
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
