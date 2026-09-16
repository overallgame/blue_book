package com.example.blue_book.host

import androidx.fragment.app.Fragment

/**
 * Tab 宿主的**窗口能力**接口 —— 由承载页面的 Activity 实现（当前是 :app 的 MainActivity
 * 与 feature-video 的 VideoActivity，它们都直接承载 [VideoFragment] 所在的页面）。
 *
 * 这里只保留「只有宿主才做得到的事」：请求转屏、收起系统栏与底部导航。
 *
 * 定位的变化：原先这里还有一批 `navigateToXxx`（搜索、资料编辑、播放页、登录入口），
 * 因为那时二级页是叠在 Tab 之上的 Fragment，只有宿主能操作那个 FragmentManager。
 * 这些页面改为独立 Activity 后，跳转就是一条路由（见 `RoutePath` / `openVideoPlayer`），
 * 任何模块都能直接调用，不必再经由宿主——顺带消掉了 `mainHost?.navigateToXxx()`
 * 的静默失效风险：宿主没实现时 `?.` 会让跳转无声无息，最难查。
 *
 * 接口位于 lib-base（而不是 :app）是因为 feature 模块不能反向依赖 :app。
 * 接口签名不含任何 Fragment 类型，因此 lib-base 不需要 Fragment 之外的宿主依赖。
 *
 * **所有方法默认抛 [UnsupportedOperationException]**：每个宿主只声明自己真正具备的能力。
 * 未实现的能力一旦被误调用会**响亮地失败**，而不是静默无效果——静默无效比崩溃更难查。
 *
 * 注意：本接口**不通过 TheRouter `@ServiceProvider` 注册**。它是页面级的宿主回调，
 * 实现者持有 Activity 生命周期，不适合放进单例服务容器；取用方式见 [mainHost]。
 */
interface IMainHost {

	/**
	 * 宿主是否在内容区下方常驻了底部导航栏。
	 *
	 * - true（MainActivity）：页面**不要**自己避让底部系统栏——那块空间已被导航栏占住，
	 *   导航栏自身会按 `bars.bottom` 抬高，页面再加一次内边距会把内容白白抬高一条。
	 * - false（VideoActivity 等独立页面）：页面要自己避让，否则底部互动栏会被
	 *   系统手势条（或三键导航）压住，点不到。
	 */
	val providesBottomNav: Boolean get() = false

	/** 进入全屏（横屏播放 + 隐藏系统栏；Tab 宿主还要收起底部导航） */
	fun enterFullscreen(): Unit = unsupported("enterFullscreen")

	/** 退出全屏（恢复竖屏 + 显示系统栏） */
	fun exitFullscreen(): Unit = unsupported("exitFullscreen")

	private fun unsupported(name: String): Nothing =
		throw UnsupportedOperationException("当前宿主未实现 IMainHost.$name")
}

/**
 * 取当前 Fragment 的 Tab 宿主；宿主未实现 [IMainHost] 时返回 null。
 *
 * 用可空取用而不是 `as` 强转：这类页面万一被别的宿主承载（测试、预览、
 * 未来的独立入口），强转会直接抛 ClassCastException，而可空取用只是不响应。
 */
val Fragment.mainHost: IMainHost?
	get() = activity as? IMainHost
