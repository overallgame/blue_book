package com.example.blue_book.event

import com.example.blue_book.data.VideoCardInfo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频互动状态广播：播放页里发生的点赞/收藏/评论数变化，需要同步回各列表页
 * （首页三条流、搜索结果、我的作品/赞过/收藏、作者主页），
 * 否则从播放页返回后卡片上的爱心/计数仍是旧值。
 *
 * 发布方：IVideoProvider 实现（所有模块的点赞/收藏都经它，天然是唯一出口）
 *         + 播放页的评论数增量；
 * 订阅方：持有卡片列表的 ViewModel（init 中订阅并落到自身 state，经既有的
 *         UpdateItem effect 局部刷新对应卡片）。订阅侧的公共部分在 `VideoCardListViewModel`。
 *
 * **为什么是 `@Singleton` 类而不是 `object`**：原先它是全局 object，订阅方与发布方通过
 * 静态成员隐式耦合——依赖不在构造函数上、读代码看不出来，而且全局单例状态在测试里无法隔离。
 * 改为 `@Inject constructor()` 后依赖在构造参数上可见，也可由测试用 `@TestInstallIn` 换掉。
 * 纯变换逻辑留在 companion：它无状态、不需要实例，这样订阅侧的基类不必为了算一个卡片
 * 就依赖整个 bus。
 */
@Singleton
class VideoInteractionBus @Inject constructor() {

	sealed interface Patch {
		val aid: Long

		data class Like(override val aid: Long, val isLike: Boolean) : Patch
		data class Collect(override val aid: Long, val isCollect: Boolean) : Patch
		data class CommentCount(override val aid: Long, val commentCount: Int) : Patch
	}

	private val _patches = MutableSharedFlow<Patch>(
		extraBufferCapacity = 64,
		onBufferOverflow = BufferOverflow.DROP_OLDEST
	)

	val patches: SharedFlow<Patch> = _patches.asSharedFlow()

	fun publish(patch: Patch) {
		_patches.tryEmit(patch)
	}

	fun publishLike(aid: Long, isLike: Boolean) = publish(Patch.Like(aid, isLike))

	fun publishCollect(aid: Long, isCollect: Boolean) = publish(Patch.Collect(aid, isCollect))

	fun publishCommentCount(aid: Long, commentCount: Int) =
		publish(Patch.CommentCount(aid, commentCount))

	companion object {

		/**
		 * 把广播变更应用到卡片；状态未变化时返回 null（避免无意义的列表刷新）。
		 * 计数按卡片当前值 ±1，与各页面乐观更新的口径一致。
		 *
		 * 纯函数、无状态，故放在 companion 而非实例方法。
		 */
		fun apply(item: VideoCardInfo, patch: Patch): VideoCardInfo? = when (patch) {
			is Patch.Like -> if (item.isLike == patch.isLike) {
				null
			} else {
				item.copy(
					isLike = patch.isLike,
					like = (item.like + if (patch.isLike) 1 else -1).coerceAtLeast(0)
				)
			}

			is Patch.Collect -> if (item.isCollect == patch.isCollect) {
				null
			} else {
				item.copy(
					isCollect = patch.isCollect,
					collection = (item.collection + if (patch.isCollect) 1 else -1).coerceAtLeast(0)
				)
			}

			is Patch.CommentCount -> if (item.commentCount == patch.commentCount) {
				null
			} else {
				item.copy(commentCount = patch.commentCount)
			}
		}
	}
}
