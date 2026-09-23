package com.example.blue_book.ui.publish

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

/** 发布视频页：系统选择器选视频 → 分块上传 → 发布元数据（带定位城市入"本地"流） */
@AndroidEntryPoint
class PublishFragment : Fragment() {

	private var _binding: PublishPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: PublishViewModel by viewModels()

	private val pickVideoLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri ->
		uri ?: return@registerForActivityResult
		// 取持久化读权限：上传耗时长（大文件可达数分钟），期间进程可能被回收，
		// 拿不到持久权限的话恢复后 URI 已失效，续传会直接失败
		runCatching {
			requireContext().contentResolver.takePersistableUriPermission(
				uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
			)
		}
		viewModel.dispatch(PublishIntent.SelectMedia(uri.toString()))
	}

	/** 定位权限：拒绝不阻塞发布，仅使视频不带地区 */
	private val locationPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { /* 结果无需处理：发布时按实际授权状态取城市 */ }

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
		initResumableBanner()
		initCancel()
		observeViewModel()
		requestLocationPermissionIfNeeded()
		// 查一次本地账本里有没有上次没传完的
		viewModel.dispatch(PublishIntent.Init)
	}

	/** 上次未完成上传的横幅：继续（填回表单）/ 忽略（作废掉） */
	private fun initResumableBanner() {
		binding.publishResumableContinue.setOnClickListener {
			viewModel.dispatch(PublishIntent.OnContinueResumable)
		}
		binding.publishResumableDismiss.setOnClickListener {
			viewModel.dispatch(PublishIntent.OnDismissResumable)
		}
	}

	/**
	 * 取消上传。
	 *
	 * 走 [PublishViewModel.requestCancel] 这个**直接方法**而不是 `dispatch`：UDF 的 intent
	 * 由单一 collector 串行消费，取消如果排队排在上传后面，等它被处理时上传早就结束了。
	 */
	private fun initCancel() {
		binding.publishCancel.setOnClickListener { viewModel.requestCancel() }
	}

	/** 进入发布页时静默申请定位权限（用于发布时带上城市） */
	private fun requestLocationPermissionIfNeeded() {
		val granted = ContextCompat.checkSelfPermission(
			requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
		) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
		}
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
						val publishing = state.phase == PublishPhase.PUBLISHING
						binding.publishProgressGroup.visibility =
							if (uploading || publishing || state.progress > 0) View.VISIBLE else View.GONE
						binding.publishProgress.setProgressCompat(state.progress, true)
						// 分片传完到 publish 返回之间还有一段时间（定位 + 一次网络往返），
						// 用文案表明还在做事，而不是留一个停在 100% 的进度条
						binding.publishProgressText.text = if (publishing) "发布中…" else "${state.progress}%"
						// 取消只在**上传阶段**给：此时服务端已经落了一些分片，取消要连带 abort；
						// 而 publish 一旦发出就没法撤回了（服务端可能已经插了行），给了按钮反而是骗人
						binding.publishCancel.visibility = if (uploading) View.VISIBLE else View.GONE
						binding.publishSubmit.isEnabled = !state.isBusy
						binding.publishSubmit.alpha = if (state.isBusy) 0.5f else 1f

						val resumable = state.resumable
						binding.publishResumable.visibility =
							if (resumable != null && !state.isBusy) View.VISIBLE else View.GONE
						if (resumable != null) {
							binding.publishResumableText.text =
								"上次有一条未完成的上传（已传 ${resumable.progress}%）：${resumable.fileName}"
						}
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
