package com.example.blue_book.udf

import androidx.lifecycle.viewModelScope
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.event.VideoInteractionBus
import kotlinx.coroutines.launch

/**
 * 卡片列表页的状态：`items` 是 `VideoCardInfo` 列表的页面共用（首页三条流、搜索结果、
 * 我的作品/收藏/点赞、作者主页共 8 个）。
 *
 * 自引用泛型（`S : VideoCardListState<S>`）是为了 [withItems] 能返回**具体**状态类型——
 * `setState { copy(...) }` 需要具体类型，而接口方法无法返回它。
 */
interface VideoCardListState<S : VideoCardListState<S>> : UiState {

	val items: List<VideoCardInfo>

	fun withItems(items: List<VideoCardInfo>): S
}

/**
 * 持有 `VideoCardInfo` 列表的 ViewModel 基类，收敛这 8 个页面此前**逐字相同**的那套逻辑：
 *
 * - 订阅 [VideoInteractionBus]，把播放页的点赞/收藏/评论数变化落到本列表
 * - 卡片定位与替换的判据（aid + cid）
 * - 状态未变化时不刷新、也不发 effect
 *
 * 此前这 8 个 ViewModel 各写一份 `init { collect }` + `applyInteraction` + `updateItemInList`，
 * 广播语义一改就要动 8 个文件——这是典型的「改动成本放大器」。
 *
 * 唯一无法上提的是各页面自己的 `UpdateItem` effect 类型，所以子类只实现
 * [onInteractionSynced]（一行 `sendEffect(...)`）。
 */
abstract class VideoCardListViewModel<I : UiIntent, S : VideoCardListState<S>, E : UiEffect>(
	initialState: S,
	private val interactionBus: VideoInteractionBus
) : UdfViewModel<I, S, E>(initialState) {

	init {
		viewModelScope.launch {
			interactionBus.patches.collect { patch ->
				syncInteractionPatch(patch)?.let { onInteractionSynced(it) }
			}
		}
	}

	/**
	 * 卡片变更已落到 state，子类在此发出自己的 `UpdateItem` effect 去刷新对应条目。
	 * 只有列表里确实有这张卡、且状态真的变了才回调。
	 */
	protected abstract suspend fun onInteractionSynced(updated: VideoCardInfo)

	/**
	 * 按 aid + cid 定位并替换卡片。
	 * 同一 aid 但 cid 不同视为不同卡片（同一作品的多个清晰度条目），故两个都要比。
	 */
	protected fun replaceCard(updated: VideoCardInfo) {
		setState {
			withItems(items.map { if (it.aid == updated.aid && it.cid == updated.cid) updated else it })
		}
	}

	/**
	 * 把播放页的互动广播落到本列表，返回更新后的卡片供 [onInteractionSynced] 使用。
	 *
	 * 返回 null 表示**列表里没有这张卡**，或**状态本就没变**（例如已点赞又收到一次点赞），
	 * 两种情况都不该刷新列表、也不该发 effect。
	 */
	private fun syncInteractionPatch(patch: VideoInteractionBus.Patch): VideoCardInfo? {
		val target = uiState.value.items.firstOrNull { it.aid == patch.aid } ?: return null
		val updated = VideoInteractionBus.apply(target, patch) ?: return null
		replaceCard(updated)
		return updated
	}
}
