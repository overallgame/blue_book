package com.example.blue_book.ui.publish

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 把 `Dispatchers.Main` 换成测试调度器（与 `feature-scan` / `feature-mine` 的同一份写法）。
 *
 * 没有它跑不起来：`UdfViewModel` 的订阅与 `dispatch` 都跑在 `viewModelScope` 上，
 * 而 `viewModelScope` 用的是 `Dispatchers.Main.immediate` —— 纯 JVM 测试里没有 Main，
 * 一构造就抛 "Module with the Main dispatcher had failed to initialize"。
 *
 * 用 [UnconfinedTestDispatcher] 而不是 StandardTestDispatcher：`launch` 立即执行、
 * 协程内不切调度器，于是 `dispatch()` 返回时状态已经落地，断言不必配 `advanceUntilIdle()`。
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
