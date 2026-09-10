package com.example.blue_book.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blue_book.domain.model.Comment
import com.example.blue_book.domain.usecase.DeleteCommentUseCase
import com.example.blue_book.domain.usecase.FetchCommentsUseCase
import com.example.blue_book.domain.usecase.FetchRepliesUseCase
import com.example.blue_book.domain.usecase.LikeCommentUseCase
import com.example.blue_book.domain.usecase.PostCommentUseCase
import com.example.blue_book.domain.usecase.ReplyCommentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentUiState(
	val comments: List<Comment> = emptyList(),
	val isLoading: Boolean = false,
	val isLoadingMore: Boolean = false,
	val error: String? = null,
	val replyToComment: Comment? = null,
	val isPosting: Boolean = false,
	val postSuccess: Boolean = false,
	/** 单条删除成功（弹层据此扣减回传给播放页的评论数增量，失败不扣） */
	val deleteSuccess: Boolean = false,
	val cursorId: Long? = null,
	val hasMore: Boolean = true
)

@HiltViewModel
class CommentViewModel @Inject constructor(
	private val fetchCommentsUseCase: FetchCommentsUseCase,
	private val fetchRepliesUseCase: FetchRepliesUseCase,
	private val postCommentUseCase: PostCommentUseCase,
	private val replyCommentUseCase: ReplyCommentUseCase,
	private val likeCommentUseCase: LikeCommentUseCase,
	private val deleteCommentUseCase: DeleteCommentUseCase
) : ViewModel() {

	private val _uiState = MutableStateFlow(CommentUiState())
	val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

	private var currentVideoId: Long = 0

	/** 正在展开回复的根评论 id，防止连点重复拉取 */
	private val loadingRepliesIds = mutableSetOf<Long>()

	/** 最多连续抓取的回复页数（每页 20 条），避免异常数据导致死循环 */
	private companion object {
		const val MAX_REPLY_PAGES = 10
	}

	fun loadComments(videoId: Long, refresh: Boolean = false) {
		if (!refresh && videoId == currentVideoId && _uiState.value.comments.isNotEmpty()) return
		currentVideoId = videoId
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true, error = null, cursorId = null, hasMore = true) }
			fetchCommentsUseCase(videoId)
				.onSuccess { page ->
					_uiState.update {
						it.copy(
							comments = page.comments,
							isLoading = false,
							cursorId = page.cursorId,
							hasMore = page.hasMore
						)
					}
				}
				.onFailure { e ->
					_uiState.update { it.copy(error = e.message, isLoading = false) }
				}
		}
	}

	fun loadMore() {
		val state = _uiState.value
		if (state.isLoadingMore || !state.hasMore) return

		viewModelScope.launch {
			_uiState.update { it.copy(isLoadingMore = true) }
			fetchCommentsUseCase(currentVideoId, state.cursorId)
				.onSuccess { page ->
					_uiState.update { s ->
						s.copy(
							comments = s.comments + page.comments,
							isLoadingMore = false,
							cursorId = page.cursorId,
							hasMore = page.hasMore
						)
					}
				}
				.onFailure { e ->
					_uiState.update { it.copy(error = e.message, isLoadingMore = false) }
				}
		}
	}

	/**
	 * 展开回复：按服务端 replyCount 持续翻页，把该根评论下的回复一次性拉齐后写入模型
	 */
	fun loadReplies(parentId: Long) {
		if (!loadingRepliesIds.add(parentId)) return
		val expected = findCommentById(parentId)?.replyCount ?: 0
		if (expected <= 0) {
			loadingRepliesIds.remove(parentId)
			return
		}

		viewModelScope.launch {
			var accumulated = emptyList<Comment>()
			var cursor: Long? = null
			var hasMore = true
			var pages = 0
			try {
				while (hasMore && accumulated.size < expected && pages < MAX_REPLY_PAGES) {
					pages++
					val page = fetchRepliesUseCase(parentId, cursor).getOrElse { e ->
						_uiState.update { it.copy(error = e.message ?: "回复加载失败") }
						return@launch
					}
					accumulated += page.comments
					cursor = page.cursorId
					hasMore = page.hasMore
				}
				val replies = accumulated
				_uiState.update { state ->
					state.copy(
						comments = state.comments.map { comment ->
							if (comment.id == parentId) comment.copy(replies = replies) else comment
						}
					)
				}
			} finally {
				loadingRepliesIds.remove(parentId)
			}
		}
	}

	fun postComment(content: String) {
		if (currentVideoId == 0L) return
		val trimmedContent = content.trim()
		if (trimmedContent.isEmpty()) {
			_uiState.update { it.copy(error = "评论内容不能为空") }
			return
		}
		viewModelScope.launch {
			_uiState.update { it.copy(isPosting = true, error = null) }
			postCommentUseCase(currentVideoId, trimmedContent)
				.onSuccess { newComment ->
					_uiState.update { state ->
						state.copy(
							comments = listOf(newComment) + state.comments,
							isPosting = false,
							postSuccess = true
						)
					}
				}
				.onFailure { e ->
					_uiState.update { it.copy(error = e.message, isPosting = false) }
				}
		}
	}

	/**
	 * 发表回复：楼层语义拍平——无论回复的是根评论还是楼中楼，parentId 一律指向根评论，
	 * replyToUserId 指向被回复的那条评论作者（服务端同样做拍平兜底）
	 */
	fun replyComment(content: String) {
		val replyTo = _uiState.value.replyToComment ?: return
		val parentId = replyTo.parentId ?: replyTo.id
		val trimmedContent = content.trim()
		if (trimmedContent.isEmpty()) {
			_uiState.update { it.copy(error = "回复内容不能为空") }
			return
		}
		viewModelScope.launch {
			_uiState.update { it.copy(isPosting = true, error = null) }
			replyCommentUseCase(parentId, replyTo.videoId, trimmedContent, replyTo.userId)
				.onSuccess { newReply ->
					_uiState.update { state ->
						state.copy(
							comments = state.comments.map { comment ->
								if (comment.id != parentId) {
									comment
								} else if (comment.replies.isNotEmpty()) {
									comment.copy(
										replies = comment.replies + newReply,
										replyCount = comment.replyCount + 1
									)
								} else {
									// 回复列表尚未展开过，不塞入不完整的列表，只同步计数
									comment.copy(replyCount = comment.replyCount + 1)
								}
							},
							replyToComment = null,
							isPosting = false,
							postSuccess = true
						)
					}
				}
				.onFailure { e ->
					_uiState.update { it.copy(error = e.message, isPosting = false) }
				}
		}
	}

	fun likeComment(commentId: Long) {
		val current = findCommentById(commentId) ?: return
		val newLiked = current.isLiked.not()

		// 乐观更新 — 先改 UI
		_uiState.update { state ->
			state.copy(comments = updateCommentLike(state.comments, commentId, newLiked))
		}

		viewModelScope.launch {
			likeCommentUseCase(commentId, newLiked)
				.onFailure { e ->
					// 失败回滚：只把该条翻回去，不覆盖期间的其他并发变更
					_uiState.update { state ->
						state.copy(
							error = e.message ?: "操作失败",
							comments = updateCommentLike(state.comments, commentId, !newLiked)
						)
					}
				}
		}
	}

	fun deleteComment(commentId: Long) {
		val rootId = rootIdOf(commentId)
		viewModelScope.launch {
			deleteCommentUseCase(commentId)
				.onSuccess {
					_uiState.update { state ->
						val comments = removeComment(state.comments, commentId)
						// 删除的是回复时，同步扣减所属根评论的回复数
						val adjusted = if (rootId != null && rootId != commentId) {
							adjustReplyCount(comments, rootId, -1)
						} else {
							comments
						}
						state.copy(comments = adjusted, deleteSuccess = true)
					}
				}
				.onFailure { e ->
					_uiState.update { it.copy(error = e.message) }
				}
		}
	}

	fun setReplyTo(comment: Comment?) {
		_uiState.update { it.copy(replyToComment = comment) }
	}

	fun clearPostSuccess() {
		_uiState.update { it.copy(postSuccess = false) }
	}

	fun clearDeleteSuccess() {
		_uiState.update { it.copy(deleteSuccess = false) }
	}

	fun clearError() {
		_uiState.update { it.copy(error = null) }
	}

	private fun findCommentById(commentId: Long): Comment? {
		fun search(list: List<Comment>): Comment? {
			for (comment in list) {
				if (comment.id == commentId) return comment
				search(comment.replies)?.let { return it }
			}
			return null
		}
		return search(_uiState.value.comments)
	}

	/** 找到 commentId 所属根评论 id；自身就是根评论时返回自身 id，找不到返回 null */
	private fun rootIdOf(commentId: Long): Long? {
		for (comment in _uiState.value.comments) {
			if (comment.id == commentId) return comment.id
			if (comment.replies.any { it.id == commentId }) return comment.id
		}
		return null
	}

	private fun updateCommentLike(
		comments: List<Comment>,
		commentId: Long,
		isLiked: Boolean
	): List<Comment> {
		return comments.map { comment ->
			when {
				comment.id == commentId -> comment.copy(
					isLiked = isLiked,
					likeCount = if (isLiked) comment.likeCount + 1 else comment.likeCount - 1
				)
				else -> comment.copy(replies = updateCommentLike(comment.replies, commentId, isLiked))
			}
		}
	}

	private fun adjustReplyCount(comments: List<Comment>, rootId: Long, delta: Int): List<Comment> {
		return comments.map { comment ->
			if (comment.id == rootId) {
				comment.copy(replyCount = (comment.replyCount + delta).coerceAtLeast(0))
			} else {
				comment
			}
		}
	}

	private fun removeComment(comments: List<Comment>, commentId: Long): List<Comment> {
		return comments
			.filter { it.id != commentId }
			.map { it.copy(replies = removeComment(it.replies, commentId)) }
	}
}
