package com.example.blue_book.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 缓存桥接层。
 * - 读：通过 @Volatile 字段，OkHttp Interceptor 可同步读取，不阻塞 I/O 线程
 * - 写：同步更新 @Volatile 字段 + 异步持久化到 DataStore（写穿缓存模式）
 */
@Singleton
class TokenCache @Inject constructor(
    private val sessionDataStore: SessionDataStore
) {
    @Volatile var authToken: String? = null
    @Volatile var refreshToken: String? = null
    @Volatile var phone: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            authToken = sessionDataStore.authToken.first()
            refreshToken = sessionDataStore.refreshToken.first()
            phone = sessionDataStore.phone.first()
        }
    }

    fun update(auth: String?, refresh: String?) {
        authToken = auth
        refreshToken = refresh
    }

    fun clear() {
        authToken = null
        refreshToken = null
        phone = null
    }
}
