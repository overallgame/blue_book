package com.example.blue_book.presentation.comment

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

    fun loadComments(videoId: Long) {
        if (videoId == currentVideoId && _uiState.value.comments.isNotEmpty()) return
        currentVideoId = videoId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, cursorId = null, hasMore = true) }
            fetchCommentsUseCase(videoId)
                .onSuccess { comments ->
                    _uiState.update { it.copy(comments = comments, isLoading = false) }
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
                .onSuccess { comments ->
                    _uiState.update { s ->
                        s.copy(
                            comments = s.comments + comments,
                            isLoadingMore = false,
                            cursorId = comments.lastOrNull()?.id
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoadingMore = false) }
                }
        }
    }

    fun loadReplies(parentId: Long) {
        viewModelScope.launch {
            fetchRepliesUseCase(parentId)
                .onSuccess { replies ->
                    _uiState.update { state ->
                        val updatedComments = state.comments.map { comment ->
                            if (comment.id == parentId) {
                                comment.copy(replies = replies)
                            } else {
                                comment
                            }
                        }
                        state.copy(comments = updatedComments)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
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

    fun replyComment(content: String) {
        val replyTo = _uiState.value.replyToComment ?: return
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            _uiState.update { it.copy(error = "回复内容不能为空") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, error = null) }
            replyCommentUseCase(replyTo.id, replyTo.videoId, trimmedContent, replyTo.userId)
                .onSuccess { newReply ->
                    _uiState.update { state ->
                        val updatedComments = state.comments.map { comment ->
                            if (comment.id == replyTo.id) {
                                comment.copy(replies = comment.replies + newReply)
                            } else {
                                comment
                            }
                        }
                        state.copy(
                            comments = updatedComments,
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
        viewModelScope.launch {
            val comment = findCommentById(commentId)
            val newLikedState = comment?.isLiked?.not() ?: return@launch

            likeCommentUseCase(commentId, newLikedState)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedComments = updateCommentLike(state.comments, commentId, newLikedState)
                        state.copy(comments = updatedComments)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            deleteCommentUseCase(commentId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(comments = removeComment(state.comments, commentId))
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun findCommentById(commentId: Long): Comment? {
        return _uiState.value.comments.find { it.id == commentId }
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

    private fun removeComment(comments: List<Comment>, commentId: Long): List<Comment> {
        return comments
            .filter { it.id != commentId }
            .map { it.copy(replies = removeComment(it.replies, commentId)) }
    }
}
