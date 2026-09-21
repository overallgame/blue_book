package com.example.blue_book.ui.image

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.example.blue_book.feature_image.R
import com.example.blue_book.router.RoutePath
import com.example.blue_book.router.ExtraKeys
import com.therouter.router.Route

@Route(path = RoutePath.IMAGE_PICKER)
class ImagePickerActivity : AppCompatActivity() {

	private var tag: String? = null

	/** 跳过裁剪：调用方要的是原图本身（如扫码），见 [ExtraKeys.EXTRA_SKIP_CROP] */
	private var skipCrop: Boolean = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_image_picker)

		tag = intent.getStringExtra(EXTRA_TAG)
		skipCrop = intent.getBooleanExtra(ExtraKeys.EXTRA_SKIP_CROP, false)

		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.imagePickerContainer, GalleryFragment.newInstance(tag))
			}
		}
	}

	/**
	 * 选中一张图后的默认去向：**要裁剪就走裁剪页，否则直接回传**。
	 *
	 * 加这一层是因为本选择器原先**无条件**进裁剪页，而"要原图本身"的调用方（扫码）不该被裁剪：
	 * 多一步是摩擦；用户可能裁掉二维码的静默区导致识别不出来；裁剪后的重压缩还会削弱识别率。
	 *
	 * 判断放在 Activity 而不是 GalleryFragment：片段只管列表与拍照，去向由宿主决定。
	 */
	fun openCropOrFinish(uri: Uri, tag: String?) {
		if (skipCrop) finishWithResult(uri, tag) else openCrop(uri, tag)
	}

	fun openCrop(uri: Uri, tag: String?) {
		supportFragmentManager.commit {
			replace(R.id.imagePickerContainer, ImageCropFragment.newInstance(uri, tag))
			addToBackStack(null)
		}
	}

	fun finishWithResult(uri: Uri, tag: String?) {
		val data = Intent().apply {
			this.data = uri
			putExtra(EXTRA_TAG, tag)
		}
		setResult(RESULT_OK, data)
		finish()
	}

	companion object {
		const val EXTRA_TAG = ExtraKeys.EXTRA_IMAGE_TAG
	}
}
