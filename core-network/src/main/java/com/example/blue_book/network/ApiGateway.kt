package com.example.blue_book.network

import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.core_network.BuildConfig
import com.example.blue_book.network.dto.CommonResult
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一网络门面 — core-network 对外的唯一消费入口。
 */
@Singleton
class ApiGateway @Inject constructor(
    tokenInterceptor: TokenInterceptor,
    tokenAuthenticator: TokenAuthenticator
) {

    companion object {
        /** Base URL，通过 BuildConfig 注入 */
        val BASE_URL: String get() = BuildConfig.BASE_URL
    }

    private val gson = Gson()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(CommonParamsInterceptor(appVersion = "1.0"))
            .addInterceptor(tokenInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /** 创建 Retrofit API 实例 */
    fun <T> createApi(service: Class<T>): T = retrofit.create(service)

    // ---- 回调风格 ----

    suspend fun <T> request(
        call: suspend () -> Response<ApiResponse<T>>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (String) -> Unit = {}
    ) = execute({ apiCall { call() } }, onSuccess, onFailure)

    suspend fun <T> commonRequest(
        call: suspend () -> Response<CommonResult<T>>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (String) -> Unit = {}
    ) = execute({ commonCall { call() } }, onSuccess, onFailure)

    // ---- Result 风格 ----

    suspend fun <T> apiResult(
        call: suspend () -> Response<ApiResponse<T>>
    ): Result<T> = apiCall { call() }

    suspend fun <T> commonResult(
        call: suspend () -> Response<CommonResult<T>>
    ): Result<T> = commonCall { call() }

    suspend fun apiUnitResult(
        call: suspend () -> Response<ApiResponse<Any>>
    ): Result<Unit> = apiUnitCall { call() }

    // ---- 内部 ----

    private suspend fun <T> execute(
        block: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (String) -> Unit
    ) {
        val result = try {
            block()
        } catch (e: CancellationException) {
            return
        } catch (e: Throwable) {
            Result.failure(e)
        }
        result.fold(
            onSuccess = { value -> withContext(Dispatchers.Main) { onSuccess(value) } },
            onFailure = { e ->
                val msg = NetworkException.from(e).message
                withContext(Dispatchers.Main) { onFailure(msg) }
            }
        )
    }
}
