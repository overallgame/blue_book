package com.example.blue_book.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * IDataStore 的 DataStore Preferences 实现。
 * 封装 AndroidX DataStore，提供通用 key-value 读写。
 */
@Singleton
class AppDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) : IDataStore {

    override suspend fun putString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun getString(key: String): String? {
        return context.dataStore.data.map { it[stringPreferencesKey(key)] }.first()
    }

    override suspend fun putInt(key: String, value: Int) {
        context.dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return context.dataStore.data.map { it[intPreferencesKey(key)] ?: defaultValue }.first()
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return context.dataStore.data.map { it[booleanPreferencesKey(key)] ?: defaultValue }.first()
    }

    override suspend fun putLong(key: String, value: Long) {
        context.dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return context.dataStore.data.map { it[longPreferencesKey(key)] ?: defaultValue }.first()
    }

    override suspend fun remove(key: String) {
        context.dataStore.edit {
            it.remove(stringPreferencesKey(key))
            it.remove(intPreferencesKey(key))
            it.remove(booleanPreferencesKey(key))
            it.remove(longPreferencesKey(key))
        }
    }

    override suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
