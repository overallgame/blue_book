package com.example.blue_book.network

import com.example.blue_book.core_network.BuildConfig
import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.network.dto.CommonResult
import com.example.blue_book.network.exception.NetworkException
import com.example.blue_book.network.interceptor.CommonParamsInterceptor
import com.example.blue_book.network.interceptor.TokenAuthenticator
import com.example.blue_book.network.interceptor.TokenInterceptor
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

@Singleton
class ApiGateway @Inject constructor(
    tokenHolder: TokenHolder
) {
    companion object {
        val BASE_URL: String get() = BuildConfig.BASE_URL
    }

    private val gson = Gson()

    private val tokenInterceptor = TokenInterceptor(tokenHolder)
    private val tokenAuthenticator = TokenAuthenticator(tokenHolder)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(CommonParamsInterceptor(appVersion = "1.0"))
            .addInterceptor(tokenInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun <T> createApi(service: Class<T>): T = retrofit.create(service)

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

    suspend fun <T> apiResult(call: suspend () -> Response<ApiResponse<T>>): Result<T> = apiCall { call() }
    suspend fun <T> commonResult(call: suspend () -> Response<CommonResult<T>>): Result<T> = commonCall { call() }
    suspend fun apiUnitResult(call: suspend () -> Response<ApiResponse<Any>>): Result<Unit> = apiUnitCall { call() }

    private suspend fun <T> execute(
        block: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (String) -> Unit
    ) {
        val result = try { block() }
        catch (e: CancellationException) { return }
        catch (e: Throwable) { Result.failure(e) }
        result.fold(
            onSuccess = { withContext(Dispatchers.Main) { onSuccess(it) } },
            onFailure = { withContext(Dispatchers.Main) { onFailure(NetworkException.from(it).message) } }
        )
    }
}
