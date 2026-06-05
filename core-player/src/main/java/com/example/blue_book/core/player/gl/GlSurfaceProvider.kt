package com.example.blue_book.core.player.gl

import android.view.Surface
import com.example.blue_book.core.player.VideoSurfaceProvider

class GlSurfaceProvider(private val view: GlVideoSurfaceView) : VideoSurfaceProvider {
    private var surface: Surface? = null

    override fun getSurface(onReady: (Surface) -> Unit) {
        if (surface != null) {
            onReady(surface!!)
        } else {
            view.provideSurface {
                surface = it
                onReady(it)
            }
        }
    }

    override fun release() {
        surface?.release()
        surface = null
        view.releaseSurface()
    }
}


