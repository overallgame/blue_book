package com.example.blue_book.network

import android.content.Context
import com.example.blue_book.core_network.BuildConfig
import com.example.blue_book.datastore.IDataStore
import com.example.blue_book.network.data.ApiResponse
import com.example.blue_book.network.dto.CommonResult
import com.example.blue_book.network.exception.NetworkException
import com.example.blue_book.network.interceptor.LogSanitizer
import com.example.blue_book.network.interceptor.RetryInterceptor
import com.example.blue_book.network.interceptor.TokenAuthenticator
import com.example.blue_book.network.interceptor.TokenInterceptor
import com.example.blue_book.network.interceptor.CommonParamsInterceptor
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: IDataStore,
    tokenHolder: TokenHolder
) {
    companion object {
        const val BASE_URL: String = BuildConfig.BASE_URL
        private const val CACHE_SIZE = 10L * 1024 * 1024 // 10MB
        private const val CACHE_DIR = "http_cache"
    }

    /**
     * 运行时动态 Base URL，优先读 DataStore 中的覆盖值，否则用 BuildConfig 默认值。
     * 设置 `dataStore.putString("base_url_override", "https://api.example.com/")` 即可切换。
     */
    var baseUrl: String = BASE_URL
        private set

    private val gson = Gson()

    private val tokenInterceptor = TokenInterceptor(tokenHolder)
    private val tokenAuthenticator = TokenAuthenticator(tokenHolder)

    private var okHttpClient: OkHttpClient? = null

    private fun refreshOkHttpClient() {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(Cache(cacheDir, CACHE_SIZE))                                     // #1 HTTP 缓存
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))        // #8 连接池
            .addInterceptor(CommonParamsInterceptor(appVersion = "1.0"))
            .addInterceptor(tokenInterceptor)
            .addInterceptor(RetryInterceptor())                                      // #2 自动重试
            .authenticator(tokenAuthenticator)
            .addInterceptor(LogSanitizer())                                          // #5 日志脱敏
            .build()
    }

    init { refreshOkHttpClient() }

    /**
     * 运行时切换 Base URL（例如 Debug 面板切换环境）。
     * 切换后立即重建 OkHttpClient/Retrofit，持久化覆盖值到 DataStore。
     */
    suspend fun overrideBaseUrl(url: String) {
        baseUrl = url
        dataStore.putString("base_url_override", url)
        refreshOkHttpClient()
    }

    private val retrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

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
