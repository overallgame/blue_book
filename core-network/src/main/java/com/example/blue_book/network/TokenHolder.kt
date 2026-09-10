package com.example.blue_book.network

import com.example.blue_book.datastore.IDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
* Token 缓存持有者，提供 @Volatile 字段供 OkHttp 线程同步读写。
* ApiGateway / AuthRepositoryImpl / UserRepositoryImpl 通过此对象操作 Token。
*
* 冷启动的持久化恢复在后台异步进行，读取方必须先 [awaitLoaded]，
* 否则会把已登录用户误判为游客（登录引导、定位申请全部走错分支）。
*/
@Singleton
class TokenHolder @Inject constructor(
	private val dataStore: IDataStore
) {
	@Volatile var authToken: String? = null
	@Volatile var refreshToken: String? = null
	@Volatile var phone: String? = null

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	/** 冷启动恢复完成信号（可重复 await） */
	private val loaded = CompletableDeferred<Unit>()

	/** 已登出：恢复过程中途登出时，不再把磁盘旧值写回内存 */
	@Volatile private var cleared = false

	init {
		scope.launch {
			try {
				val token = dataStore.getString("auth_token")
				val refresh = dataStore.getString("refresh_token")
				val savedPhone = dataStore.getString("phone")
				if (!cleared) {
					// 恢复期间若已有登录写入（内存值非空），保留内存值
					if (authToken == null) authToken = token
					if (refreshToken == null) refreshToken = refresh
					if (phone == null) phone = savedPhone
				}
			} finally {
				loaded.complete(Unit)
			}
		}
	}

	/** 等待冷启动恢复完成（挂起不阻塞线程） */
	suspend fun awaitLoaded() {
		if (!loaded.isCompleted) loaded.await()
	}

	fun saveAuthToken(token: String) {
		authToken = token
		scope.launch { dataStore.putString("auth_token", token) }
	}

	fun saveRefreshToken(token: String) {
		refreshToken = token
		scope.launch { dataStore.putString("refresh_token", token) }
	}

	fun savePhone(value: String) {
		phone = value
		scope.launch { dataStore.putString("phone", value) }
	}

	fun clear() {
		cleared = true
		authToken = null
		refreshToken = null
		phone = null
		scope.launch {
			dataStore.remove("auth_token")
			dataStore.remove("refresh_token")
			dataStore.remove("phone")
		}
	}
}
