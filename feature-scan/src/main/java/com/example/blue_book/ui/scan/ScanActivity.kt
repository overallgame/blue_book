package com.example.blue_book.ui.scan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.blue_book.feature_scan.databinding.ActivityScanBinding
import com.example.blue_book.router.RoutePath
import com.therouter.router.Route

/**
 * 扫一扫（独立 Activity，压在 MainActivity 之上，返回即回到点击它的页面）。
 *
 * 第 1 步只打通「入口 → 页面 → 返回」这条链路，页面内容是一个占位。
 * 后续按计划补：相机预览与权限（第 3 步）、校验与跳转（第 4~5 步）、相册识别（第 6 步）。
 *
 * 注意本页**不判登录**：入口 `mine_scan` 已经套了 `guardLogin`，那是既定的约束位置。
 */
@Route(path = RoutePath.SCAN)
class ScanActivity : AppCompatActivity() {

	private lateinit var binding: ActivityScanBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityScanBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.scanToolbar.setNavigationOnClickListener { finish() }
	}
}
