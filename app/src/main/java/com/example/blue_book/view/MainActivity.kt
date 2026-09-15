package com.example.blue_book.view

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioGroup
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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.R
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.host.IMainHost
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.provider.INotificationProvider
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.home.HomeFragment
import com.example.blue_book.ui.mine.MineFragment
import com.example.blue_book.ui.message.MessageFragment
import com.example.blue_book.ui.profile.ProfileFieldEditFragment
import com.example.blue_book.ui.profile.UserProfileEditFragment
import com.example.blue_book.ui.search.AfterSearchFragment
import com.example.blue_book.ui.search.SearchFragment
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
 * 二级页（搜索结果、资料编辑等）用 **add** 叠在 Tab 之上并压住它，返回时弹出即恢复 Tab。
 * 这样二级页天然覆盖内容区，底部导航保持可见。
 *
 * 本类是从「每个 Tab 一个 Activity」收敛而来的宿主，原先由各 Activity 提供的导航方法
 * 现在集中在这里（见 [IMainHost]）。
 */
@Route(path = RoutePath.MAIN)
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), IMainHost {

	private lateinit var messageTab: BadgeRadioButton
	private lateinit var radioGroup: RadioGroup
	private lateinit var navGroup: View
	private lateinit var contentContainer: FrameLayout

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

		// 二级页全部退出后恢复当前 Tab（Tab 在压二级页时被 hide 了）
		supportFragmentManager.addOnBackStackChangedListener {
			if (supportFragmentManager.backStackEntryCount == 0) applyTabVisibility()
		}

		setupBackHandling()

		messageTab = findViewById(R.id.tab_message)
		refreshUnreadBadge()
		resolveLoginState()

		// 非 Tab 入口（作者主页等）带视频参数路由过来：切到视频 Tab 播放
		consumeVideoIntent(intent)
	}

	/**
	 * singleTask 复用时必须处理新 Intent：带视频参数则切到视频 Tab 播放。
	 * 不处理会导致「点了另一个视频却还在播上一个」。
	 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		consumeVideoIntent(intent)
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

	/** 用户点击 Tab 或程序化切换：先清掉二级页，再切可见性 */
	private fun switchToTab(checkedId: Int) {
		clearDetailPages()
		currentCheckedId = checkedId
		applyTabVisibility()
		applyTabAppearance()
		// Tab 切换时同步未读角标（消息页内已读/清空后离开也能立即纠正）
		refreshUnreadBadge()
	}

	/**
	 * 程序化选中某个 Tab（会同步底部导航的选中态）。
	 * 与当前一致时也要清二级页并重算可见性——例如从视频详情返回视频 Tab。
	 */
	private fun selectTab(checkedId: Int) {
		if (checkedId == currentCheckedId) {
			clearDetailPages()
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
		fm.commitNow {
			setReorderingAllowed(true)
			tabTags.forEach { (id, tag) ->
				val fragment = fm.findFragmentByTag(tag) ?: return@forEach
				if (id == currentCheckedId) {
					show(fragment)
					setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
				} else {
					hide(fragment)
					setMaxLifecycle(fragment, Lifecycle.State.STARTED)
				}
			}
		}
	}

	/** 视频 Tab 需要黑底 + 浅色系统栏图标；其余 Tab 用主题默认（深色图标） */
	private fun applyTabAppearance() {
		val isVideo = currentCheckedId == R.id.tab_video
		contentContainer.setBackgroundColor(if (isVideo) Color.BLACK else Color.TRANSPARENT)
		WindowInsetsControllerCompat(window, window.decorView).apply {
			isAppearanceLightStatusBars = !isVideo
			isAppearanceLightNavigationBars = !isVideo
		}
	}

	/** 清掉全部二级页（Tab 切换时调用，避免二级页跨 Tab 残留） */
	private fun clearDetailPages() {
		if (supportFragmentManager.backStackEntryCount > 0) {
			supportFragmentManager.popBackStackImmediate(
				null, FragmentManager.POP_BACK_STACK_INCLUSIVE
			)
		}
	}

	/** 二级页：叠在当前 Tab 之上，并把当前 Tab 压到 STARTED（否则背后的视频会继续播） */
	private fun pushDetail(fragment: Fragment) {
		val currentTag = tabTags[currentCheckedId]
		val currentTab = currentTag?.let { supportFragmentManager.findFragmentByTag(it) }
		supportFragmentManager.commitNow {
			setReorderingAllowed(true)
			currentTab?.let {
				hide(it)
				setMaxLifecycle(it, Lifecycle.State.STARTED)
			}
			add(R.id.main_content, fragment)
			addToBackStack(null)
		}
	}

	/**
	 * 返回键：有二级页先弹二级页；否则非首页 Tab 先回首页；否则交回系统退出。
	 * （VideoFragment 全屏时它自己的回调优先级更高，会先处理退出全屏）
	 */
	private fun setupBackHandling() {
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				when {
					supportFragmentManager.backStackEntryCount > 0 ->
						supportFragmentManager.popBackStack()

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

	override fun navigateToSearch() {
		pushDetail(SearchFragment())
	}

	override fun navigateToSearchResult(keyword: String) {
		pushDetail(AfterSearchFragment().apply {
			arguments = Bundle().apply { putString(ExtraKeys.EXTRA_KEYWORD, keyword) }
		})
	}

	override fun navigateToProfileEdit() {
		pushDetail(UserProfileEditFragment())
	}

	override fun navigateToProfileFieldEdit(field: String) {
		pushDetail(ProfileFieldEditFragment().apply {
			arguments = Bundle().apply { putString(ProfileFieldEditFragment.ARG_FIELD, field) }
		})
	}

	override fun navigateToAuthEntry() {
		// 不清空任务栈也不 finish：本页面就是主界面，清栈会把整个 App 关掉。
		// 登录成功由 AuthActivity.finishAuth() 自己回到 MAIN。
		TheRouter.build(RoutePath.AUTH).navigation(this)
	}

	override fun navigateToVideoPlayer(
		item: VideoCardInfo,
		source: String?,
		keyword: String?,
		userId: Long
	) {
		showVideoWith(Bundle().apply {
			putParcelable(ExtraKeys.EXTRA_VIDEO, item)
			source?.let { putString(ExtraKeys.EXTRA_SOURCE, it) }
			keyword?.let { putString(ExtraKeys.EXTRA_KEYWORD, it) }
			putLong(ExtraKeys.EXTRA_SOURCE_USER_ID, userId)
		})
	}

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

	// ==================== 视频入口 ====================

	/**
	 * 播放指定视频：**重建**视频 Tab 的 Fragment，而不是复用旧实例。
	 * 这是原 VideoActivity.onNewIntent 的等价物——不复用是为了避免
	 * 「点了另一个视频却还在播上一个」。
	 */
	private fun showVideoWith(args: Bundle) {
		clearDetailPages()
		val old = supportFragmentManager.findFragmentByTag(TAG_VIDEO)
		supportFragmentManager.commitNow {
			setReorderingAllowed(true)
			old?.let { remove(it) }
			add(R.id.main_content, VideoFragment().apply { arguments = args }, TAG_VIDEO)
		}
		selectTab(R.id.tab_video)
		applyTabVisibility()
	}

	/** 消费 Intent 里的视频参数（非 Tab 页面路由过来时使用） */
	private fun consumeVideoIntent(source: Intent?) {
		if (source == null || !source.hasExtra(ExtraKeys.EXTRA_VIDEO)) return
		val item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			source.getParcelableExtra(ExtraKeys.EXTRA_VIDEO, VideoCardInfo::class.java)
		} else {
			@Suppress("DEPRECATION")
			source.getParcelableExtra(ExtraKeys.EXTRA_VIDEO)
		} ?: return
		showVideoWith(Bundle().apply {
			putParcelable(ExtraKeys.EXTRA_VIDEO, item)
			source.getStringExtra(ExtraKeys.EXTRA_SOURCE)?.let { putString(ExtraKeys.EXTRA_SOURCE, it) }
			source.getStringExtra(ExtraKeys.EXTRA_KEYWORD)?.let { putString(ExtraKeys.EXTRA_KEYWORD, it) }
			putLong(
				ExtraKeys.EXTRA_SOURCE_USER_ID,
				source.getLongExtra(ExtraKeys.EXTRA_SOURCE_USER_ID, 0L)
			)
		})
	}

	private companion object {
		const val TAG_HOME = "tab_home"
		const val TAG_VIDEO = "tab_video"
		const val TAG_MESSAGE = "tab_message"
		const val TAG_MINE = "tab_mine"
		const val KEY_CURRENT_TAB = "main_current_tab"
	}
}
