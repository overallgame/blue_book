package com.example.blue_book.ui.message

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.feature_message.databinding.MessagePageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFragment : Fragment() {

	private var _binding: MessagePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MessageViewModel by viewModels()
	private lateinit var adapter: MessageAdapter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = MessagePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initRecyclerView()
		initSwipeRefresh()
		observeViewModel()
		viewModel.dispatch(MessageIntent.Init)
	}

	private fun initRecyclerView() {
		adapter = MessageAdapter { item ->
			viewModel.dispatch(MessageIntent.MarkRead(item.id))
			when (item.type) {
				MessageType.Follow -> Toast.makeText(requireContext(), "关注详情（待实现）", Toast.LENGTH_SHORT).show()
				MessageType.Like, MessageType.Comment -> {
					// 后端 API 就绪后跳转到对应视频
					Toast.makeText(requireContext(), "跳转到视频（待实现）", Toast.LENGTH_SHORT).show()
				}
				MessageType.System -> { /* 系统消息，已读即可 */ }
			}
		}
		binding.messageRecycleView.layoutManager = LinearLayoutManager(requireContext())
		binding.messageRecycleView.adapter = adapter
	}

	private fun initSwipeRefresh() {
		binding.messageSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(MessageIntent.Refresh)
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitList(state.items)
						binding.messageSwipeRefreshLayout.isRefreshing = false
						val hasItems = state.items.isNotEmpty()
						binding.messageRecycleView.visibility = if (hasItems) View.VISIBLE else View.GONE
						binding.messageEmptyLayout.visibility = if (hasItems) View.GONE else View.VISIBLE
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MessageEffect.ShowToast -> Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
							is MessageEffect.NavigateToVideo -> Toast.makeText(requireContext(), "跳转视频 ${effect.aid}", Toast.LENGTH_SHORT).show()
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
