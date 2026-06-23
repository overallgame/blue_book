package com.example.blue_book.core.player

/**
 * 播放器对象池。非线程安全，必须在同一线程访问（当前为 UI 线程）。
 *
 * `maxSize` 约束 **总** 实例数（active + available），超过上限时回收最旧的 active 播放器。
 */
class PlayerEnginePool(
    private val maxSize: Int,
    private val factory: () -> PlayerEngine
) {
    private val available = ArrayDeque<PlayerEngine>()
    private val active = LinkedHashMap<String, PlayerEngine>()

    fun acquire(key: String): PlayerEngine {
        active[key]?.let { return it }
        val engine = if (available.isNotEmpty()) available.removeFirst() else factory()
        active[key] = engine
        // 总实例数超过上限，回收最旧的 active
        while (active.size + available.size > maxSize) {
            val oldest = active.entries.firstOrNull() ?: break
            oldest.value.release()
            active.remove(oldest.key)
        }
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
