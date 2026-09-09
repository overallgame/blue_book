package com.example.blue_book.ui.publish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.blue_book.feature_video.databinding.PublishPageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** 发布视频页：系统选择器选视频 → 分块上传 → 发布元数据 */
@AndroidEntryPoint
class PublishFragment : Fragment() {

	private var _binding: PublishPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: PublishViewModel by viewModels()

	private val pickVideoLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri -> uri?.let { viewModel.dispatch(PublishIntent.SelectMedia(it)) } }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = PublishPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.publishToolbar.setNavigationOnClickListener { back() }
		initWindowInsets()
		initMediaPicker()
		initSubmit()
		observeViewModel()
	}

	/** 全面屏：工具栏避让状态栏，内容避让导航栏 */
	private fun initWindowInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(binding.publishToolbar) { v, insets ->
			v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
			insets
		}
		ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
			v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
			insets
		}
	}

	private fun initMediaPicker() {
		binding.publishMediaContainer.setOnClickListener {
			if (viewModel.uiState.value.isBusy) return@setOnClickListener
			pickVideoLauncher.launch(arrayOf("video/*"))
		}
	}

	private fun initSubmit() {
		binding.publishSubmit.setOnClickListener {
			if (viewModel.uiState.value.isBusy) return@setOnClickListener
			val title = binding.publishTitle.text?.toString().orEmpty()
			val description = binding.publishDescription.text?.toString().orEmpty()
			viewModel.dispatch(PublishIntent.Submit(title, description))
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						val uri = state.mediaUri
						if (uri != null) {
							binding.publishMediaThumb.visibility = View.VISIBLE
							binding.publishMediaPlaceholder.visibility = View.GONE
							Glide.with(requireContext()).load(uri).centerCrop()
								.into(binding.publishMediaThumb)
						} else {
							binding.publishMediaThumb.visibility = View.GONE
							binding.publishMediaPlaceholder.visibility = View.VISIBLE
						}

						val uploading = state.phase == PublishPhase.UPLOADING
						binding.publishProgressGroup.visibility = if (uploading || state.progress > 0) View.VISIBLE else View.GONE
						binding.publishProgress.setProgressCompat(state.progress, true)
						binding.publishProgressText.text = "${state.progress}%"
						binding.publishSubmit.isEnabled = !state.isBusy
						binding.publishSubmit.alpha = if (state.isBusy) 0.5f else 1f
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is PublishEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							PublishEffect.PublishSuccess -> back()
						}
					}
				}
			}
		}
	}

	private fun back() {
		requireActivity().onBackPressedDispatcher.onBackPressed()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
