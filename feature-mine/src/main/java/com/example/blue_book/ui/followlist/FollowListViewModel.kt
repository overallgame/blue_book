package com.example.blue_book.ui.followlist

import com.example.blue_book.data.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.udf.UdfViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FollowListViewModel @Inject constructor(
	private val userRepository: UserRepository,
	private val currentUser: CurrentUser
) : UdfViewModel<FollowListIntent, FollowListUiState, FollowListEffect>(FollowListUiState()) {

	/** 列表类型：following=我关注的，followers=我的粉丝 */
	private var followType: String = "following"

	/** 列表归属用户（默认当前登录用户） */
	private var targetUserId: Long = 0L

	private val togglingIds = mutableSetOf<Long>()

	fun bind(followType: String, userId: Long) {
		this.followType = followType
		this.targetUserId = userId
	}

	override suspend fun handleIntent(intent: FollowListIntent) {
		when (intent) {
			FollowListIntent.Init -> initLoad()
			FollowListIntent.Refresh -> refresh()
			FollowListIntent.LoadMore -> loadMore()
			is FollowListIntent.ToggleFollow -> toggleFollow(intent.user)
		}
	}

	private suspend fun fetchPage(cursorId: Long?): Result<com.example.blue_book.domain.model.FollowListPage> {
		val userId = targetUserId.takeIf { it > 0 } ?: (currentUser.userId ?: 0L)
		val pageSize = uiState.value.pageSize
		return if (followType == "followers") {
			userRepository.fetchFollowers(userId, cursorId, pageSize)
		} else {
			userRepository.fetchFollowing(userId, cursorId, pageSize)
		}
	}

	private suspend fun initLoad() {
		setState { copy(items = emptyList(), isLoading = true, message = null, cursorId = null, hasMore = true) }
		fetchPage(null)
			.onSuccess { page ->
				setState {
					copy(items = page.users, isLoading = false, cursorId = page.nextCursorId, hasMore = page.hasMore)
				}
			}
			.onFailure { e ->
				setState { copy(isLoading = false, message = e.message ?: "加载失败") }
			}
	}

	private suspend fun refresh() {
		setState { copy(isLoading = true, message = null) }
		fetchPage(null)
			.onSuccess { page ->
				setState {
					copy(items = page.users, isLoading = false, cursorId = page.nextCursorId, hasMore = page.hasMore)
				}
			}
			.onFailure { e ->
				setState { copy(isLoading = false, message = e.message ?: "加载失败") }
			}
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || state.isLoadingMore || !state.hasMore) return
		setState { copy(isLoadingMore = true) }
		fetchPage(state.cursorId)
			.onSuccess { page ->
				setState {
					copy(
						items = items + page.users,
						isLoadingMore = false,
						cursorId = page.nextCursorId,
						hasMore = page.hasMore
					)
				}
			}
			.onFailure { e ->
				setState { copy(isLoadingMore = false, message = e.message ?: "加载失败") }
			}
	}

	/** 关注/取关：乐观更新 + 失败回滚 */
	private suspend fun toggleFollow(user: UserAccount) {
		if (user.id in togglingIds) return
		togglingIds.add(user.id)
		val newFollowed = !user.isFollowed
		val updated = user.copy(isFollowed = newFollowed)
		updateItemInList(updated)

		val result = if (newFollowed) userRepository.followUser(user.id)
		else userRepository.unfollowUser(user.id)
		result.onFailure { e ->
			updateItemInList(user)
			sendEffect(FollowListEffect.ShowToast(e.message ?: "操作失败"))
		}
		togglingIds.remove(user.id)
	}

	private fun updateItemInList(updated: UserAccount) {
		setState {
			copy(items = items.map { if (it.id == updated.id) updated else it })
		}
	}
}
