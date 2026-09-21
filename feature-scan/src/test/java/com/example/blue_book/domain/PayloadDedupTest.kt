package com.example.blue_book.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PayloadDedup` 的单元测试。纯 JVM——时间由参数传入，所以不用等待、也不依赖真实时钟。
 *
 * 时间窗抑制逻辑的坑全在边界上：窗口的开闭、持续出现怎么办、换内容是否立刻放行、
 * 以及"来回闪现两个码"这种情形。用例就集中在这几处。
 *
 * 注：`returningToPreviousPayloadWithinWindowIsSuppressed` 与
 * `continuouslyVisiblePayloadStaysSuppressed` 两条是本文件**抓到实现缺陷后补的**
 * （原实现只记"最后一次处理的 payload"，且被抑制时不刷新计时，两条都会失败）。
 */
class PayloadDedupTest {

	private val payload = "https://bluebook.invalid/v/1"
	private val otherPayload = "https://bluebook.invalid/v/2"

	@Test
	fun firstPayloadIsAlwaysHandled() {
		val dedup = PayloadDedup(windowMs = 2000)
		assertTrue("第一次出现必须放行", dedup.shouldHandle(payload, nowMs = 1_000))
	}

	@Test
	fun samePayloadWithinWindowIsSuppressed() {
		val dedup = PayloadDedup(windowMs = 2000)
		dedup.shouldHandle(payload, nowMs = 1_000)

		assertFalse(
			"同一个码 1 秒后又解出来（相机每秒回调几十次），必须丢弃",
			dedup.shouldHandle(payload, nowMs = 2_000)
		)
	}

	@Test
	fun samePayloadAfterSilenceIsHandledAgain() {
		val dedup = PayloadDedup(windowMs = 2000)
		dedup.shouldHandle(payload, nowMs = 1_000)

		assertTrue(
			"静默超过窗口后同一个码要能再处理——否则用户「扫完返回再扫一次」会毫无反应",
			dedup.shouldHandle(payload, nowMs = 3_000)
		)
	}

	@Test
	fun windowBoundaryIsExclusive() {
		val dedup = PayloadDedup(windowMs = 2000)
		dedup.shouldHandle(payload, nowMs = 1_000)

		assertTrue(
			"恰好满一个窗口即视为新事件（区间是左闭右开）",
			dedup.shouldHandle(payload, nowMs = 3_000)
		)
	}

	@Test
	fun continuouslyVisiblePayloadStaysSuppressed() {
		// 码稳稳停在视野里：每隔 1 秒被解出一次，跨越了远多于一个窗口的总时长。
		// 被抑制时必须刷新计时，否则正好在第 2 秒那一次会被重新处理。
		val dedup = PayloadDedup(windowMs = 2000)
		assertTrue("首次", dedup.shouldHandle(payload, nowMs = 1_000))
		assertFalse("1.0s 后", dedup.shouldHandle(payload, nowMs = 2_000))
		assertFalse("2.0s 后", dedup.shouldHandle(payload, nowMs = 3_000))
		assertFalse("3.0s 后——窗口早过了，但因为它一直在出现，仍应是同一次事件", dedup.shouldHandle(payload, nowMs = 4_000))
		assertFalse("4.0s 后", dedup.shouldHandle(payload, nowMs = 5_000))
	}

	@Test
	fun differentPayloadIsHandledImmediately() {
		val dedup = PayloadDedup(windowMs = 2000)
		dedup.shouldHandle(payload, nowMs = 1_000)

		assertTrue(
			"换了一个码要立刻放行，不能等窗口——用户在视野里挪动让另一个码进入很常见",
			dedup.shouldHandle(otherPayload, nowMs = 1_001)
		)
	}

	@Test
	fun returningToPreviousPayloadWithinWindowIsSuppressed() {
		// 两个码在视野里来回闪现：必须两个都抑制住，否则会交替触发、把后续处理打爆。
		// 这条是原实现（只记"最后一次处理的"）会失败的用例。
		val dedup = PayloadDedup(windowMs = 2000)
		dedup.shouldHandle(payload, nowMs = 1_000)
		dedup.shouldHandle(otherPayload, nowMs = 1_100)

		assertFalse(
			"回到上一个码、窗口没过：仍要抑制",
			dedup.shouldHandle(payload, nowMs = 1_200)
		)
		assertTrue(
			"而两个码都静默超过窗口之后，各自再出现都应放行",
			dedup.shouldHandle(payload, nowMs = 4_000)
		)
	}
}
