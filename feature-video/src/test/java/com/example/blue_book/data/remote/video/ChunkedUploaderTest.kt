package com.example.blue_book.data.remote.video

import com.example.blue_book.data.UploadPartRecord
import com.example.blue_book.data.UploadPartStatus
import com.example.blue_book.data.UploadSessionRecord
import com.example.blue_book.data.UploadSessionStatus
import com.example.blue_book.data.remote.video.dto2.UploadInitRequestDto
import com.example.blue_book.network.apiCall
import com.example.blue_book.network.apiUnitCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 分片上传编排的测试（feature-video 的第一个测试源集）。
 *
 * 覆盖的是**改动前后行为不同**的那些点，而不是把 happy path 复述一遍：
 * 分片并发度真的被限住、进度在并发下仍然单调、可重试与不可重试的区别、
 * 续传只补缺片、不可随机定位的源会退化且只开一个流、偏移算对没有。
 *
 * 用真 HTTP（MockWebServer）而不是假数据源：`@Query` 的序列化、multipart 的实际字节、
 * Retrofit 的并发行为都只在真连接上才成立；而内容源换成字节数组
 * （[ByteArrayUploadSource]），于是整条链不需要设备。
 *
 * ★ 假服务端**必须与真服务端一样严**（[UploadServer] 的类注释列了三条），
 * 否则测出来的"通过"是假的——本项目刚踩过：假服务端不区分 HTTP 状态码与业务码，
 * 于是"500 可重试"那条用例实际喂的是 400，客户端行为没错但用例毫无意义。
 */
class ChunkedUploaderTest {

    private companion object {
        /** 用服务端允许的最小分片（1MB）：既是真实契约，又能让测试数据小到可接受 */
        const val CHUNK_SIZE = 1024L * 1024L

        /** 4 片整 + 一个零头 = 5 片（顺带覆盖"末片短于分片大小"） */
        const val FILE_SIZE = 4 * CHUNK_SIZE + 12345
        const val TAIL_SIZE = 12345
        const val TOTAL_CHUNKS = 5
    }

    private lateinit var server: UploadServer
    private lateinit var store: InMemoryUploadSessionStore
    private lateinit var uploader: ChunkedUploader

