package com.example.blue_book

import android.app.Application
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.provider.IUserDataProvider
import com.example.blue_book.provider.IVideoProvider
import com.therouter.TheRouter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BlueBookApplication : Application() {

    // 强制 Hilt 在 Application.onCreate 期间创建 Provider 单例，
    // 设置它们的静态 instance 字段，确保 TheRouter.get() 可用。
    @Inject lateinit var authProvider: IAuthProvider
    @Inject lateinit var videoProvider: IVideoProvider
    @Inject lateinit var userDataProvider: IUserDataProvider

    override fun onCreate() {
        super.onCreate()
        TheRouter.init(this)
    }
}
