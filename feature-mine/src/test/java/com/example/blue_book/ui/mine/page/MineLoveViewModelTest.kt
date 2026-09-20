package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 本项目的第一个真单元测试（此前三个"测试"文件都是 Android Studio 脚手架）。
 *
 * 选 `MineLoveViewModel` 的原因：它的构造签名只有 `IVideoProvider`（接口）+ `VideoInteractionBus`
 * （可直接构造），**零 Android 依赖**，因此能在纯 JVM 上跑，不需要设备也不需要后端。
 *
 * 它同时验证了本轮抽出的 `VideoCardListViewModel` 基类：最后三个用例测的是基类里那套
 * 「广播落库 + 状态未变不刷新」的逻辑，而不是页面自己的代码。
 *
 * 命名约定：**方法名用英文**（与生产代码的标识符风格一致），**断言消息用中文**。
 * 中文放在消息里而不是方法名上，是因为 JUnit 4 没有 `@DisplayName`，而失败时真正被人读到的是
 * 断言消息；方法名会出现在"类名 > 方法名 FAILED"这一行汇总里，保持 ASCII 更利于检索与外部 CI。
 * 实测失败输出形如：
 *     MineLoveViewModelTest > initFailureResetsLoadingAndKeepsMessage FAILED
 *       java.lang.AssertionError: 失败后 isLoading 必须复位，否则页面永远转圈
 *
 * 覆盖的是**边界与失败路径**，不是 happy path 的复述：
 * 空/满页对 hasMore 的影响、失败后不能卡在 loading、hasMore 为 false 时不再请求、
 * 乐观更新失败必须回滚、广播与当前状态一致时不能白发 effect。
 */
class MineLoveViewModelTest {

	@get:Rule
	val mainDispatcherRule = MainDispatcherRule()

	/** 每个用例一个干净的总线：这是当初把 VideoInteractionBus 从全局 object 改成可注入实例的回报 */
	private val bus = VideoInteractionBus()

	// ───────────────────────── 加载 ─────────────────────────

