package com.example.blue_book.core.player

/**
* 播放器对象池。非线程安全，必须在同一线程访问（当前为 UI 线程）。
*
* `maxSize` 约束 **总** 实例数（active + available）。超上限时只回收空闲实例，
* 不会释放仍被 ViewHolder 持有的活跃实例（详见 [acquire]）。
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
		// 超上限时只回收空闲实例，绝不释放仍被 ViewHolder 持有的活跃实例：
		// 释放活跃实例会让持有者的播放器变成静默空操作（isReleased 后所有调用都是 no-op），
		// 表现为该页黑屏且永远无法恢复，且 key 被移出池后再次 acquire 会重复建实例。
		// 若当前全是活跃实例则允许暂时超额——数量受可见/预加载的 holder 数自然约束，
		// 页面销毁时由 releaseAll() 统一释放。
		while (active.size + available.size > maxSize && available.isNotEmpty()) {
			available.removeFirst().release()
		}
		return engine
	}

	/**
	 * 预加载下一个视频。
	 * key 仍处于 active（对应 ViewHolder 还在用这个引擎，例如刚滑过去的相邻页）时
	 * 直接返回：否则 prepare() 会把在用引擎的位置清零、缓冲丢弃，
	 * 造成"滑回去从头播"以及预加载完全失效。
	 */
	fun preload(key: String, url: String) {
		if (active.containsKey(key)) return
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
