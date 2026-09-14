package com.example.blue_book.core.player

/**
* 播放器对象池。非线程安全，必须在同一线程访问（当前为 UI 线程）。
*
* 内部按 **所有权** 分三类，回收优先级不同：
* - `owned`：已由某个 ViewHolder 持有，**永不回收**。回收它会（1）让持有者的播放器变成
*   静默空操作——`isReleased` 后所有调用都是 no-op，表现为该页黑屏且无法恢复；
*   （2）key 从表中消失，之后同 URL 再 acquire 会重复建实例并解绑他人 surface。
* - `preloaded`：预加载但无人持有，超上限时按插入顺序回收（最旧优先）。
* - `idle`：已归还，优先复用，超上限时最先回收。
*
* `maxSize` 约束三者总数。若全部实例都被持有，允许暂时超额——数量受可见/预加载的
* ViewHolder 数约束，页面销毁时由 [releaseAll] 统一释放。
*/
class PlayerEnginePool(
	private val maxSize: Int,
	private val factory: () -> PlayerEngine
) {
	private val owned = LinkedHashMap<String, PlayerEngine>()
	private val preloaded = LinkedHashMap<String, PlayerEngine>()
	private val idle = ArrayDeque<PlayerEngine>()

	fun acquire(key: String): PlayerEngine {
		owned[key]?.let { return it }
		// 预加载过的直接提升为持有，不丢弃已缓冲好的实例
		preloaded.remove(key)?.let {
			owned[key] = it
			trim()
			return it
		}
		val engine = if (idle.isNotEmpty()) idle.removeFirst() else factory()
		owned[key] = engine
		trim()
		return engine
	}

	/**
	 * 预加载下一个视频。
	 * key 已被持有或已预加载时直接返回：否则 prepare() 会把在用引擎的位置清零、缓冲丢弃，
	 * 造成"滑回去从头播"以及预加载完全失效。
	 */
	fun preload(key: String, url: String) {
		if (owned.containsKey(key) || preloaded.containsKey(key)) return
		val engine = if (idle.isNotEmpty()) idle.removeFirst() else factory()
		preloaded[key] = engine
		engine.setPlayWhenReady(false)
		engine.prepare(url)
		engine.pause()
		trim()
	}

	/** 归还：key 不在 owned 中时是空操作（持有者可安全地重复调用） */
	fun release(key: String) {
		val engine = owned.remove(key) ?: return
		engine.pause()
		if (idle.size < maxSize) idle.addLast(engine) else engine.release()
	}

	fun releaseAll() {
		owned.values.forEach { it.release() }
		preloaded.values.forEach { it.release() }
		idle.forEach { it.release() }
		owned.clear()
		preloaded.clear()
		idle.clear()
	}

	/** 超上限时回收：先空闲，再预加载；全是被持有的实例则暂时超额（见类注释） */
	private fun trim() {
		while (owned.size + preloaded.size + idle.size > maxSize) {
			when {
				idle.isNotEmpty() -> idle.removeFirst().release()
				preloaded.isNotEmpty() -> preloaded.remove(preloaded.keys.first())?.release()
				else -> return
			}
		}
	}
}
