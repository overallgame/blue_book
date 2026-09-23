package com.example.blue_book.data.upload

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [reconcile] 的穷举测试：**"以服务端为准"这条规矩的唯一落地点**。
 *
 * 每条用例对应一个真实的坏情况，而不是把 if 复述一遍。之所以值得这么多条，
 * 是因为它错了的表现是"用户以为传完了、其实服务端少一片"，只在最后合并时才炸，
 * 而那时报的还是"文件校验失败"——离真正的原因很远。
 */
class ReconcileTest {

	private val uri = "content://media/external/video/media/42"
	private val chunkSize = 1024L
	private val fileSize = 3 * chunkSize + 100 // 4 片，末片 100 字节

	private fun ledger(vararg statuses: UploadPartStatus): List<UploadPartRecord> =
		statuses.mapIndexed { index, status ->
			UploadPartRecord(
				uri = uri, index = index, offset = index * chunkSize,
				size = minOf(chunkSize, fileSize - index * chunkSize), status = status
			)
		}

	private fun statuses(parts: List<UploadPartRecord>): List<UploadPartStatus> = parts.map { it.status }

	// ───────────── 服务端有、本地没有 → 补记 ─────────────

	@Test
	fun `chunks the server has but local ledger does not are recorded as done`() {
		// 换设备/清了数据的场景：本地什么都没有，服务端已经有 0、2 两片
		val result = reconcile(
			local = emptyList(), serverChunks = setOf(0, 2),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertEquals(
			"账本永远覆盖整个文件的分片区间（这里是 4 片），而不是「我见过哪些片」",
			listOf(0, 1, 2, 3), result.map { it.index }
		)
		assertEquals(
			"服务端有的两片落成 DONE，其余待传",
			listOf(
				UploadPartStatus.DONE, UploadPartStatus.PENDING,
				UploadPartStatus.DONE, UploadPartStatus.PENDING
			),
			statuses(result)
		)
		assertEquals(
			"每一片都要带上正确的偏移",
			listOf(0L, chunkSize, 2 * chunkSize, 3 * chunkSize), result.map { it.offset }
		)
	}

	@Test
	fun `a chunk the server has is done even if local says failed`() {
		// 请求成功但响应丢了（或客户端把成功判成失败）：服务端有就是有
		val result = reconcile(
			local = ledger(UploadPartStatus.FAILED, UploadPartStatus.PENDING),
			serverChunks = setOf(0),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertEquals(UploadPartStatus.DONE, result.first { it.index == 0 }.status)
		assertEquals(UploadPartStatus.PENDING, result.first { it.index == 1 }.status)
	}

	// ───────────── 服务端没有、本地是 DONE → 打回 ─────────────

	@Test
	fun `a chunk local thinks is done but the server does not have is queued again`() {
		// 这是最关键的一条：本地"传成功"不代表服务端"存住了"
		val result = reconcile(
			local = ledger(UploadPartStatus.DONE, UploadPartStatus.DONE),
			serverChunks = setOf(0),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertEquals(UploadPartStatus.DONE, result.first { it.index == 0 }.status)
		assertEquals(
			"服务端没有的片必须重传，否则用户以为传完了、合并时才发现少一片",
			UploadPartStatus.PENDING, result.first { it.index == 1 }.status
		)
	}

	// ───────────── 进程被杀留下的 UPLOADING → 打回 ─────────────

	@Test
	fun `in flight parts are queued again because process death leaves no conclusion`() {
		val result = reconcile(
			local = ledger(UploadPartStatus.UPLOADING, UploadPartStatus.UPLOADING),
			serverChunks = emptySet(),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertTrue(
			"进程被杀时那次传输没有结论：既不能当成功也不能当失败",
			result.all { it.status == UploadPartStatus.PENDING }
		)
		assertEquals("账本仍是完整的 4 片", 4, result.size)
	}

	@Test
	fun `failed status is preserved so backoff counters keep working`() {
		val result = reconcile(
			local = ledger(UploadPartStatus.FAILED),
			serverChunks = emptySet(),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertEquals(
			"FAILED 与 PENDING 对重传的结论相同，但保留它才能看出这片试过几次",
			UploadPartStatus.FAILED, result.first { it.index == 0 }.status
		)
	}

	@Test
	fun `retry count is carried over`() {
		val local = listOf(
			UploadPartRecord(uri = uri, index = 0, offset = 0, size = chunkSize, retryCount = 3)
		)

		val result = reconcile(local, emptySet(), chunkSize, fileSize)

		assertEquals(3, result.first { it.index == 0 }.retryCount)
	}

	// ───────────── 补全与边界 ─────────────

	@Test
	fun `result covers exactly the union of local and server indexes`() {
		val result = reconcile(
			local = ledger(UploadPartStatus.DONE, UploadPartStatus.PENDING, UploadPartStatus.PENDING),
			serverChunks = setOf(2, 3),
			chunkSize = chunkSize, fileSize = fileSize
		)

		assertEquals("本地有 0..2、服务端有 2..3 ⇒ 并集是 0..3", listOf(0, 1, 2, 3), result.map { it.index })
		assertEquals(
			listOf(
				// 0：本地说 DONE，但服务端没有它 ⇒ 打回（这正是"本地不代表服务端"那条规则）
				UploadPartStatus.PENDING,
				// 1：两边都没有 ⇒ 待传
				UploadPartStatus.PENDING,
				// 2、3：服务端有 ⇒ 就是 DONE
				UploadPartStatus.DONE,
				UploadPartStatus.DONE
			),
			statuses(result)
		)
	}

	@Test
	fun `size of the tail chunk is the remainder not the full chunk size`() {
		val result = reconcile(emptyList(), setOf(3), chunkSize, fileSize)

		val tail = result.first { it.index == 3 }
		assertEquals("末片长度是零头", 100L, tail.size)
		assertEquals(3 * chunkSize, tail.offset)
	}

	@Test
	fun `empty server list means everything is pending`() {
		val result = reconcile(ledger(UploadPartStatus.DONE), emptySet(), chunkSize, fileSize)

		assertTrue(
			"服务端一片都没有时，本地的 DONE 全部作废",
			result.all { it.status == UploadPartStatus.PENDING }
		)
	}
}
