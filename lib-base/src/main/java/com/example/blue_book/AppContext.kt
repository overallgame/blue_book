package com.example.blue_book

import android.app.Application

/**
 * 全局 Application 引用，供 @ServiceProvider 函数通过 Hilt EntryPoint 获取依赖。
 * 在 BlueBookApplication.onCreate() 中初始化。
 */
object AppContext {
    lateinit var application: Application
        private set

    fun init(app: Application) {
        application = app
    }
}
