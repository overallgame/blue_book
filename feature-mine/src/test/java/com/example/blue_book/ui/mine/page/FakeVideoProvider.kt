package com.example.blue_book.ui.mine.page

import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.provider.IVideoProvider

/**
 * `IVideoProvider` 的测试替身：只实现本测试真正用到的两个方法。
 *
 * **下面那 9 个 `unused()` 就是「接口少而稳」和「可测试性」是同一件事的证据**：
 * 一个只想测「加载更多」的用例，必须为 11 个方法里的 9 个写占位。
 * 好处是这个 Fake 能复用于 8 个列表页（它们共用同一个 `IVideoProvider`），痛被摊薄了
 * ——这也是当初把它拆成 4 个接口（`IAuthProvider` 2 个方法、`INotificationProvider` 1 个）的回报。
 */
class FakeVideoProvider : IVideoProvider {

	/** 每次 `fetchLikedVideos` 的入参；用来断言游标是否推进、有没有多余的请求 */
	val likedVideoCalls = mutableListOf<Pair<Long?, Int?>>()

	/** 每次 `likeVideo` 的入参 */
	val likeCalls = mutableListOf<Pair<Long, Boolean>>()

	/** 按顺序出队返回；出队完返回空列表 */
	var likedVideoPages: MutableList<Result<List<VideoCardInfo>>> = mutableListOf()

	var likeResult: Result<Unit> = Result.success(Unit)

	override suspend fun fetchLikedVideos(cursorId: Long?, size: Int?): Result<List<VideoCardInfo>> {
		likedVideoCalls += cursorId to size
		return if (likedVideoPages.isEmpty()) {
			Result.success(emptyList())
		} else {
			likedVideoPages.removeAt(0)
		}
	}

	override suspend fun likeVideo(aid: Long, liked: Boolean): Result<Unit> {
		likeCalls += aid to liked
		return likeResult
	}

	/**
	 * 其余 9 个方法本测试用不到。这里**故意抛异常而不是返回空值**：
	 * 如果哪条路径误用了别的数据来源，测试要立刻炸出来，而不是静默拿到空数据后
	 * 让断言以「items 是空的」这种看不出原因的方式失败。
	 */
	private fun unused(): Nothing =
		throw AssertionError("本用例不该走这个接口方法——走到了说明页面用错了数据来源")

	override suspend fun fetchRandomVideos(cursorId: Long?, size: Int?) = unused()

	override suspend fun fetchFollowingFeed(cursorId: Long?, size: Int?) = unused()

	override suspend fun fetchRegionFeed(region: String, cursorId: Long?, size: Int?) = unused()

	override suspend fun fetchVideoById(aid: Long) = unused()

	override suspend fun fetchVideosByKeyword(keyword: String, cursorId: Long?, size: Int?) = unused()

	override suspend fun collectVideo(aid: Long, collected: Boolean) = unused()

	override suspend fun fetchCollectedVideos(cursorId: Long?, size: Int?) = unused()

	override suspend fun fetchUserVideos(userId: Long, cursorId: Long?, size: Int?) = unused()

	override suspend fun deleteVideo(videoId: Long) = unused()
}
