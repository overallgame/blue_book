package com.example.blue_book.view

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.example.blue_book.R
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter

/**
 * 未登录引导卡片：圆角卡片 + "去登录"/"暂不"。
 * 用于首页进入与底部导航拦截未登录用户使用其他页面功能。
 */
object LoginGuideDialog {

	fun show(activity: Activity) {
		if (activity.isFinishing || activity.isDestroyed) return
		val dialog = Dialog(activity)
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
		dialog.setContentView(R.layout.dialog_login_guide)
		dialog.setCancelable(true)
		dialog.window?.apply {
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
			setDimAmount(0.5f)
			setLayout(
				(activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
				WindowManager.LayoutParams.WRAP_CONTENT
			)
		}
		dialog.findViewById<View>(R.id.login_guide_confirm).setOnClickListener {
			dialog.dismiss()
			TheRouter.build(RoutePath.AUTH).navigation(activity)
		}
		dialog.findViewById<View>(R.id.login_guide_dismiss).setOnClickListener {
			dialog.dismiss()
		}
		dialog.show()
	}
}
