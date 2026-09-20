package com.example.blue_book.ui.mine.page

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 把 `Dispatchers.Main` 换成测试调度器。
 *
 * 没有它跑不起来：`UdfViewModel` 的订阅与 `dispatch` 都跑在 `viewModelScope` 上，
 * 而 `viewModelScope` 用的是 `Dispatchers.Main.immediate` —— 纯 JVM 测试里没有 Main，
 * 一构造就抛 "Module with the Main dispatcher had failed to initialize"。
 *
 * 用 [UnconfinedTestDispatcher] 而不是 StandardTestDispatcher：`launch` 立即执行、
 * 协程内不切调度器，于是 `dispatch()` 返回时状态已经落地，断言不必配 `advanceUntilIdle()`，
 * 失败时的堆栈也直接指向断言行。本 ViewModel 内部没有 `delay`，不需要虚拟时间
 * （换成需要虚拟时间的用例，把 dispatcher 传成 `StandardTestDispatcher(testScheduler)` 即可）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
	private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

	override fun starting(description: Description) {
		Dispatchers.setMain(dispatcher)
	}

	override fun finished(description: Description) {
		Dispatchers.resetMain()
	}
}
