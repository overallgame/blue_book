package com.example.blue_book.ui.video

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.data.remote.video.VideoRemoteDataSource
import com.example.blue_book.domain.repository.VideoRepository
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.udf.UdfViewModel
import com.example.blue_book.domain.model.Video
import com.example.blue_book.domain.usecase.CollectVideoUseCase
import com.example.blue_book.domain.usecase.FetchPlayUrlUseCase
import com.example.blue_book.domain.usecase.FetchRandomVideosUseCase
import com.example.blue_book.domain.usecase.FetchVideosByKeywordUseCase
import com.example.blue_book.domain.usecase.LikeVideoUseCase
import com.example.blue_book.event.VideoInteractionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
	private val fetchRandomVideos: FetchRandomVideosUseCase,
	private val fetchByKeyword: FetchVideosByKeywordUseCase,
	private val fetchPlayUrl: FetchPlayUrlUseCase,
	private val likeVideoUseCase: LikeVideoUseCase,
	private val collectVideoUseCase: CollectVideoUseCase,
	private val transcodeDataSource: VideoRemoteDataSource,
	private val videoRepository: VideoRepository,
	private val currentUser: CurrentUser
) : UdfViewModel<VideoIntent, VideoUiState, VideoUiEffect>(VideoUiState()) {

	private val togglingAids = mutableSetOf<Long>()
	private val togglingCollectAids = mutableSetOf<Long>()
	private val togglingFollowAids = mutableSetOf<Long>()

	private companion object {
		/** 转码状态轮询上限与间隔 */
		const val MAX_TRANSCODE_POLLS = 3
		const val TRANSCODE_POLL_INTERVAL_MS = 2000L

		/** 续拉同源列表的每页条数 */
		const val SOURCE_PAGE_SIZE = 20
	}

	/** 来源列表模式对应的用户 id（仅 UserVideos 模式使用） */
	private var sourceUserId: Long = 0L

	override suspend fun handleIntent(intent: VideoIntent) {
		when (intent) {
			VideoIntent.InitRandom -> initRandom()
			is VideoIntent.InitSearch -> initSearch(intent.keyword)
			is VideoIntent.InitFromSource -> initFromSource(intent)
			VideoIntent.LoadMore -> loadMore()
			is VideoIntent.RequestPlayUrl -> requestPlayUrl(intent.aid, intent.cid)
			is VideoIntent.CheckTranscode -> checkTranscode(intent.aid, intent.originMessage)
			is VideoIntent.AdjustCommentCount -> adjustCommentCount(intent.aid, intent.delta)
			is VideoIntent.ToggleLike -> toggleLike(intent.video)
			is VideoIntent.ToggleCollect -> toggleCollect(intent.video)
			is VideoIntent.ToggleFollow -> toggleFollow(intent.video)
			is VideoIntent.ReportView -> reportView(intent.aid)
		}
	}

	/** 已上报过播放量的视频 id（会话内去重） */
	private val reportedViewAids = mutableSetOf<Long>()

	/** 播放量上报：静默失败，不打扰播放体验 */
	private suspend fun reportView(aid: Long) {
		if (aid <= 0L || !reportedViewAids.add(aid)) return
		runCatching { videoRepository.reportView(aid) }
	}

	/**
	 * 从来源列表（搜索/点赞/收藏/作品）进入：播放列表以点击的视频为首条，
	 * 后续 loadMore 用来源游标续拉同源内容
	 */
	private suspend fun initFromSource(intent: VideoIntent.InitFromSource) {
		sourceUserId = intent.userId
		setState {
			copy(
				items = intent.firstVideo?.let { listOf(it) } ?: emptyList(),
				isLoading = false,
				message = null,
				mode = intent.mode,
				keyword = intent.keyword,
				hasMore = true
			)
		}
		loadMore()
	}

	/** 空态重试：按当前模式重新初始化数据 */
	fun retryInit() {
		val state = uiState.value
		if (state.isLoading) return
		when (state.mode) {
			VideoUiState.Mode.Search -> dispatch(VideoIntent.InitSearch(state.keyword))
			else -> {
				// 保留 mode / keyword / sourceUserId，只清空列表与游标后按原模式重拉。
				// 不能走 InitRandom —— 那会把 mode 覆写为 Random 且清空 keyword，
				// 使"我的喜欢/收藏/作品"点重试跳到随机流。
				setState { copy(items = emptyList(), hasMore = true, message = null) }
				dispatch(VideoIntent.LoadMore)
			}
		}
	}

	private suspend fun initRandom() {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, mode = VideoUiState.Mode.Random, keyword = "") } },
			call = { fetchRandomVideos() },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun initSearch(keyword: String) {
		runResult(
			onStart = { setState { copy(items = emptyList(), isLoading = true, message = null, mode = VideoUiState.Mode.Search, keyword = keyword) } },
			call = { fetchByKeyword(keyword) },
			onSuccess = { list -> setState { copy(items = items + list.map(::toUi), isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "搜索失败") } }
		)
	}

	private suspend fun loadMore() {
		val state = uiState.value
		if (state.isLoading || !state.hasMore) return
		val cursorId = if (state.items.isNotEmpty()) state.items.last().aid else null
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = {
				// 各分支统一为 Result<List<VideoCardInfo>>，用 toUi 映射成卡片
				when (state.mode) {
					VideoUiState.Mode.Random ->
						fetchRandomVideos(cursorId).map { list -> list.map(::toUi) }
					VideoUiState.Mode.Search ->
						fetchByKeyword(state.keyword, cursorId).map { list -> list.map(::toUi) }
					VideoUiState.Mode.Liked ->
						videoRepository.fetchLikedVideos(cursorId, SOURCE_PAGE_SIZE)
							.map { list -> list.map(::toUi) }
					VideoUiState.Mode.Collected ->
						videoRepository.fetchCollectedVideos(cursorId, SOURCE_PAGE_SIZE)
							.map { list -> list.map(::toUi) }
					VideoUiState.Mode.UserVideos ->
						videoRepository.fetchUserVideos(sourceUserId, cursorId, SOURCE_PAGE_SIZE)
							.map { list -> list.map(::toUi) }
				}
			},
			onSuccess = { list ->
				val mapped = list.filter { nv -> state.items.none { it.aid == nv.aid } }
				val hasMore = list.isNotEmpty()
				setState { copy(items = items + mapped, isLoading = false, hasMore = hasMore) }
				// 列表已拉到末尾：提示一次"没有更多了"（空列表时由空态承担）
				if (!hasMore && state.items.isNotEmpty()) {
					sendEffect(VideoUiEffect.ShowToast("没有更多了"))
				}
			},
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	/**
	 * 播放失败时查询转码状态：有限轮询（3 次 × 2s），
	 * DONE/FAILED 即时明确提示，仍在处理中则提示稍后重试
	 */
	private suspend fun checkTranscode(aid: Long, originMessage: String) {
		repeat(MAX_TRANSCODE_POLLS) { attempt ->
			val result = withContext(Dispatchers.IO) { transcodeDataSource.transcodeStatus(aid) }
			when {
				result.isFailure -> return sendEffect(VideoUiEffect.ShowToast(originMessage))
				result.getOrNull()!!.equals("DONE", true) ->
					return sendEffect(VideoUiEffect.ShowToast("转码已完成，点击重试即可播放"))
				result.getOrNull()!!.equals("FAILED", true) ->
					return sendEffect(VideoUiEffect.ShowToast("转码失败，请删除后重新上传"))
			}
			if (attempt < MAX_TRANSCODE_POLLS - 1) delay(TRANSCODE_POLL_INTERVAL_MS)
		}
		sendEffect(VideoUiEffect.ShowToast("视频转码处理中，请稍后重试"))
	}

	/** 评论数增量同步：更新 state 列表（防 UpdateItem effect 用旧值把评论数回退） */
	private suspend fun adjustCommentCount(aid: Long, delta: Int) {
		val target = uiState.value.items.firstOrNull { it.aid == aid } ?: return
		val updated = target.copy(commentCount = (target.commentCount + delta).coerceAtLeast(0))
		updateItemInList(updated)
		sendEffect(VideoUiEffect.UpdateItem(updated))
		// 广播到各列表页，返回列表卡片上的评论数同步
		VideoInteractionBus.publishCommentCount(aid, updated.commentCount)
	}

	private fun toUi(v: Video): VideoCardInfo {
		return VideoCardInfo(
			aid = v.aid,
			cid = v.cid,
			like = v.like,
			image = v.image,
			avatar = v.avatar,
			collection = v.collection,
			nickname = v.nickname,
			description = v.description,
			playUrl = v.playUrl,
			isLike = v.isLike,
			isCollect = v.isCollect,
			commentCount = v.commentCount,
			uploaderId = v.uploaderId,
			isFollowed = v.isFollowed
		)
	}

	private suspend fun requestPlayUrl(aid: Long, cid: Long) {
		val current = uiState.value.items
		val target = current.firstOrNull { it.aid == aid && it.cid == cid } ?: return
		if (target.playUrl.isNotBlank()) return
		runResult(
			call = { withContext(Dispatchers.IO) { fetchPlayUrl(aid, cid) } },
			onSuccess = { url -> sendEffect(VideoUiEffect.UpdateItem(target.copy(playUrl = url))) },
			onFailure = { e -> sendEffect(VideoUiEffect.ShowToast(e.message ?: "获取播放地址失败")) }
		)
	}

	private suspend fun toggleLike(video: VideoCardInfo) {
		// 游客可看视频，点赞需登录
		if (currentUser.userId == null) {
			sendEffect(VideoUiEffect.ShowLoginGuide)
			return
		}
		if (video.aid in togglingAids) return
		togglingAids.add(video.aid)
		val newStatus = !video.isLike
		val newLikeNumber = video.like + if (newStatus) 1 else -1
		val updatedVideo = video.copy(isLike = newStatus, like = newLikeNumber)

		// 先更新本地UI（发 UpdateItem 驱动列表局部刷新）
		updateItemInList(updatedVideo)
		sendEffect(VideoUiEffect.UpdateItem(updatedVideo))

		// 发送网络请求
		runResult(
			call = { withContext(Dispatchers.IO) {
				likeVideoUseCase(video.aid, newStatus)
			} },
			onSuccess = { togglingAids.remove(video.aid) },
			onFailure = { e ->
				// 失败回滚
				updateItemInList(video)
				sendEffect(VideoUiEffect.UpdateItem(video))
				sendEffect(VideoUiEffect.ShowToast(e.message ?: "操作失败"))
				togglingAids.remove(video.aid)
			}
		)
	}

	private suspend fun toggleCollect(video: VideoCardInfo) {
		// 游客可看视频，收藏需登录
		if (currentUser.userId == null) {
			sendEffect(VideoUiEffect.ShowLoginGuide)
			return
		}
		if (video.aid in togglingCollectAids) return
		togglingCollectAids.add(video.aid)
		val newStatus = !video.isCollect
		val newCollectionNumber = video.collection + if (newStatus) 1 else -1
		val updatedVideo = video.copy(isCollect = newStatus, collection = newCollectionNumber)

		// 先更新本地UI（发 UpdateItem 驱动列表局部刷新）
		updateItemInList(updatedVideo)
		sendEffect(VideoUiEffect.UpdateItem(updatedVideo))

		// 发送网络请求
		runResult(
			call = { withContext(Dispatchers.IO) { collectVideoUseCase(video.aid, newStatus) } },
			onSuccess = { togglingCollectAids.remove(video.aid) },
			onFailure = { e ->
				// 失败回滚
				updateItemInList(video)
				sendEffect(VideoUiEffect.UpdateItem(video))
				sendEffect(VideoUiEffect.ShowToast(e.message ?: "操作失败"))
				togglingCollectAids.remove(video.aid)
			}
		)
	}

	/**
	 * 关注/取关作者：乐观更新 isFollowed → 调 /users/{id}/follow 或 DELETE → 失败回滚
	 * （后端已提供完整关注 API，见 UserController）
	 */
	private suspend fun toggleFollow(video: VideoCardInfo) {
		val myId = currentUser.userId
		if (myId == null) return sendEffect(VideoUiEffect.ShowLoginGuide)
		if (video.uploaderId == myId || video.uploaderId == 0L) return
		if (video.uploaderId in togglingFollowAids) return
		togglingFollowAids.add(video.uploaderId)

		val newState = !video.isFollowed
		val updated = video.copy(isFollowed = newState)
		updateItemInList(updated)
		sendEffect(VideoUiEffect.UpdateItem(updated))

		runResult(
			call = {
				if (newState) videoRepository.followUser(video.uploaderId)
				else videoRepository.unfollowUser(video.uploaderId)
			},
			onSuccess = {
				togglingFollowAids.remove(video.uploaderId)
				sendEffect(VideoUiEffect.ShowToast(if (newState) "已关注 ${video.nickname}" else "已取消关注 ${video.nickname}"))
			},
			onFailure = { e ->
				// 失败回滚
				updateItemInList(video)
				sendEffect(VideoUiEffect.UpdateItem(video))
				sendEffect(VideoUiEffect.ShowToast(e.message ?: "操作失败"))
				togglingFollowAids.remove(video.uploaderId)
			}
		)
	}

	private fun updateItemInList(updatedVideo: VideoCardInfo) {
		setState {
			val newItems = items.map { if (it.aid == updatedVideo.aid) updatedVideo else it }
			copy(items = newItems)
		}
	}
}
