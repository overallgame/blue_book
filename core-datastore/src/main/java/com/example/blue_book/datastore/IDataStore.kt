package com.example.blue_book.datastore

/**
 * 通用 key-value 存储接口，封装 DataStore 的读写能力。
 */
interface IDataStore {

    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?

    suspend fun putInt(key: String, value: Int)
    suspend fun getInt(key: String, defaultValue: Int = 0): Int

    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    suspend fun putLong(key: String, value: Long)
    suspend fun getLong(key: String, defaultValue: Long = 0): Long

    suspend fun remove(key: String)
    suspend fun clear()
}
