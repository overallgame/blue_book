package com.example.blue_book.core.player

import java.util.ArrayDeque

class PlayerEnginePool(
    private val maxSize: Int,
    private val factory: () -> PlayerEngine
) {
    private val available = ArrayDeque<PlayerEngine>()
    private val active = mutableMapOf<String, PlayerEngine>()

    fun acquire(key: String): PlayerEngine {
        active[key]?.let { return it }
        val engine = if (available.isNotEmpty()) available.removeFirst() else factory()
        active[key] = engine
        return engine
    }

    fun preload(key: String, url: String) {
        val engine = acquire(key)
        engine.setPlayWhenReady(false)
        engine.prepare(url)
        engine.pause()
    }

    fun release(key: String) {
        val engine = active.remove(key) ?: return
        if (available.size < maxSize) {
            engine.pause()
            available.addLast(engine)
        } else {
            engine.release()
        }
    }

    fun releaseAll() {
        active.values.forEach { it.release() }
        available.forEach { it.release() }
        active.clear()
        available.clear()
    }
}


