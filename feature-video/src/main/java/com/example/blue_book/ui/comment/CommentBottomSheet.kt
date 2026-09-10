package com.example.blue_book.ui.comment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.feature_video.databinding.FragmentCommentBinding
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.widget.LoginGuideDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommentBottomSheet : BottomSheetDialogFragment() {

	private var _binding: FragmentCommentBinding? = null
	private val binding get() = _binding!!

	private val viewModel: CommentViewModel by viewModels()

	/** 当前登录用户：游客可看评论，发送/回复需登录 */
	@Inject
	lateinit var currentUser: CurrentUser

	private lateinit var commentAdapter: CommentAdapter

	private var currentUserId: Long = 0
	private var videoId: Long = 0

	/** 弹层期间的评论数增量：发表 +1，删除 -1（回复也计入总评论数） */
	private var commentDelta = 0

	/** "没有更多了"提示只弹一次（触底无更多数据时） */
	private var noMoreToasted = false

	companion object {
		private const val ARG_VIDEO_ID = "video_id"
		private const val ARG_USER_ID = "user_id"

		const val TAG = "CommentBottomSheet"

		/** 关闭评论弹层后，向播放页回传评论数增量的 Result Key */
		const val RESULT_COMMENT_DELTA = "result_comment_delta"
		const val KEY_VIDEO_ID = "key_video_id"
		const val KEY_DELTA = "key_delta"

		fun newInstance(videoId: Long, userId: Long): CommentBottomSheet {
			return CommentBottomSheet().apply {
				arguments = Bundle().apply {
					putLong(ARG_VIDEO_ID, videoId)
					putLong(ARG_USER_ID, userId)
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		videoId = arguments?.getLong(ARG_VIDEO_ID) ?: 0
		currentUserId = arguments?.getLong(ARG_USER_ID) ?: 0
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentCommentBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setupBottomSheet()
		setupToolbar()
		setupRecyclerView()
		setupInput()
		observeState()

		if (videoId > 0) {
			viewModel.loadComments(videoId)
		}
	}

	private fun setupBottomSheet() {
		(dialog as? BottomSheetDialog)?.behavior?.apply {
			state = BottomSheetBehavior.STATE_EXPANDED
			skipCollapsed = true
			peekHeight = resources.displayMetrics.heightPixels / 2
		}
	}

	private fun setupToolbar() {
		binding.commentDragHandle.setOnClickListener { dismiss() }
	}

	@SuppressLint("SetTextI18n")
	private fun setupRecyclerView() {
		commentAdapter = CommentAdapter(
			currentUserId = currentUserId,
			onLikeClick = { comment -> viewModel.likeComment(comment.id) },
			onReplyClick = { comment ->
				viewModel.setReplyTo(comment)
				binding.commentInput.requestFocus()
				binding.commentReplyHint.visibility = View.VISIBLE
				binding.commentReplyHint.text = "回复 @${comment.nickname}"
			},
			onDeleteClick = { comment ->
				// 对齐后端：删除仅软删单条并 incrementCommentCount(-1)，回复不级联计数
				commentDelta -= 1
				viewModel.deleteComment(comment.id)
			},
			onLoadReplies = { comment ->
				viewModel.loadReplies(comment.id)
			}
		)

		binding.commentRecycler.apply {
			layoutManager = LinearLayoutManager(requireContext())
			adapter = commentAdapter
			// 触底加载下一页评论（根评论游标分页）
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
					val lastVisible = layoutManager.findLastVisibleItemPosition()
					val state = viewModel.uiState.value
					if (state.hasMore && !state.isLoadingMore && lastVisible >= commentAdapter.itemCount - 3) {
						viewModel.loadMore()
					} else if (!state.hasMore && !state.isLoadingMore && state.comments.isNotEmpty() && !noMoreToasted) {
						noMoreToasted = true
						Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
					}
				}
			})
		}

		binding.commentSwipeRefresh.setOnRefreshListener {
			noMoreToasted = false
			viewModel.loadComments(videoId, refresh = true)
		}
	}

	private fun setupInput() {
		binding.commentSendBtn.setOnClickListener {
			// 游客可看评论，发送/回复需登录
			if (currentUser.userId == null) {
				LoginGuideDialog.show(requireActivity())
				return@setOnClickListener
			}
			val content = binding.commentInput.text.toString()
			if (viewModel.uiState.value.replyToComment != null) {
				viewModel.replyComment(content)
			} else {
				viewModel.postComment(content)
			}
			binding.commentInput.text?.clear()
			binding.commentReplyHint.visibility = View.GONE
			viewModel.setReplyTo(null)
		}
	}

	private fun observeState() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						binding.commentSwipeRefresh.isRefreshing = false

						binding.commentLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
						binding.commentEmpty.visibility = if (!state.isLoading && state.comments.isEmpty()) View.VISIBLE else View.GONE

						commentAdapter.submitList(state.comments)

						state.error?.let { error ->
							Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
							viewModel.clearError()
						}

						if (state.postSuccess) {
							Toast.makeText(requireContext(), "评论成功", Toast.LENGTH_SHORT).show()
							commentDelta += 1
							viewModel.clearPostSuccess()
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

	/** 关闭弹层时把评论数增量回传给播放页（data层为 aid→Long 的简单封装） */
	override fun onDismiss(dialog: DialogInterface) {
		super.onDismiss(dialog)
		if (commentDelta != 0 && isAdded) {
			setFragmentResult(
				RESULT_COMMENT_DELTA,
				Bundle().apply {
					putLong(KEY_VIDEO_ID, videoId)
					putInt(KEY_DELTA, commentDelta)
				}
			)
		}
	}
}
