package com.example.blue_book.network

import com.example.blue_book.datastore.IDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 缓存持有者，提供 @Volatile 字段供 OkHttp 线程同步读写。
 * ApiGateway / AuthRepositoryImpl / UserRepositoryImpl 通过此对象操作 Token。
 */
@Singleton
class TokenHolder @Inject constructor(
    private val dataStore: IDataStore
) {
    @Volatile var authToken: String? = null
    @Volatile var refreshToken: String? = null
    @Volatile var phone: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            authToken = dataStore.getString("auth_token")
            refreshToken = dataStore.getString("refresh_token")
            phone = dataStore.getString("phone")
        }
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
        authToken = null
        refreshToken = null
        phone = null
        scope.launch { dataStore.clear() }
    }
}
