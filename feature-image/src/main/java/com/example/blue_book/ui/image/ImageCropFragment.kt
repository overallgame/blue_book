package com.example.blue_book.ui.image

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.blue_book.feature_image.R
import java.io.File
import java.io.FileOutputStream

/**
 * 裁剪页（小红书风格）：黑底 + 暗化遮罩 + 九宫格 + 顶部关闭/完成 + 底部旋转。
 * 裁剪比例按业务 tag 决定：头像 1:1，背景图 16:9。
 */
class ImageCropFragment : Fragment() {

	private var uri: Uri? = null
	private var tag: String? = null
	private var cropView: CropImageView? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		uri = arguments?.let { args ->
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				args.getParcelable(ARG_URI, Uri::class.java)
			} else {
				@Suppress("DEPRECATION")
				args.getParcelable(ARG_URI)
			}
		}
		tag = arguments?.getString(ARG_TAG)
		// 清理历史裁剪临时文件（超过 1 小时的），避免缓存目录累积
		cleanStaleCropFiles()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return inflater.inflate(R.layout.fragment_image_crop, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val crop = view.findViewById<CropImageView>(R.id.cropImageView)
		cropView = crop

		// 比例：头像 1:1；背景图 16:9；其余默认 1:1
		crop.setAspectRatio(if (tag == TAG_BACKGROUND) 16f / 9f else 1f)
		uri?.let { crop.setImageUri(it) }

		view.findViewById<View>(R.id.crop_close).setOnClickListener {
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
		view.findViewById<View>(R.id.crop_rotate).setOnClickListener {
			crop.rotate90()
		}
		view.findViewById<View>(R.id.crop_done).setOnClickListener {
			saveAndFinish()
		}

		initWindowInsets(view)
	}

	/** 黑底页面延展到系统栏后方：顶部栏/底部栏避让 */
	private fun initWindowInsets(root: View) {
		val toolbar = root.findViewById<View>(R.id.crop_toolbar)
		val bottomBar = root.findViewById<View>(R.id.crop_bottom_bar)
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			toolbar.updatePadding(top = bars.top)
			bottomBar.updatePadding(bottom = bars.bottom)
			insets
		}
	}

	private fun saveAndFinish() {
		val bitmap = cropView?.getCroppedBitmap() ?: return
		val context = requireContext()
		val file = File(context.cacheDir, "custom_crop_${System.currentTimeMillis()}.jpg")
		FileOutputStream(file).use { out ->
			bitmap.compress(Bitmap.CompressFormat.JPEG, CropImageView.OUTPUT_QUALITY, out)
		}
		(activity as? ImagePickerActivity)?.finishWithResult(Uri.fromFile(file), tag)
	}

	/** 清理超过 1 小时的裁剪临时文件 */
	private fun cleanStaleCropFiles() {
		val context = context ?: return
		val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
		context.cacheDir.listFiles { f -> f.name.startsWith("custom_crop_") }
			?.filter { it.lastModified() < cutoff }
			?.forEach { it.delete() }
	}

	override fun onDestroyView() {
		super.onDestroyView()
		cropView = null
	}

	companion object {
		private const val ARG_URI = "arg_uri"
		private const val ARG_TAG = "arg_tag"

		/** 背景图业务标记（比例 16:9，与 MineFragment 的 tag 对应） */
		const val TAG_BACKGROUND = "backgroundImage"

		fun newInstance(uri: Uri, tag: String?): ImageCropFragment {
			return ImageCropFragment().apply {
				arguments = Bundle().apply {
					putParcelable(ARG_URI, uri)
					putString(ARG_TAG, tag)
				}
			}
		}
	}
}
