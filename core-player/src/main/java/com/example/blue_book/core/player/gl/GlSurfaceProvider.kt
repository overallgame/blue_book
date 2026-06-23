package com.example.blue_book.core.player.gl

import android.view.Surface
import com.example.blue_book.core.player.VideoSurfaceProvider

class GlSurfaceProvider(private val view: GlVideoSurfaceView) : VideoSurfaceProvider {
    private var surface: Surface? = null
    private var lastContextGen: Int = -1

    override fun getSurface(onReady: (Surface) -> Unit) {
        val currentGen = view.contextGeneration
        if (surface != null && currentGen == lastContextGen) {
            onReady(surface!!)
        } else {
            // GL context 已重建或首次，重新获取 Surface
            lastContextGen = currentGen
            surface = null
            view.provideSurface {
                surface = it
                onReady(it)
            }
        }
    }

    override fun release() {
        surface = null
        view.releaseSurface()
    }
}
