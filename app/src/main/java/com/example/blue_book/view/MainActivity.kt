package com.example.blue_book.view

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.R
import com.example.blue_book.host.IMainHost
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.provider.INotificationProvider
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.home.HomeFragment
import com.example.blue_book.ui.mine.MineFragment
import com.example.blue_book.ui.message.MessageFragment
import com.example.blue_book.ui.video.VideoFragment
import com.example.blue_book.widget.LoginGuideDialog
import com.therouter.TheRouter
import com.therouter.router.Route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面：底部导航 + 四个 Tab（Fragment 承载，导航栏常驻）。
 *
 * 四个 Tab 用 **show/hide** 而不是 replace：切走时保留各自的视图状态
 * （滚动位置、播放进度），同时把隐藏的 Tab 上限压到 STARTED —— 这会触发 onPause，
 * 于是视频 Tab 切走时自动暂停播放（VideoFragment.foreground 依赖的正是 onPause）。
 *
 * **本页面只承载四个一级页面**，没有任何二级页：搜索、资料编辑、播放页、作者主页、
 * 关注列表、发布页、图片选择器、登录页都是独立 Activity，压在 MainActivity 之上，
 * 返回即 finish、回到点击它们的那个页面。因此这里不需要管理返回栈，
 * 返回键只剩「非首页 Tab 先回首页，否则退出」两条规则。
 *
 * 具体分工见 [IMainHost]（本类只向 Tab 提供宿主窗口能力：全屏进出）。
 */
