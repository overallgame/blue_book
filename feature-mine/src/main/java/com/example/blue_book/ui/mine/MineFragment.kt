package com.example.blue_book.ui.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.blue_book.feature_mine.R
import com.example.blue_book.feature_mine.databinding.MinePageBinding
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.mine.page.MineCollectionFragment
import com.example.blue_book.ui.mine.page.MineLoveFragment
import com.example.blue_book.ui.mine.page.MineWorkFragment
import com.therouter.TheRouter
import com.example.blue_book.datastore.ThemeMode
import com.example.blue_book.datastore.ThemeRepository
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.widget.LoginGuideDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MineFragment : Fragment() {
	@Inject
	lateinit var authProvider: IAuthProvider


	private var _binding: MinePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MineViewModel by viewModels()
	private lateinit var fragments: List<Fragment>
	private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

	/** 当前登录用户（关注/粉丝列表入口使用） */
	@Inject
	lateinit var currentUser: CurrentUser

	/** 主题偏好（深色模式设置） */
	@Inject
	lateinit var themeRepository: ThemeRepository

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = MinePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initActivityResult()
		initSwipeRefreshLayout()
		initWindowInsets()
		initNavigationView()
		initTopActions()
		initRadioGroup()
		initViewPager()
		initImagePickers()
		observeViewModel()
		viewModel.dispatch(MineIntent.Init)
	}

	private var firstResume = true

	/** 游客状态（未登录）：浏览可，功能使用引导登录 */
	private var isGuest = false

	/** 最近一次已提示的加载错误（避免重复 Toast） */
	private var lastMessage: String? = null

	/** 从资料编辑/播放页返回时刷新；未登录进入弹登录引导卡片 */
	override fun onResume() {
		super.onResume()
		viewLifecycleOwner.lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				authProvider.isLoggedIn()
			}
			if (!isAdded) return@launch
			isGuest = !logged
			if (logged) {
				if (firstResume) firstResume = false else viewModel.dispatch(MineIntent.Refresh)
			} else {
				LoginGuideDialog.show(requireActivity())
			}
		}
	}

	/** 未登录使用功能：弹登录引导卡片，不执行动作 */
	private fun guardLogin(action: () -> Unit) {
		if (isGuest) {
			LoginGuideDialog.show(requireActivity())
		} else {
			action()
		}
	}

	private fun initActivityResult() {
		pickImageLauncher = registerForActivityResult(
			ActivityResultContracts.StartActivityForResult()
		) { result ->
			if (result.resultCode == AppCompatActivity.RESULT_OK) {
				val uri = result.data?.data ?: return@registerForActivityResult
				val tag = result.data?.getStringExtra("tag")
				when (tag) {
					"avatar" -> {
						// 用 Glide 而非 setImageURI：后者会按原始尺寸同步解码
						// （12MP 照片在 ARGB_8888 下约 48MB），且 URI 不可读时会抛异常崩溃
						Glide.with(requireContext()).load(uri).into(binding.mineAvatar)
						viewLifecycleOwner.lifecycleScope.launch {
							viewModel.dispatch(MineIntent.UpdateAvatar(uri.toString()))
						}
					}
					"backgroundImage" -> {
						Glide.with(requireContext()).load(uri).into(binding.mineBackgroundImage)
						viewLifecycleOwner.lifecycleScope.launch {
							viewModel.dispatch(MineIntent.UpdateBackground(uri.toString()))
						}
					}
				}
			}
		}
	}

	private fun initSwipeRefreshLayout() {
		binding.mineSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(MineIntent.Refresh)
		}
	}

	private fun initNavigationView() {
		binding.mineNavButton.setOnClickListener {
			binding.layoutMine.openDrawer(GravityCompat.START)
		}
		binding.minePagerNavigationView.setNavigationItemSelectedListener { menuItem ->
			when (menuItem.itemId) {
				R.id.menu_theme_mode -> {
					binding.layoutMine.closeDrawers()
					showThemeModeDialog()
					true
				}

				com.example.blue_book.lib_base.R.id.menu_backLogin -> {
					viewModel.dispatch(MineIntent.Logout)
					true
				}

				else -> {
					binding.layoutMine.closeDrawers()
					true
				}
			}
		}
	}

	/** 深色模式三选：跟随系统 / 浅色 / 深色（持久化 + 立即生效） */
	private fun showThemeModeDialog() {
		val modes = ThemeMode.entries
		val labels = arrayOf("跟随系统", "浅色", "深色")
		viewLifecycleOwner.lifecycleScope.launch {
			val current = themeRepository.getThemeMode()
			AlertDialog.Builder(requireContext())
				.setTitle("深色模式")
				.setSingleChoiceItems(labels, modes.indexOf(current)) { dialog, which ->
					val mode = modes[which]
					viewLifecycleOwner.lifecycleScope.launch { themeRepository.setThemeMode(mode) }
					AppCompatDelegate.setDefaultNightMode(
						when (mode) {
							ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
							ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
							ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
						}
					)
					dialog.dismiss()
				}
				.show()
		}
	}

	/**
	 * 全面屏：顶栏下移避让状态栏；背景图自然延展到状态栏后方。
	 *
	 * 底部**不再**加 bars.bottom 内边距：底部导航栏由宿主常驻在内容区下方，
	 * 这里再加一次会把内容白白抬高一条导航栏的高度。
	 */
	private fun initWindowInsets() {
		val initialTopMargin = (binding.mineNavButton.layoutParams as ViewGroup.MarginLayoutParams).topMargin
		ViewCompat.setOnApplyWindowInsetsListener(binding.mineContent) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			binding.mineNavButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = initialTopMargin + bars.top
			}
			insets
		}
	}

	private fun initTopActions() {
		binding.mineScan.setOnClickListener {
			guardLogin {
				Toast.makeText(requireContext(), "扫一扫即将上线", Toast.LENGTH_SHORT).show()
			}
		}
		binding.mineShare.setOnClickListener {
			guardLogin {
				Toast.makeText(requireContext(), "分享即将上线", Toast.LENGTH_SHORT).show()
			}
		}
		binding.mineCopyId.setOnClickListener { guardLogin { copyXhsId() } }
		// 关注/粉丝列表入口：点击用户列表项进入作者主页
		binding.mineFocus.setOnClickListener { guardLogin { navigateFollowList("following") } }
		binding.mineFan.setOnClickListener { guardLogin { navigateFollowList("followers") } }
	}

	private fun navigateFollowList(type: String) {
		val myId = currentUser.userId ?: run {
			Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
			return
		}
		TheRouter.build(RoutePath.FOLLOW_LIST)
			.withString(ExtraKeys.EXTRA_FOLLOW_TYPE, type)
			.withLong(ExtraKeys.EXTRA_USER_ID, myId)
			.navigation(requireContext())
	}

	/** 上传失败回滚：重新绑定服务端原图（无原图时恢复占位） */
	private fun restoreImage(tag: String) {
		val user = viewModel.uiState.value.user
		when (tag) {
			"avatar" -> {
				val url = user?.avatar
				if (url != null) {
					Glide.with(requireContext()).load(url).into(binding.mineAvatar)
				} else {
					binding.mineAvatar.setImageResource(com.example.blue_book.lib_base.R.drawable.default_avatar)
				}
			}
			"background" -> {
				val url = user?.background
				if (url != null) {
					Glide.with(requireContext()).load(url).into(binding.mineBackgroundImage)
				} else {
					binding.mineBackgroundImage.setImageDrawable(null)
				}
			}
		}
	}

	private fun copyXhsId() {
		val xhsId = viewModel.uiState.value.user?.xhsId?.takeIf { it.isNotBlank() } ?: return
		val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText("xhs_id", xhsId))
		Toast.makeText(requireContext(), "已复制小红书号", Toast.LENGTH_SHORT).show()
	}

	private fun initRadioGroup() {
		binding.mineNavRadioGroup.setOnCheckedChangeListener { _, checkId ->
			when (checkId) {
				binding.mineWork.id -> binding.mineViewPager.currentItem = 0
				binding.mineCollection.id -> binding.mineViewPager.currentItem = 1
				binding.mineLove.id -> binding.mineViewPager.currentItem = 2
			}
		}
		binding.mineEditUserProfile.setOnClickListener {
			guardLogin { TheRouter.build(RoutePath.PROFILE_EDIT).navigation(requireContext()) }
		}
	}

	private fun initImagePickers() {
		// 头像/背景替换属于登录后功能
		binding.mineAvatar.setOnClickListener {
			guardLogin { openCustomImagePicker("avatar") }
		}
		binding.mineBackgroundImage.setOnClickListener {
			guardLogin { openCustomImagePicker("backgroundImage") }
		}
	}

	private fun openCustomImagePicker(tag: String) {
		val intent = TheRouter.build(RoutePath.IMAGE_PICKER)
			.withString("tag", tag)
			.createIntent(requireContext())
		pickImageLauncher.launch(intent)
	}

	private fun initViewPager() {
		binding.mineViewPager.run {
			fragments = listOf(MineWorkFragment(), MineCollectionFragment(), MineLoveFragment())
			adapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
				override fun getItemCount(): Int = fragments.size
				override fun createFragment(position: Int): Fragment = fragments[position]
			}
			registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
				override fun onPageSelected(position: Int) {
					super.onPageSelected(position)
					when (position) {
						0 -> binding.mineNavRadioGroup.check(binding.mineWork.id)
						1 -> binding.mineNavRadioGroup.check(binding.mineCollection.id)
						2 -> binding.mineNavRadioGroup.check(binding.mineLove.id)
					}
				}
			})
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						val user = state.user
						if (user != null) {
							user.background?.let {
								Glide.with(requireContext()).load(it)
									.into(binding.mineBackgroundImage)
							}
							user.avatar?.let {
								Glide.with(requireContext()).load(it).into(binding.mineAvatar)
							}
							binding.mineNickname.text = user.nickname ?: user.phone
							binding.mineXhsId.text = "小红书号：${user.xhsId ?: "--"}"
							binding.mineIntroduction.text = user.introduction.orEmpty()
							binding.mineFocusNumber.text = user.followingCount.toString()
							binding.mineFanNumber.text = user.followerCount.toString()
							binding.mineRevLoveNumber.text = (user.likedCount + user.collectedCount).toCountText()
						} else if (isGuest) {
							// 游客：明确标识（已登录用户在资料加载完成前不覆盖昵称）
							binding.mineNickname.text = "未登录"
						}
						// 加载失败等提示（同一条只提示一次）
						state.message?.takeIf { it.isNotBlank() && it != lastMessage }?.let {
							lastMessage = it
							Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
						}
						binding.mineSwipeRefreshLayout.isRefreshing = false
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MineEffect.ShowToast -> Toast.makeText(
								requireContext(),
								effect.message,
								Toast.LENGTH_SHORT
							).show()
							is MineEffect.NavigateToLogin -> {
								TheRouter.build(RoutePath.AUTH).navigation(requireContext())
							}
							is MineEffect.ImageUploadFailed -> restoreImage(effect.tag)
						}
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

/** 统计数字格式化：1.2k / 3.4w */
private fun Long.toCountText(): String = when {
	this >= 10000 -> "%.1fw".format(this / 10000.0)
	this >= 1000 -> "%.1fk".format(this / 1000.0)
	else -> toString()
}