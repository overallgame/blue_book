package com.example.blue_book.ui.image

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.feature_image.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 裁剪页（小红书风格）：黑底 + 暗化遮罩 + 九宫格 + 顶部关闭/完成 + 底部旋转。
 * 裁剪比例按业务 tag 决定：头像 1:1，背景图 16:9。
 *
 * 解码与编码都在后台线程完成，主线程只做矩阵运算与视图更新。
 */
class ImageCropFragment : Fragment() {

	private var uri: Uri? = null
	private var tag: String? = null
	private var cropView: CropImageView? = null

	/** 正在保存：期间禁止旋转/重复保存 */
	private var isSaving = false
	private var doneButton: View? = null
	private var rotateButton: View? = null

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
		loadImage(crop)

		view.findViewById<View>(R.id.crop_close).setOnClickListener {
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
		rotateButton = view.findViewById<View>(R.id.crop_rotate).apply {
			setOnClickListener { crop.rotate90() }
		}
		doneButton = view.findViewById<View>(R.id.crop_done).apply {
			setOnClickListener { saveAndFinish() }
		}

		initWindowInsets(view)
	}

	/**
	 * 解码放后台：采样解码 + EXIF 读取 + 二次解码在大图上是数百毫秒级的主线程阻塞
	 * （此前是在 onViewCreated 里同步解码 12MP 照片）。
	 */
	private fun loadImage(crop: CropImageView) {
		val source = uri ?: return
		viewLifecycleOwner.lifecycleScope.launch {
			val decoded = withContext(Dispatchers.IO) {
				runCatching { decodeSampledForCrop(requireContext(), source) }.getOrNull()
			}
			if (decoded == null) {
				Toast.makeText(requireContext(), "无法读取图片，请重新选择", Toast.LENGTH_SHORT).show()
				return@launch
			}
			crop.setBitmap(decoded)
		}
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
		if (isSaving) return
		val crop = cropView ?: return
		// 裁剪本身（读 drawMatrix/cropRect）留在主线程，编码 + 写盘移到 IO：
		// 1440px 位图的 JPEG 压缩是数百毫秒级的主线程阻塞
		val bitmap = crop.getCroppedBitmap() ?: return
		setSaving(true)
		viewLifecycleOwner.lifecycleScope.launch {
			val saved = withContext(Dispatchers.IO) {
				runCatching {
					val context = requireContext()
					val file = File(context.cacheDir, "custom_crop_${System.currentTimeMillis()}.jpg")
					FileOutputStream(file).use { out ->
						bitmap.compress(Bitmap.CompressFormat.JPEG, CropImageView.OUTPUT_QUALITY, out)
					}
					file
				}
			}
			setSaving(false)
			saved
				.onSuccess { file -> (activity as? ImagePickerActivity)?.finishWithResult(Uri.fromFile(file), tag) }
				.onFailure { Toast.makeText(requireContext(), "保存失败，请重试", Toast.LENGTH_SHORT).show() }
		}
	}

	/**
	 * 保存期间禁用旋转与完成：旋转会替换并回收源位图，而待保存的裁剪结果
	 * 在某些情况下可能是与源位图同一实例，回收后再压缩会崩溃。
	 */
	private fun setSaving(saving: Boolean) {
		isSaving = saving
		doneButton?.isEnabled = !saving
		rotateButton?.isEnabled = !saving
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