@Route(path = RoutePath.MAIN)
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), IMainHost {

	private lateinit var messageTab: BadgeRadioButton
	private lateinit var radioGroup: RadioGroup
	private lateinit var navGroup: View
	private lateinit var contentContainer: FrameLayout

	/**
	 * 底色恒为深色、不随主题变化的 Tab：视频（纯黑底）与「我的」
	 * （`mine_page_background` 两种主题下都是 #333232，深色封面式设计）。
	 * 这两个 Tab 需要浅色系统栏图标，见 [refreshSystemBarAppearance]。
	 */
	private val inherentlyDarkTabs = setOf(R.id.tab_video, R.id.tab_mine)

	/** 当前选中的导航项（用于防递归导航与判断是否需要切换） */
	private var currentCheckedId = R.id.tab_home

	/** 登录态：null=判断中（放行，避免误拦已登录用户） */
	private var loggedIn: Boolean? = null

	private val locationPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {
			/* 拒绝不阻塞：未授权时本地流自动回退推荐内容 */
		}

	/** Tab 的 id 与 Fragment tag 对应关系 */
	private val tabTags = linkedMapOf(
		R.id.tab_home to TAG_HOME,
		R.id.tab_video to TAG_VIDEO,
		R.id.tab_message to TAG_MESSAGE,
		R.id.tab_mine to TAG_MINE
	)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main_bottom_nav)

		radioGroup = findViewById(R.id.main_navRadioGroup)
		navGroup = findViewById(R.id.main_navRadioGroup)
		contentContainer = findViewById(R.id.main_content)

		setupEdgeToEdge()

		currentCheckedId = savedInstanceState?.getInt(KEY_CURRENT_TAB) ?: R.id.tab_home
		if (savedInstanceState == null) {
			// 一次性把四个 Tab 都挂上，非当前的直接隐藏（同事务内 hide 避免闪现）
			supportFragmentManager.commitNow {
				setReorderingAllowed(true)
				tabTags.forEach { (id, tag) ->
					val fragment = createTabFragment(id)
					add(R.id.main_content, fragment, tag)
					if (id != currentCheckedId) hide(fragment)
				}
			}
		}
		// 旋转恢复时 Fragment 已由 FragmentManager 重建，这里只需把可见性重新对齐
		applyTabVisibility()
		applyTabAppearance()
		radioGroup.check(currentCheckedId)

		radioGroup.setOnCheckedChangeListener { _, checkedId ->
			// 拦截重复选中（防递归导航）
			if (checkedId == currentCheckedId) return@setOnCheckedChangeListener
			switchToTab(checkedId)
		}

		// 底部导航正中发布入口（小红书风格）：未登录先引导，已登录直接打开发布页
		findViewById<ImageView>(R.id.publish_btn).setOnClickListener {
			if (loggedIn == false) {
				LoginGuideDialog.show(this)
			} else {
				TheRouter.build(RoutePath.PUBLISH).navigation(this)
			}
		}

		setupBackHandling()

		messageTab = findViewById(R.id.tab_message)
		refreshUnreadBadge()
		resolveLoginState()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt(KEY_CURRENT_TAB, currentCheckedId)
	}

	// ==================== Tab 切换 ====================

	private fun createTabFragment(checkedId: Int): Fragment = when (checkedId) {
		R.id.tab_video -> VideoFragment()
		R.id.tab_message -> MessageFragment()
		R.id.tab_mine -> MineFragment()
		else -> HomeFragment()
	}

	/** 用户点击 Tab 或程序化切换：改选中项并重算可见性 */
	private fun switchToTab(checkedId: Int) {
		currentCheckedId = checkedId
		applyTabVisibility()
		applyTabAppearance()
		// Tab 切换时同步未读角标（消息页内已读/清空后离开也能立即纠正）
		refreshUnreadBadge()
	}

	/**
	 * 程序化选中某个 Tab（会同步底部导航的选中态）。
	 * 与当前一致时也要重算可见性——例如全屏切换到视频 Tab 后回到本页面。
	 */
	private fun selectTab(checkedId: Int) {
		if (checkedId == currentCheckedId) {
			applyTabVisibility()
		} else {
			// 交由 RadioGroup 触发监听，保证选中态与切换逻辑只有一条路径
			radioGroup.check(checkedId)
		}
	}

	/**
	 * 只对当前 Tab 用 RESUMED，其余上限压到 STARTED。
	 * 压到 STARTED 会触发 onPause —— 视频 Tab 切走后自动暂停，且视图不销毁（保留进度）。
	 */
	private fun applyTabVisibility() {
		val fm = supportFragmentManager
		// 已销毁或状态已保存时提交事务会抛异常；这两种情况也不需要切可见性
		if (fm.isDestroyed || fm.isStateSaved) return
		val transaction = fm.beginTransaction().setReorderingAllowed(true)
		tabTags.forEach { (id, tag) ->
			val fragment = fm.findFragmentByTag(tag) ?: return@forEach
			if (id == currentCheckedId) {
				transaction.show(fragment)
				transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
			} else {
				transaction.hide(fragment)
				transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
			}
		}
		transaction.commitNow()
	}

	/**
	 * 页面外观：视频 Tab 需要黑底（内容区垫黑，视频才不会有白边）。
	 * 系统栏图标另见 [refreshSystemBarAppearance]。
	 *
	 * 底部导航也要跟着垫黑：视频 Tab 是纯黑底，浅色主题下若导航栏仍是主题的浅色底 + 深色文字，
	 * 深色页面下方会多出一条浅色带。见 [applyNavBarAppearance]。
	 *
	 * 「我的」Tab 同样是恒定深色封面（底色 #333232，在 mine 模块），需要时按同一规则加进来即可。
	 */
	private fun applyTabAppearance() {
		val isVideo = currentCheckedId == R.id.tab_video
		contentContainer.setBackgroundColor(if (isVideo) Color.BLACK else Color.TRANSPARENT)
		applyNavBarAppearance(darkPage = isVideo)
		refreshSystemBarAppearance()
	}

	/**
	 * 底部导航的底色与文字色跟随当前 Tab。
	 *
	 * 默认（浅色主题 + 浅色页面）用主题色：透明底 + [navigation_item_selector] 的深浅文字；
	 * 深色页面上则垫成同色底，并换成常量浅色的 [navigation_item_selector_on_dark]——
	 * 否则浅色主题下的深色文字会压在黑底上看不清。
	 */
	private fun applyNavBarAppearance(darkPage: Boolean) {
		navGroup.setBackgroundColor(if (darkPage) Color.BLACK else Color.TRANSPARENT)
		val selectorRes = if (darkPage) {
			R.color.navigation_item_selector_on_dark
		} else {
			R.color.navigation_item_selector
		}
		val textColors = ContextCompat.getColorStateList(this, selectorRes) ?: return
		tabTags.keys.forEach { id ->
			findViewById<TextView>(id)?.setTextColor(textColors)
		}
	}

	/**
	 * 系统栏图标明暗——判据是**最上层页面的底色**，不是主题、也不是 Tab：
	 * - 深色底 → 浅色图标；浅色底 → 深色图标
	 *
	 * 之所以不能只看主题：视频 Tab 是纯黑底、「我的」Tab 的
	 * `mine_page_background` 两种主题下都是 #333232（深色封面式设计），
	 * 这两个 Tab 在浅色主题下也必须用浅色图标，否则深色图标压在深色底上看不清。
	 * 二级页都是独立 Activity，会各自设置自己的系统栏外观，与本页面无关。
	 */
	private fun refreshSystemBarAppearance() {
		val isNightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
			Configuration.UI_MODE_NIGHT_YES
		val useLightIcons = currentCheckedId in inherentlyDarkTabs || isNightTheme
		// isAppearanceLightStatusBars = true 表示「状态栏背景是浅色」→ 用深色图标
		WindowInsetsControllerCompat(window, window.decorView).apply {
			isAppearanceLightStatusBars = !useLightIcons
			isAppearanceLightNavigationBars = !useLightIcons
		}
	}

	/**
	 * 返回键：非首页 Tab 先回首页，否则交回系统退出。
	 *
	 * 这里**故意用不带 LifecycleOwner 的重载**：`addCallback(callback)` 立即入队，
	 * 而 `addCallback(owner, callback)` 要等 owner 到达 ON_START 才入队。
	 * API 29+ 上 Activity 自身的 ON_START 在 Fragment 的 ON_START **之后**派发
	 * （由 `Activity.onActivityPostStarted` 驱动），用带 owner 的重载会把自己排在
	 * Tab 里 VideoFragment 的全屏回调**之后**，而返回键是后入队者优先——
	 * 于是「全屏时按返回」会被本回调抢走，表现为切到首页 Tab 却仍横屏、且底部导航不可见。
	 * 立即入队保证 Fragment 的回调（后入队）优先：全屏时先退全屏。
	 */
	private fun setupBackHandling() {
		onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				when {
					currentCheckedId != R.id.tab_home -> selectTab(R.id.tab_home)

					else -> {
						// 交回系统（退出 App）；恢复 enabled 以便未退出时仍可用
						isEnabled = false
						onBackPressedDispatcher.onBackPressed()
						isEnabled = true
					}
				}
			}
		})
	}

	// ==================== 窗口与边距 ====================

	/**
	 * 全面屏：内容延展到系统栏后方，各页面自行避让。
	 * 底部导航按 bars.bottom 抬高，避免压在系统手势条上。
	 */
	private fun setupEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		ViewCompat.setOnApplyWindowInsetsListener(navGroup) { v, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.updatePadding(bottom = bars.bottom)
			insets
		}
	}

	/** 登录态：已登录在首页先行申请定位权限（未登录引导由首页负责，避免 Dialog 被首页覆盖） */
	private fun resolveLoginState() {
		lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: false
			}
			loggedIn = logged
			if (logged) {
				requestLocationPermission()
			}
		}
	}

	/** 首页先行动态申请定位权限（"本地"流依赖，未授权时端上自动降级） */
	private fun requestLocationPermission() {
		val granted = ContextCompat.checkSelfPermission(
			this, Manifest.permission.ACCESS_COARSE_LOCATION
		) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
		}
	}

	override fun onResume() {
		super.onResume()
		// 重新对齐系统栏图标：从登录页/其它 Activity 返回、或弹过 Dialog 之后，
		// 系统栏外观可能被别的窗口改掉，这里按当前主题与 Tab 再设一次（幂等）
		applyTabAppearance()
		// 从消息页返回后同步角标
		refreshUnreadBadge()
	}

	/** 未读消息角标：静默刷新（未登录/失败时清零） */
	private fun refreshUnreadBadge() {
		lifecycleScope.launch {
			val count = withContext(Dispatchers.IO) {
				TheRouter.get(INotificationProvider::class.java)
					?.unreadCount()?.getOrNull() ?: 0L
			}
			messageTab.setUnreadCount(count.toInt())
		}
	}

	// ==================== IMainHost ====================
	// 导航不再走宿主：搜索/资料编辑/播放页/登录页都是独立 Activity，各调用点直接用路由。
	// 本类只提供宿主独有的窗口能力——只有承载页面的 Activity 才能转屏与收起导航栏。

	/** 底部导航常驻在内容区下方，页面不必自己避让系统手势条（见 [IMainHost.providesBottomNav]） */
	override val providesBottomNav: Boolean get() = true

	override fun enterFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
		// 全屏时连底部导航一起收起，否则不是真正的全屏
		navGroup.visibility = View.GONE
		WindowInsetsControllerCompat(window, window.decorView).apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
	}

	override fun exitFullscreen() {
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		navGroup.visibility = View.VISIBLE
		WindowInsetsControllerCompat(window, window.decorView)
			.show(WindowInsetsCompat.Type.systemBars())
	}

	private companion object {
		const val TAG_HOME = "tab_home"
		const val TAG_VIDEO = "tab_video"
		const val TAG_MESSAGE = "tab_message"
		const val TAG_MINE = "tab_mine"
		const val KEY_CURRENT_TAB = "main_current_tab"
	}
}
