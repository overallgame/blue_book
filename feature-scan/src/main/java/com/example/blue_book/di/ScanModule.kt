package com.example.blue_book.di

import com.example.blue_book.data.repository.ScanRepositoryImpl
import com.example.blue_book.domain.repository.ScanRepository
import com.example.blue_book.ui.scan.BarcodeScanner
import com.example.blue_book.ui.scan.MlKitBarcodeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

	/**
	 * 识别器的绑定。换库（ML Kit → ZXing）只改这里与 `MlKitBarcodeScanner` 一个文件。
	 *
	 * **刻意不加 `@Singleton`**：`BarcodeScanner.close()` 会释放 ML Kit 客户端，
	 * 而 Activity 离开页面时必须调用它（否则识别器一直占着资源）；
	 * 绑成单例的话，第一个 Activity 关闭之后，第二个 Activity 拿到的是**已关闭的实例**。
	 * 扫码页是低频短生命周期页面，每次新建客户端的成本可以接受。
	 */
	@Binds
	abstract fun bindBarcodeScanner(impl: MlKitBarcodeScanner): BarcodeScanner

	/**
	 * 仓库绑定。加 `@Singleton` 的依据与上面识别器相反，判据是
	 * "**这个实例自己持有需要释放的资源吗**"：识别器持有 ML Kit 客户端且必须能被关闭；
	 * 仓库只持有一个无状态的 `ScanRemoteDataSource`，共享它没有副作用。
	 */
	@Binds
	@Singleton
	abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository
}
