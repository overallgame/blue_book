package com.example.blue_book.ui.home.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_home.databinding.HomeLocalPageBinding
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.router.openVideoPlayer
import com.example.blue_book.util.LocationHelper
import com.example.blue_book.widget.LoginGuideDialog
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地流：需登录使用。
 * 未登录进入：弹登录引导卡片 + 展示空态（不定位、不加载）；
 * 登录后：首次进入加载推荐内容并定位切换为该城市本地流。
 */
@AndroidEntryPoint
class HomeLocalFragment : Fragment() {

	private var _binding: HomeLocalPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: HomeLocalViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter
	private var isLoading = false
	private var noMoreToasted = false
	private var lastMessage: String? = null

	/** 游客状态（未登录） */
	private var isGuest = false

	/** 是否已初始化（登录态首次进入时触发加载与定位） */
	private var initialized = false

	private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		locationPermissionLauncher = registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { granted ->
			if (granted) {
				resolveLocation()
			} else {
				fallbackToRecommend("未开启定位权限，已展示推荐内容")
			}
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomeLocalPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefresh()
		initRecyclerView()
		initEmptyState()
		observeViewModel()
	}

	override fun onResume() {
		super.onResume()
		// 每次可见：同步登录态；未登录弹引导卡片，登录后首次进入加载并定位
		viewLifecycleOwner.lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: false
			}
			if (!isAdded) return@launch
			isGuest = !logged
			if (logged) {
				binding.homeLocalEmpty.visibility = View.GONE
				if (!initialized) {
					initialized = true
					viewModel.dispatch(HomeLocalIntent.Init)
					initLocalFlow()
				}
			} else {
				binding.homeLocalEmpty.visibility = View.VISIBLE
				LoginGuideDialog.show(requireActivity())
			}
		}
	}

	/** 本地流入口：有权限直接定位，否则申请（拒绝走推荐内容兜底） */
	private fun initLocalFlow() {
		val granted = ContextCompat.checkSelfPermission(
			requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
		) == PackageManager.PERMISSION_GRANTED
		if (granted) {
			resolveLocation()
		} else {
			locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
		}
	}

	/** 取最近已知位置逆地理为城市，成功切换本地流 */
	private fun resolveLocation() {
		viewLifecycleOwner.lifecycleScope.launch {
			val city = withContext(Dispatchers.IO) { LocationHelper.currentCity(requireContext()) }
			if (!isAdded) return@launch
			if (city.isNullOrBlank()) {
				fallbackToRecommend("无法获取位置，已展示推荐内容")
			} else {
				viewModel.dispatch(HomeLocalIntent.InitRegion(city))
			}
		}
	}

	private fun fallbackToRecommend(message: String) {
		if (!isAdded) return
		Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
	}

	private fun initSwipeRefresh() {
		binding.mainLocalPagerSwipeRefreshLayout.setOnRefreshListener {
			if (isGuest) {
				LoginGuideDialog.show(requireActivity())
				binding.mainLocalPagerSwipeRefreshLayout.isRefreshing = false
				return@setOnRefreshListener
			}
			noMoreToasted = false
			viewModel.dispatch(HomeLocalIntent.Refresh)
		}
	}

	private fun initEmptyState() {
		binding.homeLocalEmptyLogin.setOnClickListener {
			LoginGuideDialog.show(requireActivity())
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> guardLike(v) },
			onClickItem = { v ->
				openVideoPlayer(requireContext(), v)
			}
		)
		binding.mainLocalRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@HomeLocalFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1)) {
						val state = viewModel.uiState.value
						if (state.hasMore && !state.isLoading) {
							isLoading = true
							viewModel.dispatch(HomeLocalIntent.LoadMore)
						} else if (!state.hasMore && !state.isLoading && state.items.isNotEmpty() && !noMoreToasted) {
							noMoreToasted = true
							Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
						}
					}
				}
			})
		}
	}

	/** 未登录触发点赞：弹登录引导卡片 */
	private fun guardLike(v: VideoCardInfo) {
		if (isGuest) {
			LoginGuideDialog.show(requireActivity())
		} else {
			viewModel.dispatch(HomeLocalIntent.ToggleLike(v))
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitAppend(state.items)
						isLoading = state.isLoading
						binding.mainLocalPagerSwipeRefreshLayout.isRefreshing = false
						// 本地流无内容/加载失败提示（每次新消息只提示一次）
						state.message?.takeIf { it != lastMessage }?.let {
							lastMessage = it
							Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
						}
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is HomeLocalEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							is HomeLocalEffect.UpdateItem -> adapter.updateVideoList(effect.item)
							HomeLocalEffect.ShowLoginGuide -> LoginGuideDialog.show(requireActivity())
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