    @Before
    fun setUp() {
        server = UploadServer()
        store = InMemoryUploadSessionStore()
        // 提议 1MB（服务端允许区间的下限，是合法值）：既能用少量数据覆盖多片路径，
        // 也不必让假服务端去假装接受一个真服务端会拒绝的分片大小
        uploader = ChunkedUploader(RealHttpChunkRemote(server.url), store, proposedChunkSize = CHUNK_SIZE)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newLedger(uri: String, allDone: Set<Int>) = (0 until TOTAL_CHUNKS).map { index ->
        UploadPartRecord(
            uri = uri, index = index, offset = index * CHUNK_SIZE,
            size = minOf(CHUNK_SIZE, FILE_SIZE - index * CHUNK_SIZE),
            status = if (index in allDone) UploadPartStatus.DONE else UploadPartStatus.PENDING
        )
    }

    private fun source(randomAccess: Boolean = true) = ByteArrayUploadSource(
        bytes = ByteArray(FILE_SIZE.toInt()) { (it % 251).toByte() },
        randomAccess = randomAccess
    )

    // ───────────────────────── 并发 ─────────────────────────

    @Test
    fun `uploads parts concurrently but never more than the limit`() = runBlocking {
        uploader.upload(source()) { }

        assertEquals("5 片都应上传", (0 until TOTAL_CHUNKS).toSet(), server.receivedChunkIndexes())
        assertEquals("并发度应恰好用满上限 3", 3, server.maxInFlightChunks)
    }

    @Test
    fun `progress is monotonic and ends at 100 under concurrency`() = runBlocking {
        val progress = Collections.synchronizedList(mutableListOf<Int>())

        uploader.upload(source()) { progress += it }

        assertTrue("进度不能倒退（并发下按 index+1 算就会倒退）：$progress", progress == progress.sorted())
        assertEquals("最后必须到 100", 100, progress.last())
    }

    @Test
    fun `every part carries the bytes of its own offset`() = runBlocking {
        val src = source()

        uploader.upload(src) { }

        // 偏移算错的表现是"每一片都传了、每一片都不对"，在真机上表现为花屏/音画错位，
        // 所以逐片按字节比对，而不是只看片数
        for (index in 0 until TOTAL_CHUNKS) {
            val expected = src.slice(index * CHUNK_SIZE, CHUNK_SIZE.toInt())
            val received = server.partContent(index)
            assertTrue(
                "第 $index 片必须与源文件同偏移的切片逐字节相同" +
                    "（期望 ${expected.size} 字节，实收 ${received.size} 字节）",
                expected.contentEquals(received)
            )
        }
        assertEquals("末片应当是零头而不是整片", TAIL_SIZE, server.partContent(TOTAL_CHUNKS - 1).size)
    }

    // ───────────────────────── 退化：不可随机定位 ─────────────────────────

    @Test
    fun `falls back to a single sequential stream when the source cannot seek`() = runBlocking {
        val src = source(randomAccess = false)

        uploader.upload(src) { }

        assertEquals("退化后必须串行", 1, server.maxInFlightChunks)
        assertEquals("退化后只应打开一个流（老实现就是这条路径）", 1, src.readerOpens)
        assertEquals("五片仍要全部上传", (0 until TOTAL_CHUNKS).toSet(), server.receivedChunkIndexes())
    }

    // ───────────────────────── 重试策略 ─────────────────────────

    @Test
    fun `retries a chunk in place when the failure is retryable`() = runBlocking {
        // 服务端临时故障（HTTP 500）：同一片原地重试即可，不必把整条链推倒重来
        server.failChunk(index = 2, httpStatus = 500, times = 1)

        uploader.upload(source()) { }

        assertEquals("500 应当原地重试，不该重新 init", 1, server.initCount)
        assertEquals("第 2 片应当被请求两次（一次失败 + 一次成功）", 2, server.chunkRequests(2))
    }

    @Test
    fun `does not retry a chunk when the failure is a business rejection`() = runBlocking {
        // HTTP 400 + 业务码 13002（会话不存在/分片缺失）：重试必然同样结果，
        // 原地退避三次只是把失败推迟几秒；有进展的做法是整链续传（重新 init + 只补缺片）
        server.failChunk(index = 1, httpStatus = 400, businessCode = 13002, times = 1)

        uploader.upload(source()) { }

        assertEquals("不可重试的失败不该原地重试，应当整链续传一次", 2, server.initCount)
        assertEquals("第 1 片共请求两次：每次链尝试各一次", 2, server.chunkRequests(1))
        assertEquals("最终仍然要合并", 1, server.completeCount.get())
    }

    // ───────────────────────── 续传与秒传 ─────────────────────────

    @Test
    fun `resume only uploads the missing chunks`() = runBlocking {
        // 服务端已经有 0、2 两片（上次中断留下的）
        server.uploadedOnServer += setOf(0, 2)
        val progress = Collections.synchronizedList(mutableListOf<Int>())

        uploader.upload(source()) { progress += it }

        assertEquals(
            "只补缺失的片，已落盘的一个字节都不重传",
            (0 until TOTAL_CHUNKS).toSet() - setOf(0, 2), server.receivedChunkIndexes()
        )
        assertEquals("续传不该新建会话", 1, server.initCount)
        assertEquals("续传后仍要合并", 1, server.completeCount.get())
        assertEquals("续传也要把进度走完", 100, progress.last())
    }

    @Test
    fun `instant upload skips every chunk`() = runBlocking {
        server.skipUpload = true
        val progress = Collections.synchronizedList(mutableListOf<Int>())

        val path = uploader.upload(source()) { progress += it }

        assertEquals("秒传命中不该传任何分片", emptySet<Int>(), server.receivedChunkIndexes())
        assertEquals("仍然要调 complete 拿到服务端已存的文件路径", 1, server.completeCount.get())
        assertEquals("秒传直接就是 100%", 100, progress.last())
        assertEquals("2026-09-22/u1.mp4", path)
    }

    // ───────────────────────── 请求契约 ─────────────────────────

    @Test
    fun `init request carries the digest size and a self consistent chunk contract`() = runBlocking {
        val src = source()

        uploader.upload(src) { }

        val init = server.initBodies.single()
        assertTrue("必须带上文件大小", init.contains(""""fileSize":$FILE_SIZE"""))
        assertTrue("必须带上指纹（服务端的秒传/续传靠它）", init.contains(src.digest()))
        assertTrue("必须提议分片大小（服务端要校验它落在允许区间）", init.contains(""""chunkSize":$CHUNK_SIZE"""))
        assertTrue(
            "分片数必须与文件大小/分片大小自洽（服务端会校验，对不上直接 400）",
            init.contains(""""totalChunks":$TOTAL_CHUNKS""")
        )
    }

    // ───────────────────────── 本地账本（阶段 3）─────────────────────────

    @Test
    fun `reuses the cached digest instead of re-reading the whole file`() = runBlocking {
        // 本地已有一条同文件的会话（上次上传中断留下的）：指纹应当直接命中，
        // 而不是为了续传把 1GB 的视频重读一遍算 MD5
        val src = source()
        store.seed(
            UploadSessionRecord(
                // 用字面量而不是 src.digest()：后者会把"算过几次"的计数变成 1，断言就失去意义了
                uri = src.key, fileName = src.name, fileSize = src.size, fileMd5 = "cached-digest",
                chunkSize = CHUNK_SIZE, totalChunks = TOTAL_CHUNKS, uploadId = "old",
                status = UploadSessionStatus.UPLOADING
            )
        )

        uploader.upload(src) { }

        assertEquals("指纹必须命中缓存，不该再读一遍文件", 0, src.digestCalls)
    }

    @Test
    fun `recomputes the digest when the cached session is for a different size`() = runBlocking {
        // 同一个 URI 指向的文件被换过（重新录制/编辑）：大小变了就不能信那条缓存
        val src = source()
        store.seed(
            UploadSessionRecord(
                uri = src.key, fileName = src.name, fileSize = src.size + 1, fileMd5 = "stale",
                chunkSize = CHUNK_SIZE, totalChunks = TOTAL_CHUNKS, uploadId = "old", status = UploadSessionStatus.UPLOADING
            )
        )

        uploader.upload(src) { }

        assertEquals("大小对不上必须重算指纹", 1, src.digestCalls)
    }

    @Test
    fun `a local chunk marked done but missing on the server is uploaded again`() = runBlocking {
        // "本地以为传完了、服务端没有"——最危险的一种不一致。
        // 上传器必须让它真的重传，而不是跳过它然后等合并时才发现少一片
        val src = source()
        store.seed(
            UploadSessionRecord(
                uri = src.key, fileName = src.name, fileSize = src.size, fileMd5 = src.digest(),
                chunkSize = CHUNK_SIZE, totalChunks = TOTAL_CHUNKS, uploadId = "old", status = UploadSessionStatus.UPLOADING
            ),
            ledger = newLedger(src.key, allDone = setOf(0, 1, 2, 3, 4))
        )

        uploader.upload(src) { }

        assertEquals(
            "本地账本说全传完了，但服务端一片都没有 ⇒ 五片都得重传",
            (0 until TOTAL_CHUNKS).toSet(), server.receivedChunkIndexes()
        )
    }

    @Test
    fun `ledger is reconciled into the store before uploading`() = runBlocking {
        server.uploadedOnServer += setOf(0, 2)

        uploader.upload(source()) { }

        assertEquals("对账结果要落库（进页面时进度要能立刻画出来）", 1, store.replacePartsCalls)
    }

    @Test
    fun `ledger is cleared after a successful upload`() = runBlocking {
        val src = source()

        uploader.upload(src) { }

        assertEquals(
            "传完了账本就该清掉，否则下次进发布页还会提示「有一条没传完」",
            0, store.partsOf(src.key).size
        )
        assertEquals("会话本身也要清", null, store.getSession(src.key))
    }

    @Test
    fun `cancelling the upload aborts the server session`() = runBlocking {
        val src = source()
        val job = launch(Dispatchers.IO) { uploader.upload(src) { } }
        try {
            // 等到至少一片已经到服务端：此时会话已经建好、uploadId 已落本地。
            // ★ 必须有界等待：写成 `while (空) delay(10)` 时，如果上传在第一步就失败，
            // 这个循环会永远转下去——表现是"测试挂死"，而不是"测试失败"（本项目刚踩过）
            withTimeout(10_000) {
                while (server.receivedChunkIndexes().isEmpty()) delay(10)
            }
        } catch (timeout: TimeoutCancellationException) {
            job.cancelAndJoin()
            throw AssertionError("等不到任何分片到达服务端，说明上传在第一步就失败了", timeout)
        }

        job.cancelAndJoin()

        assertEquals("取消后要主动收掉服务端会话，别让分片占盘等 24 小时清理", 1, server.abortCount.get())
    }

    @Test
    fun `abort reaches the server and swallows its failure`() = runBlocking {
        // 放弃上传是"我已经不要了"：清理失败不该让用户看到一个报错
        server.failAbort = true

        uploader.abort("u1")

        assertEquals("abort 要真的发出去", 1, server.abortCount.get())
    }
}

// ───────────────────────── 测试脚手架 ─────────────────────────

/**
 * 假服务端。与真服务端的三点关键一致——**否则测出来的"通过"没有意义**：
 *
 * 1. **校验分片契约**：`totalChunks` 与 `ceil(fileSize/chunkSize)` 不符就 400（13005）
 * 2. **记住已落盘的分片**：`init` 返回的 `uploadedChunks` 就是"磁盘上真有的那些"，
 *    于是"续传只补缺片"是被真的走了一遍，而不是靠测试自己塞一个集合糊过去
 * 3. **HTTP 状态码与业务码分开**：5xx 是 HTTP 500（可重试），业务失败是 HTTP 400 + 业务码
 *    （不可重试）——这决定了客户端是原地重试还是整链续传，混在一起就测不出策略差别
 */
private class UploadServer {

