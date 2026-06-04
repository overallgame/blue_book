package com.example.blue_book.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_PHONE = stringPreferencesKey("phone")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    val phone: Flow<String?> = context.dataStore.data.map { it[KEY_PHONE] }
    val authToken: Flow<String?> = context.dataStore.data.map { it[KEY_AUTH_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }

    suspend fun saveTokens(phone: String, authToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE] = phone
            prefs[KEY_AUTH_TOKEN] = authToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun savePhone(phone: String) {
        context.dataStore.edit { it[KEY_PHONE] = phone }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
