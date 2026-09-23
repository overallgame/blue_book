package com.example.blue_book.data

import com.example.blue_book.datastore.IDataStore
import javax.inject.Inject
import javax.inject.Singleton

/** 搜索历史：本地 datastore 保存，最多 [MAX_ITEMS] 条，最新在前、去重 */
@Singleton
class SearchHistoryStore @Inject constructor(
	private val dataStore: IDataStore
) {

	suspend fun get(): List<String> {
		val raw = dataStore.getString(KEY) ?: return emptyList()
		return raw.split('\n').filter { it.isNotBlank() }
	}

	/** 新增一条（去重置顶），返回更新后的列表 */
	suspend fun add(term: String): List<String> {
		val trimmed = term.trim()
		if (trimmed.isEmpty()) return get()
		val updated = (listOf(trimmed) + get().filter { it != trimmed }).take(MAX_ITEMS)
		dataStore.putString(KEY, updated.joinToString("\n"))
		return updated
	}

	suspend fun clear() {
		dataStore.remove(KEY)
	}

	companion object {
		private const val KEY = "search_history"
		private const val MAX_ITEMS = 10
	}
}
