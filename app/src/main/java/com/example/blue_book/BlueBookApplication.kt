package com.example.blue_book

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.blue_book.datastore.ThemeMode
import com.example.blue_book.datastore.ThemeRepository
import com.therouter.TheRouter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class BlueBookApplication : Application() {

	@Inject
	lateinit var themeRepository: ThemeRepository

	override fun onCreate() {
		super.onCreate()
		AppContext.init(this)
		TheRouter.init(this)
		applyStoredTheme()
	}

	/** 启动时应用已保存的主题偏好（DataStore 轻量 key，同步读一次，首帧前生效） */
	private fun applyStoredTheme() {
		val mode = runBlocking { themeRepository.getThemeMode() }
		AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
	}
}

/** ThemeMode → AppCompatDelegate 夜间模式常量 */
fun ThemeMode.toNightMode(): Int = when (this) {
	ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
	ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
	ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}
