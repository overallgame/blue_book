package com.example.blue_book.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [isExpired] 的边界：它决定发布页还提不提示"继续上次未完成的上传"。
 *
 * 判错有两个方向，代价不同，所以窗口两端都要贴着毫秒验：
 *  - 判早了：把还能续传的会话当成过期，本地账本连同缓存的文件指纹一起被删，用户白传一遍
 *  - 判晚了：横幅承诺"已传 68%"，而服务端的分片已被定时清理，点下去还是从 0 开始
 */
class UploadSessionRetentionTest {

	private val now = 1_800_000_000_000L
	private val window = UPLOAD_SESSION_RETENTION_HOURS * 60 * 60 * 1000

	@Test
	fun `a session active just now is resumable`() {
		assertFalse(
			"刚传过的会话必须还能续传",
			session(updatedAt = now).isExpired(now)
		)
	}

	@Test
	fun `a session exactly at the window is still resumable`() {
		// 边界算"还没超过"：服务端的清理是每小时跑一次的定时任务，
		// 正好卡在 48 小时的会话一定还没被它扫到
		assertFalse(
			"正好 48 小时的会话还不算过期",
			session(updatedAt = now - window).isExpired(now)
		)
	}

	@Test
	fun `one millisecond past the window is expired`() {
		assertTrue(
			"刚过窗口就该算过期——窗口按「最后活动时间」计，不是「创建时间」",
			session(updatedAt = now - window - 1).isExpired(now)
		)
	}

	@Test
	fun `a session left for days is expired`() {
		assertTrue(
			"几天前的会话早该作废",
			session(updatedAt = now - window * 3).isExpired(now)
		)
	}

	@Test
	fun `a future timestamp is not treated as expired`() {
		// 用户改过系统时间/时钟回拨：updatedAt 落在未来时差为负，
		// 不能因为"负的时间差"就把它判成过期
		assertFalse(
			"时间戳在未来的会话不该被当成过期",
			session(updatedAt = now + window).isExpired(now)
		)
	}

	private fun session(updatedAt: Long) = UploadSessionRecord(
		uri = "content://media/external/video/media/42",
		fileName = "a.mp4", fileSize = 5_000_000, fileMd5 = "md5",
		chunkSize = 1_000_000, totalChunks = 5, updatedAt = updatedAt
	)
}
