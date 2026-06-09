package com.example.blue_book.datastore

import com.example.blue_book.preference.AuthPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore 版本的 AuthPreferences 实现。
 * - 读：直接从 TokenCache 的 @Volatile 字段读取（同步，不阻塞）
 * - 写：同步更新 TokenCache + 异步持久化到 DataStore
 */
@Singleton
class AuthDataStoreImpl @Inject constructor(
    private val sessionDataStore: SessionDataStore,
    private val tokenCache: TokenCache
) : AuthPreferences {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun setPhone(phone: String) {
        tokenCache.phone = phone
        scope.launch { sessionDataStore.savePhone(phone) }
    }

    override fun getPhone(): String? = tokenCache.phone

    override fun setAuthToken(token: String?) {
        tokenCache.authToken = token
        scope.launch {
            sessionDataStore.saveTokens(
                tokenCache.phone ?: "",
                token ?: "",
                tokenCache.refreshToken ?: ""
            )
        }
    }

    override fun getAuthToken(): String? = tokenCache.authToken

    override fun setRefreshToken(token: String?) {
        tokenCache.refreshToken = token
        scope.launch {
            sessionDataStore.saveTokens(
                tokenCache.phone ?: "",
                tokenCache.authToken ?: "",
                token ?: ""
            )
        }
    }

    override fun getRefreshToken(): String? = tokenCache.refreshToken

    override fun clear() {
        tokenCache.clear()
        scope.launch { sessionDataStore.clear() }
    }
}
