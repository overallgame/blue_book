package com.example.blue_book.presentation.comment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blue_book.databinding.FragmentCommentBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CommentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentCommentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentViewModel by viewModels()

    private lateinit var commentAdapter: CommentAdapter

    private var currentUserId: Long = 0
    private var videoId: Long = 0

    companion object {
        private const val ARG_VIDEO_ID = "video_id"
        private const val ARG_USER_ID = "user_id"

        const val TAG = "CommentBottomSheet"

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
        binding.commentToolbar.setNavigationOnClickListener {
            dismiss()
        }
    }

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
                viewModel.deleteComment(comment.id)
            },
            onLoadReplies = { comment ->
                viewModel.loadReplies(comment.id)
            }
        )

        binding.commentRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentAdapter
        }

        binding.commentSwipeRefresh.setOnRefreshListener {
            viewModel.loadComments(videoId)
        }
    }

    private fun setupInput() {
        binding.commentSendBtn.setOnClickListener {
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
}
