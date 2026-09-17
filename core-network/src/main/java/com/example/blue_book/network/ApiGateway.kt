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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class ApiGateway @Inject constructor(
	@ApplicationContext private val context: Context,
	private val dataStore: IDataStore,
	tokenHolder: TokenHolder,
	currentUser: CurrentUser,
	private val networkMonitor: NetworkMonitor
) {
	companion object {
		const val BASE_URL: String = BuildConfig.BASE_URL
		private const val CACHE_SIZE = 10L * 1024 * 1024 // 10MB
		private const val CACHE_DIR = "http_cache"

		/** 断网等待网络恢复的上限（毫秒）：仅在系统报告无连接时生效 */
		private const val NETWORK_WAIT_MS = 1200L
	}

	/**
	* 运行时动态 Base URL，优先读 DataStore 中的覆盖值，否则用 BuildConfig 默认值。
	* 设置 `dataStore.putString("base_url_override", "https://api.example.com/")` 即可切换。
	*/
	@Volatile
	var baseUrl: String = BASE_URL
		private set

	private val gson = Gson()

	private val tokenInterceptor = TokenInterceptor(tokenHolder)
	// 会话失效时同时清内存登录态（CurrentUser），避免"有登录态但请求全 401"的僵尸状态
	private val tokenAuthenticator = TokenAuthenticator(tokenHolder) { currentUser.clear() }

	private var okHttpClient: OkHttpClient? = null

	/**
	 * 目标是局域网/本机地址时跳过断网快速失败：
	 * 这类网络（内网服务器、无外网的路由器）系统可能不标记 NET_CAPABILITY_INTERNET，
	 * 但请求本身仍然可达，不能凭系统状态判死。
	 */
	@Volatile
	private var targetIsLocal: Boolean = isLocalHost(baseUrl)

	private fun refreshOkHttpClient() {
		targetIsLocal = isLocalHost(baseUrl)
		val cacheDir = File(context.cacheDir, CACHE_DIR)
		val builder = OkHttpClient.Builder()
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

		// 请求/响应全文日志只在 debug 挂载：这个拦截器要把响应体读进内存才能打印，
		// 正式包既不需要这些日志，也不该为每个请求付这份开销。
		// （重试日志不在此列——那是异常路径的 Log.w，见 RetryInterceptor）
		if (BuildConfig.DEBUG) {
			builder.addInterceptor(LogSanitizer())                                   // #5 日志脱敏
		}
		okHttpClient = builder.build()
	}

	/** 后台恢复持久化的 Base URL 覆盖（不做主线程阻塞读） */
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	init {
		refreshOkHttpClient()
		ioScope.launch {
			val persisted = dataStore.getString("base_url_override")
			if (!persisted.isNullOrBlank() && persisted != baseUrl) {
				baseUrl = persisted
				refreshOkHttpClient()
			}
		}
	}

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
	) = execute({ withNetwork { apiCall { call() } } }, onSuccess, onFailure)

	suspend fun <T> commonRequest(
		call: suspend () -> Response<CommonResult<T>>,
		onSuccess: suspend (T) -> Unit,
		onFailure: suspend (String) -> Unit = {}
	) = execute({ withNetwork { commonCall { call() } } }, onSuccess, onFailure)

	suspend fun <T> apiResult(call: suspend () -> Response<ApiResponse<T>>): Result<T> =
		withNetwork { apiCall { call() } }

	suspend fun <T> commonResult(call: suspend () -> Response<CommonResult<T>>): Result<T> =
		withNetwork { commonCall { call() } }

	suspend fun apiUnitResult(call: suspend () -> Response<ApiResponse<Any>>): Result<Unit> =
		withNetwork { apiUnitCall { call() } }

	/**
	 * 断网快速失败：无网络时不再等连接/读超时（各 10s），直接给出明确文案。
	 * 设备刚开机等瞬时状态先短等一次网络广播，避免误判。
	 */
	private suspend fun <T> withNetwork(block: suspend () -> Result<T>): Result<T> {
		if (targetIsLocal || networkMonitor.isConnected) return block()
		val connected = withTimeoutOrNull(NETWORK_WAIT_MS.milliseconds) {
			networkMonitor.networkState.first { it }
		} ?: false
		if (!connected && !networkMonitor.isConnected) {
			return Result.failure(
				NetworkException(NetworkException.CODE_NET_ERROR, "网络未连接，请检查网络设置")
			)
		}
		return block()
	}

	/** 本机 / 私有网段（10/8、172.16-31、192.168、169.254）判定 */
	private fun isLocalHost(url: String): Boolean {
		val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
		if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
		if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return true
		if (host.startsWith("172.")) {
			val second = host.removePrefix("172.").substringBefore('.').toIntOrNull()
			if (second != null && second in 16..31) return true
		}
		return false
	}

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
