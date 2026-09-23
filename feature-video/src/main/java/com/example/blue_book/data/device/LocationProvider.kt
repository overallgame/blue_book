package com.example.blue_book.data.device

import android.content.Context
import com.example.blue_book.util.LocationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 取"当前所在城市"（发布视频时带上，用于「本地」流）。
 *
 * ## 为什么要抽这层
 *
 * 它此前是 `PublishViewModel` 构造签名里的 `@ApplicationContext Context` 的直接用途，
 * 而那正是 `tools/check_viewmodel_layer.py` 登记在案的欠债：**带着 Context 的 ViewModel
 * 在纯 JVM 上构造不出来，于是"取消上传""进度不倒退""publish 只调一次"这些编排逻辑全都测不了**。
 * 把平台能力收进接口之后，ViewModel 只依赖 [LocationProvider]，
 * 测试里给一个假的即可。
 *
 * 与 `ChunkUploader`/`VideoPublisher` 同一套做法：**窄接口 + 平台实现**，
 * 消费方（ViewModel）只看到它需要的那一个方法。
 */
interface LocationProvider {

	/** 定位失败/未授权返回 null——发布不该因为定位不上而失败 */
	suspend fun currentCity(): String?
}

/**
 * 真实实现：转发到 [LocationHelper]。
 *
 * 定位是"能拿到就带、拿不到就算了"的信息，所以这里也照旧吞掉异常：
 * 它只影响视频进不进「本地」流，不该阻塞发布。
 */
class AndroidLocationProvider @Inject constructor(
	@ApplicationContext private val appContext: Context
) : LocationProvider {

	override suspend fun currentCity(): String? = withContext(Dispatchers.IO) {
		runCatching { LocationHelper.currentCity(appContext) }.getOrNull()
	}
}
