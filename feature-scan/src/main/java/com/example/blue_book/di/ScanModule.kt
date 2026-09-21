package com.example.blue_book.di

import com.example.blue_book.ui.scan.BarcodeScanner
import com.example.blue_book.ui.scan.MlKitBarcodeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

	/**
	 * 识别器的绑定。换库（ML Kit → ZXing）只改这里与 `MlKitBarcodeScanner` 一个文件。
	 *
	 * **刻意不加 `@Singleton`**：`BarcodeScanner.close()` 会释放 ML Kit 客户端，
	 * 而 Activity 离开页面时必须调用它（否则识别器一直占着资源）。
	 * 如果绑成单例，第一个 Activity 关闭它之后，第二个 Activity 拿到的是**已关闭的实例** ——
	 * 那种 bug 只在"扫一次 → 返回 → 再扫一次"时才出现，很难查。
	 * 扫码页是低频短生命周期页面，每次新建客户端的成本可以接受。
	 */
	@Binds
	abstract fun bindBarcodeScanner(impl: MlKitBarcodeScanner): BarcodeScanner
}
