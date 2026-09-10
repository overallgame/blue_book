package com.example.blue_book.ui.image

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.feature_image.R
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 相册选择页：网格选图 → 进入裁剪；支持拍照入口、权限引导、空态与加载中 */
class GalleryFragment : Fragment() {

	private var tag: String? = null
	private lateinit var permissionLauncher: ActivityResultLauncher<String>
	private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
	private lateinit var recyclerView: RecyclerView
	private lateinit var galleryAdapter: GalleryAdapter
	private var pendingCameraUri: Uri? = null

	private var loadingView: View? = null
	private var emptyView: View? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		tag = arguments?.getString(ARG_TAG)
		initPermissionLauncher()
		initCameraLauncher()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return inflater.inflate(R.layout.fragment_gallery, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		recyclerView = view.findViewById(R.id.galleryRecyclerView)
		loadingView = view.findViewById(R.id.gallery_loading)
		emptyView = view.findViewById(R.id.gallery_empty)
		recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
		galleryAdapter = GalleryAdapter { uri ->
			(activity as? ImagePickerActivity)?.openCrop(uri, tag)
		}
		recyclerView.adapter = galleryAdapter

		view.findViewById<MaterialToolbar>(R.id.gallery_toolbar).setNavigationOnClickListener { back() }
		view.findViewById<View>(R.id.gallery_take_photo).setOnClickListener { takePhoto() }
		view.findViewById<View>(R.id.gallery_empty_action).setOnClickListener { openAppSettings() }

		checkPermissionAndLoad()
	}

	override fun onResume() {
		super.onResume()
		// 从系统设置授权返回后自动刷新
		if (isPermissionGranted() && galleryAdapter.itemCount == 0) {
			checkPermissionAndLoad()
		}
	}

	private fun back() {
		requireActivity().supportFragmentManager.popBackStack()
	}

	// ==================== 权限 ====================

	private fun currentPermission(): String =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			Manifest.permission.READ_MEDIA_IMAGES
		} else {
			Manifest.permission.READ_EXTERNAL_STORAGE
		}

	private fun isPermissionGranted(): Boolean =
		ContextCompat.checkSelfPermission(requireContext(), currentPermission()) ==
				PackageManager.PERMISSION_GRANTED

	private fun initPermissionLauncher() {
		permissionLauncher = registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { granted ->
			if (granted) {
				loadImagesAsync()
			} else {
				showPermissionDenied()
			}
		}
	}

	private fun checkPermissionAndLoad() {
		if (isPermissionGranted()) {
			loadImagesAsync()
		} else {
			permissionLauncher.launch(currentPermission())
		}
	}

	/** 权限被拒：空态提示 + 引导去系统设置授权 */
	private fun showPermissionDenied() {
		if (!isAdded) return
		showEmpty(getString(R.string.gallery_permission_denied), showSettingsAction = true)
		Toast.makeText(requireContext(), R.string.gallery_permission_denied, Toast.LENGTH_LONG).show()
	}

	private fun openAppSettings() {
		startActivity(
			Intent(
				Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
				Uri.fromParts("package", requireContext().packageName, null)
			)
		)
	}

	// ==================== 加载相册 ====================

	private fun loadImagesAsync() {
		showLoading()
		viewLifecycleOwner.lifecycleScope.launch {
			val images = withContext(Dispatchers.IO) { queryImages() }
			if (!isAdded) return@launch
			galleryAdapter.submitList(images)
			if (images.isEmpty()) {
				showEmpty(getString(R.string.gallery_empty), showSettingsAction = false)
			} else {
				showContent()
			}
		}
	}

	/** 查询最近 500 张图片（避免超大相册全量遍历导致首屏卡顿） */
	private fun queryImages(): List<Uri> {
		val uris = mutableListOf<Uri>()
		val projection = arrayOf(MediaStore.Images.Media._ID)
		val queryArgs = Bundle().apply {
			putString(
				ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
				"${MediaStore.Images.Media.DATE_ADDED} DESC"
			)
			putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_QUERY_COUNT)
		}
		requireContext().contentResolver.query(
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
			projection,
			queryArgs,
			null
		)?.use { cursor ->
			val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
			while (cursor.moveToNext()) {
				uris.add(
					ContentUris.withAppendedId(
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						cursor.getLong(idColumn)
					)
				)
			}
		}
		return uris
	}

	// ==================== 拍照 ====================

	private fun initCameraLauncher() {
		cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
			val uri = pendingCameraUri
			pendingCameraUri = null
			if (success && uri != null) {
				(activity as? ImagePickerActivity)?.openCrop(uri, tag)
			}
		}
	}

	private fun takePhoto() {
		try {
			val dir = File(requireContext().cacheDir, "camera").apply { mkdirs() }
			val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
			val uri = FileProvider.getUriForFile(
				requireContext(),
				"${requireContext().packageName}.fileprovider",
				file
			)
			pendingCameraUri = uri
			cameraLauncher.launch(uri)
		} catch (t: Throwable) {
			Toast.makeText(requireContext(), "无法启动相机", Toast.LENGTH_SHORT).show()
		}
	}

	// ==================== 视图状态 ====================

	private fun showLoading() {
		loadingView?.visibility = View.VISIBLE
		emptyView?.visibility = View.GONE
	}

	private fun showContent() {
		loadingView?.visibility = View.GONE
		emptyView?.visibility = View.GONE
	}

	private fun showEmpty(text: String, showSettingsAction: Boolean) {
		loadingView?.visibility = View.GONE
		emptyView?.visibility = View.VISIBLE
		view?.findViewById<TextView>(R.id.gallery_empty_text)?.text = text
		view?.findViewById<View>(R.id.gallery_empty_action)?.visibility =
			if (showSettingsAction) View.VISIBLE else View.GONE
	}

	// ==================== 适配器 ====================

	class GalleryAdapter(
		private val onClick: (Uri) -> Unit
	) : ListAdapter<Uri, GalleryAdapter.VH>(UriDiffCallback()) {

		inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
			val imageView: androidx.appcompat.widget.AppCompatImageView =
				itemView.findViewById(R.id.itemImage)
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
			val v = LayoutInflater.from(parent.context)
				.inflate(R.layout.item_gallery_image, parent, false)
			return VH(v)
		}

		override fun onBindViewHolder(holder: VH, position: Int) {
			val uri = getItem(position)
			Glide.with(holder.imageView.context)
				.load(uri)
				.centerCrop()
				.into(holder.imageView)
			holder.itemView.setOnClickListener { onClick(uri) }
		}
	}

	private class UriDiffCallback : DiffUtil.ItemCallback<Uri>() {
		override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
		override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
	}

	companion object {
		private const val ARG_TAG = "arg_tag"
		private const val MAX_QUERY_COUNT = 500

		fun newInstance(tag: String?): GalleryFragment {
			val fragment = GalleryFragment()
			fragment.arguments = Bundle().apply {
				putString(ARG_TAG, tag)
			}
			return fragment
		}
	}
}
