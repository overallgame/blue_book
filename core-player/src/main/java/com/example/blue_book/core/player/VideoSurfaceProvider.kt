package com.example.blue_book.core.player

import android.view.Surface

interface VideoSurfaceProvider {
    fun getSurface(onReady: (Surface) -> Unit)
    fun release()
}


