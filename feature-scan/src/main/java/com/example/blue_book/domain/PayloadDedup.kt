package com.example.blue_book.domain

/**
 * 同一个码在短时间内只处理一次。
 *
 * ## 为什么需要
 * 相机分析器每秒回调几十次，同一个二维码在视野里停留时会反复解出**同一个字符串**。
 * 不去重的话一次扫码会触发几十次后续处理（校验请求、跳转、提示刷屏）。
 *
 * ## 语义：滑动窗口，连续出现视为同一次事件
 * 同一个 payload 只要**每次出现都在窗口内**，就一直是同一次事件、只放行一次；
 * 一旦静默超过窗口，下次再出现才重新放行。
 *
 * 两条被测试逼出来的细节，都不是想当然的写法：
 *
 * 1. **必须按 payload 各自记时间，而不是只记"最后一次处理的"**。
 *    只记最后一次的话，`A → B → A`（都在窗口内）里第三个 A 不会被抑制——
 *    而相机里两个码来回闪现正是常见情形，会交替触发、把后续处理打爆。
 * 2. **被抑制时也要刷新计时**。否则"一个码稳稳停在视野里"会在正好 2 秒时被重新处理一次。
 *    刷新之后，持续可见就始终是同一次事件；用户必须把码移开一会儿才会再次触发。
 *
 * 为什么用"时间由参数传入"的纯类：时间相关逻辑若在内部直接读 `System.currentTimeMillis()`，
 * 就没法稳定测试（要么真等 2 秒，要么为了测它引入时钟注入）。
 */
class PayloadDedup(private val windowMs: Long = DEFAULT_WINDOW_MS) {

	/** payload -> 最近一次出现的时间 */
	private val recentAt = mutableMapOf<String, Long>()

	/**
	 * 该不该处理这个 payload。
	 *
	 * @param nowMs 当前时间（由调用方传入，便于测试）
	 * @return true 表示放行；false 表示窗口内已经出现过，应当丢弃
	 */
	fun shouldHandle(payload: String, nowMs: Long): Boolean {
		pruneExpired(nowMs)

		val lastSeen = recentAt[payload]
		if (lastSeen != null) {
			// 抑制也要刷新计时：否则持续可见的码会在窗口边界被重复处理
			recentAt[payload] = nowMs
			return false
		}

		recentAt[payload] = nowMs
		return true
	}

	/** 清掉已过期的记录，避免长时间扫不同码时 map 无限增长 */
	private fun pruneExpired(nowMs: Long) {
		recentAt.entries.removeAll { nowMs - it.value >= windowMs }
	}

	companion object {
		/** 2 秒：足够覆盖"同一个码在视野里停一会儿"，又不会让用户觉得"扫第二次没反应" */
		const val DEFAULT_WINDOW_MS = 2_000L
	}
}
