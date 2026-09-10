package com.example.blue_book.widget

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.example.blue_book.lib_base.R
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter

/**
 * 未登录引导卡片（公共组件）：圆角卡片 + "去登录"/"暂不"。
 * - [showIfNeeded]：进入 App 首页时引导，进程内仅一次
 * - [show]：底部导航拦截等场景，每次调用都弹
 */
object LoginGuideDialog {

	private var shownInProcess = false

	/** 进程内仅弹一次（进入 App 的首页引导） */
	fun showIfNeeded(activity: Activity) {
		if (shownInProcess) return
		shownInProcess = true
		show(activity)
	}

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
