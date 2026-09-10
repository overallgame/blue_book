package com.example.blue_book.datastore

import javax.inject.Inject
import javax.inject.Singleton

/** 主题模式：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode(val value: Int) {
	FOLLOW_SYSTEM(0),
	LIGHT(1),
	DARK(2);

	companion object {
		fun fromValue(value: Int): ThemeMode = entries.firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
	}
}

/** 主题偏好：存 DataStore，全局共享（Application 启动应用 + 设置页读写） */
@Singleton
class ThemeRepository @Inject constructor(
	private val dataStore: AppDataStore
) {

	suspend fun getThemeMode(): ThemeMode =
		ThemeMode.fromValue(dataStore.getInt(KEY_THEME_MODE, ThemeMode.FOLLOW_SYSTEM.value))

	suspend fun setThemeMode(mode: ThemeMode) {
		dataStore.putInt(KEY_THEME_MODE, mode.value)
	}

	companion object {
		private const val KEY_THEME_MODE = "theme_mode"
	}
}