	@Test
	fun initWithFullPageStoresItemsAndAdvancesCursor() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success((1L..20L).map { card(it) })
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		val state = vm.uiState.value
		assertEquals("满页 20 条应全部落库", 20, state.items.size)
		assertFalse("加载完成必须清掉转圈", state.isLoading)
		assertTrue("整页返回说明后面还有", state.hasMore)
		assertEquals("游标应指向最后一条的 aid", 20L, state.cursorId)
		assertEquals("首次加载不带游标，size 用 pageSize", listOf(null to 20), provider.likedVideoCalls)
	}

	@Test
	fun initWithPartialPageSetsHasMoreFalse() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1), card(2)))
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		assertEquals("返回几条就落几条", 2, vm.uiState.value.items.size)
		assertFalse("不足一页说明已经到底", vm.uiState.value.hasMore)
	}

	@Test
	fun initFailureResetsLoadingAndKeepsMessage() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.failure(RuntimeException("网络不可用"))
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		val state = vm.uiState.value
		assertTrue("失败时列表应为空", state.items.isEmpty())
		assertFalse("失败后 isLoading 必须复位，否则页面永远转圈", state.isLoading)
		assertEquals("失败原因要带进 state 供页面展示", "网络不可用", state.message)
	}

	@Test
	fun loadMoreAppendsUsingPreviousCursor() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success((1L..20L).map { card(it) })
			likedVideoPages += Result.success((21L..22L).map { card(it) })
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.LoadMore)

		val state = vm.uiState.value
		assertEquals("分页应是追加，不是覆盖", 22, state.items.size)
		assertEquals(
			"首尾要分别是第 1 条和第 22 条",
			listOf(1L, 22L), listOf(state.items.first().aid, state.items.last().aid)
		)
		assertEquals(
			"第二次请求必须带上次的游标",
			listOf(null to 20, 20L to 20), provider.likedVideoCalls
		)
		assertFalse("第二页不足一页，hasMore 应变 false", state.hasMore)
	}

	@Test
	fun loadMoreDoesNothingWhenHasMoreIsFalse() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1)))
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.LoadMore)

		assertEquals("已经到底了就不该再打接口", 1, provider.likedVideoCalls.size)
	}

	// ───────────────────────── 点赞 ─────────────────────────

	@Test
	fun toggleLikeOptimisticallyFlipsStateAndIncrementsCount() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1, like = 5)))
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.ToggleLike(vm.uiState.value.items.first()))

		val after = vm.uiState.value.items.first()
		assertTrue("乐观更新：不等接口返回就该翻成已点赞", after.isLike)
		assertEquals("计数应 +1", 6, after.like)
		assertEquals("应把新状态提交给服务端", listOf(1L to true), provider.likeCalls)
	}

	@Test
	fun toggleLikeRollsBackAndToastsOnFailure() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1, like = 5)))
			likeResult = Result.failure(RuntimeException("点赞失败"))
		}
		val vm = MineLoveViewModel(provider, bus)
		val effects = collectEffects(vm)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.ToggleLike(vm.uiState.value.items.first()))

		val after = vm.uiState.value.items.first()
		assertFalse("失败必须回滚，否则界面留着一个服务端并不存在的赞", after.isLike)
		assertEquals("回滚时计数也要还原", 5, after.like)
		assertTrue(
			"失败要有可见提示，不能静默失败",
			effects.any { it is MineLoveEffect.ShowToast && it.message == "点赞失败" }
		)
	}

	// ────────────── 播放页广播同步（测的是本轮抽出的基类）──────────────

	@Test
	fun interactionPatchFromPlayerUpdatesCard() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1, like = 5)))
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		bus.publishLike(aid = 1, isLike = true)

		val after = vm.uiState.value.items.first()
		assertTrue("播放页点的赞应同步回列表卡片", after.isLike)
		assertEquals("计数也要跟着 +1", 6, after.like)
	}

	@Test
	fun interactionPatchWithSameStateEmitsNoEffect() = runTest {
		val provider = FakeVideoProvider().apply {
			// 卡片本来就是已点赞 —— 再收到一次"已点赞"的广播属于无事发生
			likedVideoPages += Result.success(listOf(card(1, like = 5, isLike = true)))
		}
		val vm = MineLoveViewModel(provider, bus)
		val effects = collectEffects(vm)
		vm.dispatch(MineLoveIntent.Init)
		effects.clear()

		bus.publishLike(aid = 1, isLike = true)

		assertTrue("状态没变就不该发 UpdateItem（基类的 null 守卫）", effects.isEmpty())
	}

	@Test
	fun interactionPatchForUnknownAidIsIgnored() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1)))
		}
		val vm = MineLoveViewModel(provider, bus)
		val effects = collectEffects(vm)
		vm.dispatch(MineLoveIntent.Init)
		effects.clear()

		bus.publishLike(aid = 999, isLike = true)

		assertEquals("列表里没有这张卡，就直接忽略", 1, vm.uiState.value.items.size)
		assertTrue("更不该为此发 effect", effects.isEmpty())
	}

	// ───────────────────────── 辅助 ─────────────────────────

	/**
	 * 收集一次性副作用。
	 *
	 * 必须先订阅再 dispatch：`uiEffect` 是 replay=0 的 SharedFlow，没有订阅者时
	 * emit 出去的值会被丢掉。用 UnconfinedTestDispatcher 是为了让收集立即开始，
	 * 否则要等 advanceUntilIdle，早于它发出的 effect 会丢。
	 */
	@OptIn(ExperimentalCoroutinesApi::class)
	private fun kotlinx.coroutines.test.TestScope.collectEffects(
		vm: MineLoveViewModel
	): MutableList<MineLoveEffect> {
		val effects = mutableListOf<MineLoveEffect>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
			vm.uiEffect.collect { effects += it }
		}
		return effects
	}

	private fun card(aid: Long, like: Int = 0, isLike: Boolean = false) = VideoCardInfo(
		aid = aid,
		cid = aid * 10,
		like = like,
		image = "https://example.invalid/$aid.jpg",
		avatar = "https://example.invalid/avatar$aid.jpg",
		collection = 0,
		nickname = "user$aid",
		description = "描述 $aid",
		playUrl = "https://example.invalid/$aid.m3u8",
		isLike = isLike,
		isCollect = false
	)
}
