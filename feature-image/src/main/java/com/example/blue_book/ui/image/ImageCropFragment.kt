package com.example.blue_book.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.blue_book.feature_image.R
import java.io.File
import java.io.FileOutputStream

class ImageCropFragment : Fragment() {

	private var uri: Uri? = null
	private var tag: String? = null

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
		val cropView = view.findViewById<CropImageView>(R.id.cropImageView)
		val btnCancel = view.findViewById<View>(R.id.btnCropCancel)
		val btnDone = view.findViewById<View>(R.id.btnCropDone)

		uri?.let { cropView.setImageUri(it) }

		btnCancel.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
		btnDone.setOnClickListener {
			val bitmap = cropView.getCroppedBitmap() ?: return@setOnClickListener
			val file = File(requireContext().cacheDir, "custom_crop_${System.currentTimeMillis()}.jpg")
			FileOutputStream(file).use {
				bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
			}
			val resultUri = Uri.fromFile(file)
			(activity as? ImagePickerActivity)?.finishWithResult(resultUri, tag)
		}
	}

	companion object {
		private const val ARG_URI = "arg_uri"
		private const val ARG_TAG = "arg_tag"

		fun newInstance(uri: Uri, tag: String?): ImageCropFragment {
			val fragment = ImageCropFragment()
			fragment.arguments = Bundle().apply {
				putParcelable(ARG_URI, uri)
				putString(ARG_TAG, tag)
			}
			return fragment
		}
	}
}

class CropImageView(context: Context, attrs: AttributeSet? = null) :
	androidx.appcompat.widget.AppCompatImageView(context, attrs) {

	private var bitmap: Bitmap? = null
	private val imageMatrixValues = FloatArray(9)
	private val drawMatrix = Matrix()
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val cropRect = RectF()

	private var lastX = 0f
	private var lastY = 0f
	private var lastDistance = 0f
	private var isDragging = false
	private var isScaling = false

	init {
		scaleType = ScaleType.MATRIX
		setOnTouchListener { _, event ->
			when (event.action and MotionEvent.ACTION_MASK) {
				MotionEvent.ACTION_DOWN -> {
					lastX = event.x
					lastY = event.y
					isDragging = true
				}
				MotionEvent.ACTION_POINTER_DOWN -> {
					lastDistance = fingerDistance(event)
					if (lastDistance > 10f) isScaling = true
				}
				MotionEvent.ACTION_MOVE -> {
					if (isScaling && event.pointerCount == 2) {
						val newDist = fingerDistance(event)
						if (newDist > 10f && lastDistance > 10f) {
							val scaleFactor = newDist / lastDistance
							val cx = (event.getX(0) + event.getX(1)) / 2f
							val cy = (event.getY(0) + event.getY(1)) / 2f
							drawMatrix.postScale(scaleFactor, scaleFactor, cx, cy)
							imageMatrix = drawMatrix
						}
						lastDistance = newDist
					} else if (isDragging) {
						val dx = event.x - lastX
						val dy = event.y - lastY
						drawMatrix.postTranslate(dx, dy)
						imageMatrix = drawMatrix
						lastX = event.x
						lastY = event.y
					}
				}
				MotionEvent.ACTION_POINTER_UP -> {
					isScaling = false
					if (event.pointerCount == 1) {
						lastX = event.x
						lastY = event.y
						isDragging = true
					}
				}
				MotionEvent.ACTION_UP -> {
					isDragging = false
					isScaling = false
				}
			}
			true
		}
	}

	private fun fingerDistance(event: MotionEvent): Float {
		if (event.pointerCount < 2) return 0f
		val dx = event.getX(0) - event.getX(1)
		val dy = event.getY(0) - event.getY(1)
		return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
	}

	fun setImageUri(uri: Uri) {
		context.contentResolver.openInputStream(uri)?.use { input ->
			bitmap = BitmapFactory.decodeStream(input)
		} ?: return
		requestLayout()
		invalidate()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		updateMatrix()
	}

	private fun updateMatrix() {
		val bmp = bitmap ?: return
		val viewWidth = width.toFloat()
		val viewHeight = height.toFloat()
		val scale = maxOf(viewWidth / bmp.width, viewHeight / bmp.height)
		val dx = (viewWidth - bmp.width * scale) / 2f
		val dy = (viewHeight - bmp.height * scale) / 2f
		drawMatrix.reset()
		drawMatrix.postScale(scale, scale)
		drawMatrix.postTranslate(dx, dy)
		imageMatrix = drawMatrix

		val size = minOf(viewWidth, viewHeight) * 0.7f
		val left = (viewWidth - size) / 2f
		val top = (viewHeight - size) / 2f
		cropRect.set(left, top, left + size, top + size)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		// 这里只画一个简单的方形裁剪框
		paint.style = Paint.Style.STROKE
		paint.color = 0xFFFFFFFF.toInt()
		paint.strokeWidth = 4f
		canvas.drawRect(cropRect, paint)
	}

	fun getCroppedBitmap(): Bitmap? {
		val bmp = bitmap ?: return null
		imageMatrix.getValues(imageMatrixValues)
		val scale = imageMatrixValues[Matrix.MSCALE_X]
		val transX = imageMatrixValues[Matrix.MTRANS_X]
		val transY = imageMatrixValues[Matrix.MTRANS_Y]

		val left = ((cropRect.left - transX) / scale).toInt().coerceIn(0, bmp.width)
		val top = ((cropRect.top - transY) / scale).toInt().coerceIn(0, bmp.height)
		val right = ((cropRect.right - transX) / scale).toInt().coerceIn(0, bmp.width)
		val bottom = ((cropRect.bottom - transY) / scale).toInt().coerceIn(0, bmp.height)

		val width = (right - left).coerceAtLeast(1)
		val height = (bottom - top).coerceAtLeast(1)
		return Bitmap.createBitmap(bmp, left, top, width, height)
	}
}
