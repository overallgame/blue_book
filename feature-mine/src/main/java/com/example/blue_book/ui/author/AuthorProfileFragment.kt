package com.example.blue_book.ui.author

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.example.blue_book.feature_mine.R
import com.example.blue_book.feature_mine.databinding.AuthorProfilePageBinding
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.openVideoPlayer
import com.example.blue_book.widget.LoginGuideDialog
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AuthorProfileFragment : Fragment() {

	private var _binding: AuthorProfilePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: AuthorProfileViewModel by viewModels()

	/** 当前登录用户，用于隐藏自己的关注按钮 */
	@Inject
	lateinit var currentUser: CurrentUser

	private lateinit var adapter: PreVideoAdapter
	private var userId = 0L
	private var noMoreToasted = false

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = AuthorProfilePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		userId = requireArguments().getLong(ExtraKeys.EXTRA_USER_ID, 0L)
		viewModel.bindUserId(userId)
		initSwipeRefresh()
		initRecyclerView()
		initTopActions()
		initEmptyState()
		initWindowInsets()
		observeViewModel()
		viewModel.dispatch(AuthorProfileIntent.Init)
	}

	private fun initSwipeRefresh() {
		binding.authorSwipeRefresh.setOnRefreshListener {
			noMoreToasted = false
			viewModel.dispatch(AuthorProfileIntent.Refresh)
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> viewModel.dispatch(AuthorProfileIntent.ToggleLike(v)) },
			onClickItem = { v ->
				// 从作者主页进入播放页：压一个独立播放页，返回回到本页。
				// 以点击视频为首条，按 user_videos 来源续拉该作者作品。
				openVideoPlayer(requireContext(), v, source = "user_videos", userId = userId)
			}
		)
		binding.authorVideos.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@AuthorProfileFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1)) {
						val state = viewModel.uiState.value
						if (state.hasMore && !state.isLoadingMore) {
							viewModel.dispatch(AuthorProfileIntent.LoadMore)
						} else if (!state.hasMore && !state.isLoadingMore && state.items.isNotEmpty() && !noMoreToasted) {
							noMoreToasted = true
							Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
						}
					}
				}
			})
		}
	}

	private fun initTopActions() {
		binding.authorBack.setOnClickListener {
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
		binding.authorFollowBtn.setOnClickListener {
			viewModel.dispatch(AuthorProfileIntent.ToggleFollow)
		}
	}

	/** 空态重试：重新触发整页刷新 */
	private fun initEmptyState() {
		binding.authorEmptyRetry.setOnClickListener {
			viewModel.dispatch(AuthorProfileIntent.Refresh)
		}
	}

	/** 背景图延展到状态栏后方：顶栏避让状态栏，底部避让导航栏 */
	private fun initWindowInsets() {
		val root = binding.root
		val initialTopMargin = (binding.authorBack.layoutParams as ViewGroup.MarginLayoutParams).topMargin
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			binding.authorBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = initialTopMargin + bars.top
			}
			root.updatePadding(bottom = bars.bottom)
			insets
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						val profile = state.profile
						if (profile != null) {
							profile.background?.let {
								Glide.with(requireContext()).load(it)
									.into(binding.authorBackgroundImage)
							}
							profile.avatar?.let {
								Glide.with(requireContext()).load(it).into(binding.authorAvatar)
							}
							binding.authorNickname.text = profile.nickname ?: "用户"
							binding.authorFocusNumber.text = profile.followingCount.toString()
							binding.authorFanNumber.text = profile.followerCount.toString()
							binding.authorRevLoveNumber.text = (profile.likedCount + profile.collectedCount).toCountText()
							binding.authorIntroduction.text = profile.introduction.orEmpty()
						}
						bindFollowButton(state)

						adapter.submitAppend(state.items)
						binding.authorSwipeRefresh.isRefreshing = false
						// 空态：无数据且不在加载中（加载失败/无内容）时展示，优先显示错误信息
						if (state.items.isEmpty() && !state.isLoading) {
							binding.authorEmpty.visibility = View.VISIBLE
							binding.authorEmptyText.text = state.message ?: "暂无作品"
						} else {
							binding.authorEmpty.visibility = View.GONE
						}
					}
				}
					launch {
						viewModel.uiEffect.collect { effect ->
							when (effect) {
								is AuthorProfileEffect.ShowToast -> Toast.makeText(
									requireContext(), effect.message, Toast.LENGTH_SHORT
								).show()
								is AuthorProfileEffect.UpdateItem -> adapter.updateVideoList(effect.item)
								AuthorProfileEffect.ShowLoginGuide -> LoginGuideDialog.show(requireActivity())
							}
						}
					}
			}
		}
	}

	/** 关注按钮三态：加载中/本人 → 隐藏；未关注 → 红胶囊；已关注 → 半透明胶囊 */
	private fun bindFollowButton(state: AuthorProfileUiState) {
		val profile = state.profile
		val isSelf = profile != null && profile.id == (currentUser.userId ?: 0L)
		if (profile == null || isSelf) {
			binding.authorFollowBtn.visibility = View.GONE
			return
		}
		binding.authorFollowBtn.visibility = View.VISIBLE
		if (profile.isFollowed) {
			binding.authorFollowBtn.text = "已关注"
			binding.authorFollowBtn.setBackgroundResource(R.drawable.shape_pill)
		} else {
			binding.authorFollowBtn.text = "关注"
			binding.authorFollowBtn.setBackgroundResource(R.drawable.shape_author_follow)
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
