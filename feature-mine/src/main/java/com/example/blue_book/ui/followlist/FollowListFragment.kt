package com.example.blue_book.ui.followlist

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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.example.blue_book.data.UserAccount
import com.example.blue_book.feature_mine.R
import com.example.blue_book.feature_mine.databinding.FollowListPageBinding
import com.example.blue_book.feature_mine.databinding.ItemFollowUserBinding
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowListFragment : Fragment() {

	private var _binding: FollowListPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: FollowListViewModel by viewModels()

	private lateinit var adapter: FollowAdapter
	private var noMoreToasted = false

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FollowListPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val followType = requireArguments().getString(ExtraKeys.EXTRA_FOLLOW_TYPE, "following")
		val userId = requireArguments().getLong(ExtraKeys.EXTRA_USER_ID, 0L)
		viewModel.bind(followType, userId)

		binding.followListTitle.text =
			if (followType == "followers") "我的粉丝" else "我的关注"
		binding.followListBack.setOnClickListener {
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
		binding.followListEmptyRetry.setOnClickListener {
			noMoreToasted = false
			viewModel.dispatch(FollowListIntent.Refresh)
		}

		initRecyclerView()
		initWindowInsets()
		observeViewModel()
		viewModel.dispatch(FollowListIntent.Init)
	}

	private fun initRecyclerView() {
		adapter = FollowAdapter(
			onItemClick = { user ->
				if (user.id > 0) {
					TheRouter.build(RoutePath.USER_PROFILE)
						.withLong(ExtraKeys.EXTRA_USER_ID, user.id)
						.navigation(requireContext())
				}
			},
			onFollowClick = { user -> viewModel.dispatch(FollowListIntent.ToggleFollow(user)) }
		)
		binding.followListRecycler.apply {
			layoutManager = LinearLayoutManager(requireContext())
			adapter = this@FollowListFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1)) {
						val state = viewModel.uiState.value
						if (state.hasMore && !state.isLoadingMore) {
							viewModel.dispatch(FollowListIntent.LoadMore)
						} else if (!state.hasMore && !state.isLoadingMore && state.items.isNotEmpty() && !noMoreToasted) {
							noMoreToasted = true
							Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
						}
					}
				}
			})
		}
	}

	private fun initWindowInsets() {
		val root = binding.root
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			root.updatePadding(top = bars.top, bottom = bars.bottom)
			insets
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitList(state.items)
						if (state.items.isEmpty() && !state.isLoading) {
							binding.followListEmpty.visibility = View.VISIBLE
							binding.followListEmptyText.text = state.message ?: "暂无内容"
						} else {
							binding.followListEmpty.visibility = View.GONE
						}
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is FollowListEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
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

/** 关注/粉丝列表项：头像 + 昵称 + 简介 + 关注胶囊 */
private class FollowAdapter(
	private val onItemClick: (UserAccount) -> Unit,
	private val onFollowClick: (UserAccount) -> Unit
) : ListAdapter<UserAccount, FollowAdapter.VH>(DIFF) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
		val binding = ItemFollowUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return VH(binding)
	}

	override fun onBindViewHolder(holder: VH, position: Int) {
		holder.bind(getItem(position), onItemClick, onFollowClick)
	}

	class VH(private val binding: ItemFollowUserBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(user: UserAccount, onItemClick: (UserAccount) -> Unit, onFollowClick: (UserAccount) -> Unit) {
			Glide.with(binding.root.context).load(user.avatar).placeholder(R.drawable.default_avatar).into(binding.followItemAvatar)
			binding.followItemNickname.text = user.nickname ?: "用户"
			binding.followItemBio.text = user.introduction.orEmpty().ifBlank { "暂无简介" }

			if (user.isFollowed) {
				binding.followItemBtn.text = "已关注"
				binding.followItemBtn.setBackgroundResource(R.drawable.shape_pill)
			} else {
				binding.followItemBtn.text = "关注"
				binding.followItemBtn.setBackgroundResource(R.drawable.shape_author_follow)
			}

			binding.followItemBtn.setOnClickListener { onFollowClick(user) }
			binding.root.setOnClickListener { onItemClick(user) }
		}
	}

	private companion object {
		val DIFF = object : DiffUtil.ItemCallback<UserAccount>() {
			override fun areItemsTheSame(oldItem: UserAccount, newItem: UserAccount): Boolean =
				oldItem.id == newItem.id

			override fun areContentsTheSame(oldItem: UserAccount, newItem: UserAccount): Boolean =
				oldItem == newItem
		}
	}
}
