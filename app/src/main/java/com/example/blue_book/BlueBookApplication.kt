package com.example.blue_book

import android.app.Application
import com.therouter.TheRouter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BlueBookApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        TheRouter.init(this)
    }
}
