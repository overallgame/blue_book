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
 * 它同时验证了本轮抽出的 `VideoCardListViewModel` 基类：最后两个用例测的是基类里那套
 * 「广播落库 + 状态未变不刷新」的逻辑，而不是页面自己的代码。
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
	fun `Init 返回满页：落库、游标推进到最后一条、hasMore 为 true`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success((1L..20L).map { card(it) })
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		val state = vm.uiState.value
		assertEquals(20, state.items.size)
		assertFalse(state.isLoading)
		assertTrue(state.hasMore)
		assertEquals(20L, state.cursorId)
		assertEquals(listOf(null to 20), provider.likedVideoCalls)
	}

	@Test
	fun `Init 返回不足一页：hasMore 置 false`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1), card(2)))
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		assertEquals(2, vm.uiState.value.items.size)
		assertFalse(vm.uiState.value.hasMore)
	}

	@Test
	fun `Init 失败：不能卡在 loading，且把失败原因带进 state`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.failure(RuntimeException("网络不可用"))
		}
		val vm = MineLoveViewModel(provider, bus)

		vm.dispatch(MineLoveIntent.Init)

		val state = vm.uiState.value
		assertTrue(state.items.isEmpty())
		assertFalse("失败后 isLoading 必须复位，否则页面永远转圈", state.isLoading)
		assertEquals("网络不可用", state.message)
	}

	@Test
	fun `LoadMore：用上次的游标追加而不是覆盖`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success((1L..20L).map { card(it) })
			likedVideoPages += Result.success((21L..22L).map { card(it) })
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.LoadMore)

		val state = vm.uiState.value
		assertEquals(22, state.items.size)
		assertEquals(listOf(1L, 22L), listOf(state.items.first().aid, state.items.last().aid))
		assertEquals(listOf(null to 20, 20L to 20), provider.likedVideoCalls)
		assertFalse("第二页不足一页，hasMore 应变 false", state.hasMore)
	}

	@Test
	fun `hasMore 为 false 时 LoadMore 不再发请求`() = runTest {
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
	fun `ToggleLike 乐观更新：立刻翻状态并 +1`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1, like = 5)))
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		vm.dispatch(MineLoveIntent.ToggleLike(vm.uiState.value.items.first()))

		val after = vm.uiState.value.items.first()
		assertTrue(after.isLike)
		assertEquals(6, after.like)
		assertEquals(listOf(1L to true), provider.likeCalls)
	}

	@Test
	fun `ToggleLike 失败：回滚到原值并弹提示`() = runTest {
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
		assertEquals(5, after.like)
		assertTrue(effects.any { it is MineLoveEffect.ShowToast && it.message == "点赞失败" })
	}

	// ────────────── 播放页广播同步（测的是本轮抽出的基类）──────────────

	@Test
	fun `播放页点赞的广播：落到本列表的卡片上`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1, like = 5)))
		}
		val vm = MineLoveViewModel(provider, bus)
		vm.dispatch(MineLoveIntent.Init)

		bus.publishLike(aid = 1, isLike = true)

		val after = vm.uiState.value.items.first()
		assertTrue(after.isLike)
		assertEquals(6, after.like)
	}

	@Test
	fun `广播状态与当前一致：不刷新也不发 effect`() = runTest {
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
	fun `广播的 aid 不在列表里：忽略而不报错`() = runTest {
		val provider = FakeVideoProvider().apply {
			likedVideoPages += Result.success(listOf(card(1)))
		}
		val vm = MineLoveViewModel(provider, bus)
		val effects = collectEffects(vm)
		vm.dispatch(MineLoveIntent.Init)
		effects.clear()

		bus.publishLike(aid = 999, isLike = true)

		assertEquals(1, vm.uiState.value.items.size)
		assertTrue(effects.isEmpty())
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
