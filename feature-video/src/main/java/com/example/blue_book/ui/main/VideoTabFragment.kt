package com.example.blue_book.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.blue_book.feature_video.databinding.PublicationPageBinding
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter

class VideoTabFragment : Fragment() {

	private var _binding: PublicationPageBinding? = null
	private val binding get() = _binding!!
	private val selectedTags = mutableSetOf<String>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = PublicationPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 返回按钮
		binding.loadBack.setOnClickListener {
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}

		// 发表按钮
		binding.loadShare.setOnClickListener {
			val title = binding.loadTitle.text?.toString().orEmpty()
			if (title.isBlank()) {
				Toast.makeText(requireContext(), "请输入视频标题", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			// 发布功能待后端 API 接入
			Toast.makeText(requireContext(), "发布功能开发中", Toast.LENGTH_SHORT).show()
		}

		// 标签点击切换选中状态
		binding.tagsSection?.let { tagLayout ->
			for (i in 0 until tagLayout.childCount) {
				val tagView = tagLayout.getChildAt(i)
				tagView.setOnClickListener {
					val tag = (tagView as? android.widget.TextView)?.text?.toString() ?: return@setOnClickListener
					if (tag in selectedTags) {
						selectedTags.remove(tag)
						tagView.alpha = 0.5f
					} else {
						selectedTags.add(tag)
						tagView.alpha = 1f
					}
				}
			}
		}

		// 添加图片/视频
		binding.loadAddImage.setOnClickListener {
			TheRouter.build(RoutePath.IMAGE_PICKER)
				.withString("tag", "publication")
				.navigation(requireContext())
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