    private val server = MockWebServer()
    val url: String get() = server.url("/").toString()

    var skipUpload: Boolean = false
    var failAbort: Boolean = false

    /** 服务端"磁盘上已落盘"的分片。测试可预置，上传成功也会往里加 */
    val uploadedOnServer: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())

    val initBodies = Collections.synchronizedList(mutableListOf<String>())
    val initCount: Int get() = initBodies.size
    val completeCount = AtomicInteger()
    val abortCount = AtomicInteger()

    private val chunkHits = mutableMapOf<Int, Int>()
    private val chunkPayloads = mutableMapOf<Int, ByteArray>()
    private val failures = mutableMapOf<Int, Failure>()
    private val inFlight = AtomicInteger()

    @Volatile
    var maxInFlightChunks: Int = 0
        private set

    private data class Failure(val httpStatus: Int, val businessCode: Int, var remaining: Int)

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readByteArray()
                return when {
                    path.startsWith("/api/file/upload/init") -> handleInit(body)
                    path.startsWith("/api/file/upload/chunk") -> handleChunk(path, body)
                    path.startsWith("/api/file/upload/complete") -> {
                        completeCount.incrementAndGet()
                        ok(""""2026-09-22/u1.mp4"""")
                    }
                    path.startsWith("/api/file/upload/abort") -> {
                        abortCount.incrementAndGet()
                        if (failAbort) MockResponse().setResponseCode(500) else ok("null")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    fun shutdown() = server.shutdown()

    fun receivedChunkIndexes(): Set<Int> = synchronized(chunkHits) { chunkHits.keys.toSet() }

    fun chunkRequests(index: Int): Int = synchronized(chunkHits) { chunkHits[index] ?: 0 }

    /** 取回某一片的**裸内容**（去掉 multipart 的头部与结尾边界），用于逐字节比对 */
    fun partContent(index: Int): ByteArray = synchronized(chunkPayloads) { chunkPayloads.getValue(index) }

    fun failChunk(index: Int, httpStatus: Int, businessCode: Int = 14999, times: Int = 1) {
        failures[index] = Failure(httpStatus, businessCode, times)
    }

    private fun handleInit(body: ByteArray): MockResponse {
        val json = String(body, Charsets.UTF_8)
        initBodies += json
        val fileSize = Regex(""""fileSize":(\d+)""").find(json)!!.groupValues[1].toLong()
        val chunkSize = Regex(""""chunkSize":(\d+)""").find(json)!!.groupValues[1].toLong()
        val totalChunks = Regex(""""totalChunks":(\d+)""").find(json)!!.groupValues[1].toInt()

        // 与真服务端同一条校验：分片数必须与"文件大小 / 分片大小"自洽
        val expected = ((fileSize + chunkSize - 1) / chunkSize).toInt()
        if (totalChunks != expected) {
            return MockResponse().setResponseCode(400)
                .setBody("""{"code":13005,"message":"分片数与分片大小不一致","ttl":0,"data":null}""")
        }

        val uploaded = synchronized(uploadedOnServer) { uploadedOnServer.sorted() }
        return ok(
            """{"uploadId":"u1","skipUpload":$skipUpload,"uploadedChunks":[${
                uploaded.joinToString(",")
            }],"chunkSize":$chunkSize}"""
        )
    }

    private fun handleChunk(path: String, body: ByteArray): MockResponse {
        val index = Regex("chunkIndex=(\\d+)").find(path)!!.groupValues[1].toInt()
        val current = inFlight.incrementAndGet()
        maxInFlightChunks = maxOf(maxInFlightChunks, current)
        try {
            // 停留一会儿：不停留的话请求会串行完成，"并发度"就永远测不出真值
            Thread.sleep(50)
            synchronized(chunkHits) { chunkHits[index] = (chunkHits[index] ?: 0) + 1 }

            val failure = failures[index]
            if (failure != null && failure.remaining > 0) {
                failure.remaining--
                return MockResponse().setResponseCode(failure.httpStatus)
                    .setBody("""{"code":${failure.businessCode},"message":"测试用失败","ttl":0,"data":null}""")
            }

            synchronized(chunkPayloads) { chunkPayloads[index] = extractPartContent(body) }
            uploadedOnServer += index
            return ok("null")
        } finally {
            inFlight.decrementAndGet()
        }
    }

    /** 单部件 multipart：内容在两个空行之后、结尾边界之前 */
    private fun extractPartContent(body: ByteArray): ByteArray {
        val headerEnd = indexOf(body, HEADER_END) + HEADER_END.size
        val boundaryStart = lastIndexOf(body, BOUNDARY_PREFIX)
        return body.copyOfRange(headerEnd, boundaryStart)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        for (i in haystack.size - needle.size downTo 0) {
            var match = true
            for (j in needle.indices) if (haystack[i + j] != needle[j]) {
                match = false
                break
            }
            if (match) return i
        }
        return -1
    }

    private fun ok(data: String) =
        MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json; charset=UTF-8")
            .setBody("""{"code":0,"message":"success","ttl":0,"data":$data}""")

    private companion object {
        val HEADER_END = "\r\n\r\n".toByteArray()
        val BOUNDARY_PREFIX = "\r\n--".toByteArray()
    }
}

/**
 * [ChunkUploadRemote] 的真实实现：真 Retrofit + 真 OkHttp，只是指向 MockWebServer。
 *
 * 之所以在测试里自己拼一遍 Retrofit：生产实现走 `ApiGateway`，而后者依赖
 * Android Context / DataStore / TokenHolder，纯 JVM 测试里造不出来。
 * 换成"让数据源 open 好继承"或"把 Retrofit 从生产实现里抽出来"都是为了测试改生产结构，
 * 而这一层本来就有"窄接口 + 实现"的正当理由（见 [ChunkUploadRemote]）。
 */
private class RealHttpChunkRemote(baseUrl: String) : ChunkUploadRemote {

    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PublishApi::class.java)

    override suspend fun initUpload(body: UploadInitRequestDto) = apiCall { api.initUpload(body) }

    override suspend fun uploadChunk(uploadId: String, chunkIndex: Int, part: MultipartBody.Part) =
        apiUnitCall { api.uploadChunk(uploadId, chunkIndex, part) }

    override suspend fun completeUpload(uploadId: String) = apiCall { api.completeUpload(uploadId) }

    override suspend fun listParts(uploadId: String) = apiCall { api.listParts(uploadId) }

    override suspend fun abortUpload(uploadId: String) = apiUnitCall { api.abortUpload(uploadId) }
}
