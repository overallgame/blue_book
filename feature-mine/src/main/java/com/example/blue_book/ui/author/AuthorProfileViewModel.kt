package com.example.blue_book.ui.author

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.event.VideoInteractionBus
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.udf.UdfViewModel
import com.therouter.TheRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AuthorProfileViewModel @Inject constructor(
	private val userRepository: UserRepository,
	private val currentUser: CurrentUser
) : UdfViewModel<AuthorProfileIntent, AuthorProfileUiState, AuthorProfileEffect>(AuthorProfileUiState()) {

	private val videoProvider: IVideoProvider get() = TheRouter.get(IVideoProvider::class.java)!!

	/** 目标用户 id（进入页面后固定） */
	private var userId: Long = 0L

	fun bindUserId(userId: Long) {
		this.userId = userId
	}

	/** 跨页互动同步：播放页内的点赞/收藏/评论数变化落到本列表 */
	init {
		viewModelScope.launch {
			VideoInteractionBus.patches.collect { patch -> applyInteraction(patch) }
		}
	}

	override suspend fun handleIntent(intent: AuthorProfileIntent) {
		when (intent) {
			AuthorProfileIntent.Init -> initLoad()
			AuthorProfileIntent.Refresh -> refresh()
			AuthorProfileIntent.LoadMore -> loadMore()
			AuthorProfileIntent.ToggleFollow -> toggleFollow()
			is AuthorProfileIntent.ToggleLike -> toggleLike(intent.item)
		}
	}

	private val togglingAids = mutableSetOf<Long>()

	private suspend fun toggleLike(item: VideoCardInfo) {
		// 作者主页对游客开放，点赞需登录
		if (currentUser.userId == null) {
			sendEffect(AuthorProfileEffect.ShowLoginGuide)
			return
		}
		if (item.aid in togglingAids) return
		togglingAids.add(item.aid)
		val newStatus = !item.isLike
		val updated = item.copy(
			isLike = newStatus,
			like = item.like + if (newStatus) 1 else -1
		)
		// 乐观更新
		updateItemInList(updated)
		sendEffect(AuthorProfileEffect.UpdateItem(updated))
		val result = videoProvider.likeVideo(item.aid, newStatus)
		result.onFailure { e ->
			updateItemInList(item)
			sendEffect(AuthorProfileEffect.UpdateItem(item))
			sendEffect(AuthorProfileEffect.ShowToast(e.message ?: "点赞失败"))
		}
		togglingAids.remove(item.aid)
	}

	private suspend fun initLoad() {
		if (userId <= 0L) return
		setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true, profile = null) }
		loadProfile()
		loadVideos(reset = true)
	}

	private suspend fun refresh() {
		if (userId <= 0L) return
		setState { copy(isLoading = true, message = null) }
		loadProfile()
		loadVideos(reset = true)
	}

	private suspend fun loadProfile() {
		userRepository.fetchUserProfile(userId)
			.onSuccess { profile -> setState { copy(profile = profile) } }
			.onFailure { e -> setState { copy(message = e.message ?: "资料加载失败") } }
	}

	private suspend fun loadVideos(reset: Boolean) {
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { videoProvider.fetchUserVideos(userId, cursorId = null, size = uiState.value.pageSize) },
			onSuccess = { list ->
				setState {
					copy(
						items = if (reset) list else items + list,
						isLoading = false,
						cursorId = list.lastOrNull()?.aid,
						hasMore = list.size >= pageSize
					)
				}
			},
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || state.isLoadingMore || !state.hasMore) return
		runResult(
			onStart = { setState { copy(isLoadingMore = true) } },
			call = { videoProvider.fetchUserVideos(userId, cursorId = state.cursorId, size = state.pageSize) },
			onSuccess = { list ->
				setState {
					copy(
						items = items + list,
						isLoadingMore = false,
						cursorId = list.lastOrNull()?.aid,
						hasMore = list.size >= pageSize
					)
				}
			},
			onFailure = { e -> setState { copy(isLoadingMore = false, message = e.message ?: "加载失败") } }
		)
	}

	/** 关注/取关：乐观更新 + 失败回滚（与播放页关注逻辑对齐） */
	private suspend fun toggleFollow() {
		val profile = uiState.value.profile ?: return
		val myId = currentUser.userId
		if (myId == null) {
			sendEffect(AuthorProfileEffect.ShowLoginGuide)
			return
		}
		if (profile.id == myId || uiState.value.isTogglingFollow) return

		setState { copy(isTogglingFollow = true) }
		val newFollowed = !profile.isFollowed
		val updated = profile.copy(
			isFollowed = newFollowed,
			followerCount = (profile.followerCount + if (newFollowed) 1 else -1).coerceAtLeast(0)
		)
		setState { copy(profile = updated) }

		runResult(
			call = {
				if (newFollowed) userRepository.followUser(profile.id)
				else userRepository.unfollowUser(profile.id)
			},
			onSuccess = {
				setState { copy(isTogglingFollow = false) }
				sendEffect(AuthorProfileEffect.ShowToast(if (newFollowed) "已关注 ${profile.nickname ?: "作者"}" else "已取消关注"))
			},
			onFailure = { e ->
				// 失败回滚
				setState { copy(profile = profile, isTogglingFollow = false) }
				sendEffect(AuthorProfileEffect.ShowToast(e.message ?: "操作失败"))
			}
		)
	}

	private fun updateItemInList(updatedVideo: VideoCardInfo) {
		setState {
			val newItems = items.map { if (it.aid == updatedVideo.aid && it.cid == updatedVideo.cid) updatedVideo else it }
			copy(items = newItems)
		}
	}

	/** 播放页互动结果同步：更新 state 与对应卡片 */
	private suspend fun applyInteraction(patch: VideoInteractionBus.Patch) {
		val target = uiState.value.items.firstOrNull { it.aid == patch.aid } ?: return
		val updated = VideoInteractionBus.apply(target, patch) ?: return
		updateItemInList(updated)
		sendEffect(AuthorProfileEffect.UpdateItem(updated))
	}
}
