package com.example.blue_book.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

/**
 * 裁剪控件（小红书风格）：
 * - 采样解码（防 OOM）+ EXIF 方向校正（竖拍照片不会躺倒）
 * - 暗化遮罩 + 九宫格 + 白色裁剪框
 * - 边界约束：图片始终覆盖裁剪框，不可拖出/缩小于框
 * - 比例由外部指定（头像 1:1 / 背景 16:9），输出长边限制 + 支持旋转
 *
 * 源图由调用方在后台线程解码后经 [setBitmap] 传入；本类不读磁盘。
 */
class CropImageView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

	private var bitmap: Bitmap? = null

	/** 裁剪框宽高比（宽/高） */
	private var aspectRatio = 1f

	/** 图片覆盖裁剪框的最小缩放（由初始矩阵确定） */
	private var baseScale = 1f

	private val drawMatrix = Matrix()
	private val matrixValues = FloatArray(9)
	private val cropRect = RectF()
	private val overlayPath = Path()

	private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
	private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		color = 0xFFFFFFFF.toInt()
		strokeWidth = 2f * context.resources.displayMetrics.density
	}
	private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		color = 0x4DFFFFFF
		strokeWidth = 1f * context.resources.displayMetrics.density
	}

	private var lastX = 0f
	private var lastY = 0f
	private var lastDistance = 0f
	private var isDragging = false
	private var isScaling = false

	init {
		scaleType = ScaleType.MATRIX
		setOnTouchListener { _, event -> handleTouch(event) }
	}

	/** 设置裁剪比例（在 setBitmap 前后皆可） */
	fun setAspectRatio(ratio: Float) {
		if (ratio <= 0f || ratio == aspectRatio) return
		aspectRatio = ratio
		if (width > 0 && bitmap != null) {
			updateCropRect()
			applyInitialMatrix()
			invalidate()
		}
	}

	/**
	 * 设置源图（主线程调用）。替换时回收旧位图——连续旋转会不断产生全尺寸副本
	 * （2560 长边下每张约 26MB），不回收会在低端机上累积到 OOM。
	 * 解码本身由调用方放在后台线程完成（见 decodeSampledForCrop）。
	 */
	fun setBitmap(newBitmap: Bitmap?) {
		val old = bitmap
		bitmap = newBitmap
		if (old != null && old !== newBitmap && !old.isRecycled) old.recycle()
		if (newBitmap != null && width > 0) {
			updateCropRect()
			applyInitialMatrix()
		}
		invalidate()
	}

	/** 旋转 90°（重建位图与矩阵，旧位图由 setBitmap 回收） */
	fun rotate90() {
		val bmp = bitmap ?: return
		if (bmp.isRecycled) return
		val matrix = Matrix().apply { postRotate(90f) }
		setBitmap(Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true))
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		if (bitmap != null) {
			updateCropRect()
			applyInitialMatrix()
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (bitmap == null) return

		// 暗化遮罩：全屏填充后挖出裁剪框（反选路径）
		overlayPath.reset()
		overlayPath.fillType = Path.FillType.INVERSE_EVEN_ODD
		overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
		overlayPath.addRect(cropRect, Path.Direction.CW)
		canvas.drawPath(overlayPath, maskPaint)

		// 九宫格辅助线
		for (i in 1..2) {
			val x = cropRect.left + cropRect.width() * i / 3f
			canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint)
			val y = cropRect.top + cropRect.height() * i / 3f
			canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint)
		}

		// 白色裁剪框
		canvas.drawRect(cropRect, framePaint)
	}

	// ==================== 内部实现 ====================

	private fun handleTouch(event: MotionEvent): Boolean {
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
				if (isScaling && event.pointerCount >= 2) {
					val newDistance = fingerDistance(event)
					if (newDistance > 10f && lastDistance > 10f) {
						val factor = newDistance / lastDistance
						val cx = (event.getX(0) + event.getX(1)) / 2f
						val cy = (event.getY(0) + event.getY(1)) / 2f
						drawMatrix.postScale(factor, factor, cx, cy)
						imageMatrix = drawMatrix
						constrain()
					}
					lastDistance = newDistance
				} else if (isDragging) {
					drawMatrix.postTranslate(event.x - lastX, event.y - lastY)
					imageMatrix = drawMatrix
					constrain()
					lastX = event.x
					lastY = event.y
				}
			}

			MotionEvent.ACTION_POINTER_UP -> {
				isScaling = false
				// 抬起一根手指后，剩下那根继续拖动：以剩余手指为基准重置起点，
				// 否则沿用双指前的 lastX/lastY 会造成图片瞬间跳变
				if (event.pointerCount - 1 == 1) {
					val remainIndex = if (event.actionIndex == 0) 1 else 0
					lastX = event.getX(remainIndex)
					lastY = event.getY(remainIndex)
					isDragging = true
				}
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				isDragging = false
				isScaling = false
			}
		}
		return true
	}

	private fun fingerDistance(event: MotionEvent): Float {
		if (event.pointerCount < 2) return 0f
		val dx = event.getX(0) - event.getX(1)
		val dy = event.getY(0) - event.getY(1)
		return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
	}

	/** 裁剪框：按比例在视图内尽可能大（留 10% 边距），居中 */
	private fun updateCropRect() {
		val w = width.toFloat()
		val h = height.toFloat()
		if (w <= 0f || h <= 0f) return
		val maxW = w * 0.9f
		val maxH = h * 0.9f
		var cw = maxW
		var ch = cw / aspectRatio
		if (ch > maxH) {
			ch = maxH
			cw = ch * aspectRatio
		}
		val left = (w - cw) / 2f
		val top = (h - ch) / 2f
		cropRect.set(left, top, left + cw, top + ch)
	}

	/** 初始矩阵：cover 裁剪框并居中 */
	private fun applyInitialMatrix() {
		val bmp = bitmap ?: return
		if (width <= 0 || cropRect.isEmpty) return
		val scale = maxOf(cropRect.width() / bmp.width, cropRect.height() / bmp.height)
		baseScale = scale
		drawMatrix.reset()
		drawMatrix.postScale(scale, scale)
		drawMatrix.postTranslate(
			cropRect.centerX() - bmp.width * scale / 2f,
			cropRect.centerY() - bmp.height * scale / 2f
		)
		imageMatrix = drawMatrix
		constrain()
	}

	/** 边界约束：缩放下限 baseScale；平移保证图片覆盖裁剪框 */
	private fun constrain() {
		val bmp = bitmap ?: return
		drawMatrix.getValues(matrixValues)
		var scale = matrixValues[Matrix.MSCALE_X]

		if (scale < baseScale) {
			val k = baseScale / scale
			drawMatrix.postScale(k, k, cropRect.centerX(), cropRect.centerY())
			imageMatrix = drawMatrix
			drawMatrix.getValues(matrixValues)
			scale = baseScale
		}

		val dispW = bmp.width * scale
		val dispH = bmp.height * scale
		val tx = matrixValues[Matrix.MTRANS_X].coerceAtMost(cropRect.left)
			.coerceAtLeast(cropRect.right - dispW)
		val ty = matrixValues[Matrix.MTRANS_Y].coerceAtMost(cropRect.top)
			.coerceAtLeast(cropRect.bottom - dispH)
		val dx = tx - matrixValues[Matrix.MTRANS_X]
		val dy = ty - matrixValues[Matrix.MTRANS_Y]
		if (dx != 0f || dy != 0f) {
			drawMatrix.postTranslate(dx.toFloat(), dy.toFloat())
			imageMatrix = drawMatrix
		}
	}

	/** 采样解码与 EXIF 校正已移至 ImageDecoder.kt（可在后台线程调用） */

	/** 输出裁剪位图：按矩阵逆映射裁剪框，长边限制 1440px */
	fun getCroppedBitmap(): Bitmap? {
		val bmp = bitmap ?: return null
		drawMatrix.getValues(matrixValues)
		val scale = matrixValues[Matrix.MSCALE_X]
		if (scale <= 0f) return null
		val transX = matrixValues[Matrix.MTRANS_X]
		val transY = matrixValues[Matrix.MTRANS_Y]

		val left = ((cropRect.left - transX) / scale).toInt().coerceIn(0, bmp.width - 1)
		val top = ((cropRect.top - transY) / scale).toInt().coerceIn(0, bmp.height - 1)
		val right = ((cropRect.right - transX) / scale).toInt().coerceIn(left + 1, bmp.width)
		val bottom = ((cropRect.bottom - transY) / scale).toInt().coerceIn(top + 1, bmp.height)

		val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
		val longSide = maxOf(cropped.width, cropped.height)
		return if (longSide > MAX_OUTPUT_LONG_SIDE) {
			val factor = MAX_OUTPUT_LONG_SIDE.toFloat() / longSide
			Bitmap.createScaledBitmap(
				cropped,
				(cropped.width * factor).toInt().coerceAtLeast(1),
				(cropped.height * factor).toInt().coerceAtLeast(1),
				true
			)
		} else {
			cropped
		}
	}

	companion object {
		/** 输出长边上限（控制上传体积） */
		private const val MAX_OUTPUT_LONG_SIDE = 1440

		/** 裁剪结果 JPEG 质量 */
		const val OUTPUT_QUALITY = 85
	}
}
