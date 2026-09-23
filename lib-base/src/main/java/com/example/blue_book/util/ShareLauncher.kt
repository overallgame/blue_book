package com.example.blue_book.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 用系统分享面板发出**纯文本**。
 *
 * 抽到 lib-base 是因为多个模块都要分享（`feature-video` 的播放页、`feature-mine` 的我的页），
 * 各自写一份会让兜底行为漂移——一边改了提示文案、另一边忘了 catch，
 * 表现是"某些设备上点分享直接崩"。与 `openVideoPlayer`（路由）同理。
 *
 * 它**只负责发出去**：分享什么内容由调用方决定（站内码的分享文案见
 * [com.example.blue_book.scan.ShareCode]），所以这个函数与扫码无关。
 */
fun sharePlainText(context: Context, text: String, chooserTitle: String = "分享到") {
	val send = Intent(Intent.ACTION_SEND).apply {
		type = "text/plain"
		putExtra(Intent.EXTRA_TEXT, text)
	}
	val chooser = Intent.createChooser(send, chooserTitle)
	// 非 Activity 的 Context 起 Activity 必须带这个 flag，否则抛
	// "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag"。
	// 当前两个调用点传的都是 Fragment.requireContext()（即 Activity），
	// 但这是个会被复用的公共函数，一行判断换掉一个将来才会暴露的崩溃，值得。
	if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	try {
		context.startActivity(chooser)
	} catch (_: ActivityNotFoundException) {
		// 分享是"顺手做的事"：设备上确实可能没有任何能接收 text/plain 的应用
		// （精简 ROM / 纯工作设备）。这不该让页面崩掉。
		Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
	}
}
