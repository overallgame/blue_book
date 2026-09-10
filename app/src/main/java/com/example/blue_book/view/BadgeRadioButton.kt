package com.example.blue_book.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import kotlin.math.max

/**
 * 带未读角标的底部导航按钮：未读数 > 0 时在文字右上角绘制红色数字角标。
 * 继承 RadioButton，可直接作为 RadioGroup 直接子项使用。
 */
class BadgeRadioButton @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : AppCompatRadioButton(context, attrs) {

	private var unreadCount = 0

	private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = 0xFFFF3B30.toInt()
	}
	private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		textSize = dp(10f)
		typeface = Typeface.DEFAULT_BOLD
	}

	fun setUnreadCount(count: Int) {
		val normalized = count.coerceAtLeast(0)
		if (normalized == unreadCount) return
		unreadCount = normalized
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (unreadCount <= 0) return

		val label = if (unreadCount > 99) "99+" else unreadCount.toString()
		val textWidth = badgeTextPaint.measureText(label)
		val radius = max(dp(7f), textWidth / 2f + dp(4f))

		// 贴文字右上角（文字居中于按钮）
		val labelWidth = paint.measureText(text?.toString().orEmpty())
		val cx = width / 2f + labelWidth / 2f - dp(4f) + radius / 2f
		val cy = height / 2f - textSize / 2f - radius / 1.4f

		canvas.drawCircle(cx, cy, radius, badgePaint)
		val baseline = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
		canvas.drawText(label, cx, baseline, badgeTextPaint)
	}

	private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
