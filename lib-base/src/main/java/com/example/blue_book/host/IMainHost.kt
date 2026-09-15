package com.example.blue_book.host

import androidx.fragment.app.Fragment
import com.example.blue_book.data.VideoCardInfo

/**
 * Tab 宿主接口 — 由承载底部导航的宿主页面实现（当前为 :app 的 MainActivity）。
 *
 * 存在的理由：feature 模块不能依赖 :app（反向依赖），而各 Tab 页面原本用
 * `(requireActivity() as HomeActivity).navigateToXxx()` 调用宿主——一旦 Tab 改为
 * Fragment 共用一个宿主，那个强制转换就会 ClassCastException。改成本接口后：
 * - feature 只依赖本接口（位于 lib-base），与宿主实现解耦；
 * - 宿主从「每个 Tab 一个 Activity」收敛为「一个 Activity」，本接口的签名不含任何
 *   Fragment 类型，迁移期间旧 Activity 与新宿主可以同时实现它。
 *
 * **所有方法默认抛 [UnsupportedOperationException]**：每个宿主只声明自己真正具备的能力
 * （首页提供搜索、我的页提供资料编辑、视频页提供全屏），不为做不到的事写空实现。
 * 未实现的能力一旦被误调用会**响亮地失败**，而不是静默无效果——静默无效比崩溃更难查。
 *
 * 注意：本接口**不通过 TheRouter `@ServiceProvider` 注册**。它是页面级的宿主回调，
 * 实现者持有 Activity 生命周期，不适合放进单例服务容器；取用方式见 [mainHost]。
 */
interface IMainHost {

	/**
	 * 进入播放页并按来源列表续播（首条为点击的视频，后续按来源游标续拉同源内容）。
	 *
	 * 合并了原先 Home 与 Mine 两套不兼容的签名：
	 * `(item, source?, keyword?)` 与 `(item, source, userId)`。
	 *
	 * @param source 列表来源标记：search / liked / collected / user_videos
	 * @param keyword 搜索关键词（仅 source = search）
	 * @param userId 作品列表所属用户 id（仅 source = user_videos）
	 */
	fun navigateToVideoPlayer(
		item: VideoCardInfo,
		source: String? = null,
		keyword: String? = null,
		userId: Long = 0L
	): Unit = unsupported("navigateToVideoPlayer")

	/**
	 * 打开登录/注册入口。
	 * 实现方**不得**清空任务栈：宿主就是主界面，清栈会把整个 App 关掉。
	 */
	fun navigateToAuthEntry(): Unit = unsupported("navigateToAuthEntry")

	/** 打开搜索页 */
	fun navigateToSearch(): Unit = unsupported("navigateToSearch")

	/** 打开搜索结果页 */
	fun navigateToSearchResult(keyword: String): Unit = unsupported("navigateToSearchResult")

	/** 打开资料编辑页 */
	fun navigateToProfileEdit(): Unit = unsupported("navigateToProfileEdit")

	/** 打开单字段编辑页（名字/简介/性别/生日/地区/职业/学校） */
	fun navigateToProfileFieldEdit(field: String): Unit = unsupported("navigateToProfileFieldEdit")

	/** 进入全屏（横屏播放 + 隐藏系统栏） */
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
 * 未来的独立入口），强转会直接抛 ClassCastException，而可空取用只是不响应导航。
 */
val Fragment.mainHost: IMainHost?
	get() = activity as? IMainHost
