package com.example.bluebook.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.example.bluebook.common.JwtUtil
import com.example.bluebook.file.entity.UploadSession
import com.example.bluebook.file.entity.UploadStatus
import com.example.bluebook.file.repository.UploadSessionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import java.io.File
import java.security.MessageDigest

/**
 * 分片上传接口的集成测试（真 Spring 上下文 + H2 + MockMvc）。
 *
 * 这是本项目**第一个覆盖文件上传的测试**——此前这条链一个用例都没有，
 * 而它恰好是"错了也不崩、只是白传一遍"的那一类：分片数对不上、分片越界、
 * 续传时契约变了、别人的会话能写、缺片却拼出一个残缺文件……都不会抛异常给你看。
 *
 * ## 三个测试上的坑（都已在下面处理，别人接手时不必再踩）
 *
 * 1. **这些接口要求登录**：`/api/file/` 不在免鉴权名单里，而 `JwtAuthFilter` 对非 GET 请求
 *    强制要 token，所以不能用 `addFilters = false` 躲过去（那会让 `currentUserId()` 恒为 0，
 *    归属校验就没法测了）。这里**真的签一个 JWT**，让请求走完整条鉴权链。
 * 2. **Redis 是 @MockBean**：`JwtAuthFilter` 要查 token 黑名单，不 stub `opsForValue()`
 *    会在过滤器里 NPE（表现为 500，与真正的原因毫无关系）。用户上传路径本身**不再碰 Redis**
 *    （见 `ChunkUploadService` 的类注释），所以只需这一处 stub。
 * 3. **分片大小有下限（1MB）**：`chunkSize` 会被钳到 `[1MB, 8MB]`，所以测试里的"小文件"
 *    最小也得是 1MB 级——3MB 文件按 1MB 切片正好 3 片，其中最后一片是零头，一次覆盖两条路径。
 *
 * 存储目录用 `build/test-upload`（在模块构建目录里，且每次跑前清空），不污染 `./upload`。
 */
@SpringBootTest(properties = ["app.upload.storage-path=build/test-upload"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadApiTest {

    @MockBean
    lateinit var stringRedisTemplate: StringRedisTemplate

    @MockBean
    lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtUtil: JwtUtil

    @Autowired
    lateinit var uploadSessionRepository: UploadSessionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private companion object {
        const val STORAGE = "build/test-upload"
        const val ONE_MB = 1024L * 1024L

        /** 2MB + 12345 字节按 1MB 切片 = 3 片，最后一片是零头（顺带覆盖"末片可小于分片大小"） */
        const val FILE_SIZE = 2 * ONE_MB + 12345
        const val CHUNK_SIZE = ONE_MB

        const val ALICE_ID = 1001L
        const val BOB_ID = 1002L
    }

    private lateinit var aliceToken: String
    private lateinit var bobToken: String

    /** 文件内容：按片填充可区分的字节，便于断言拼接顺序 */
    private val content: ByteArray = ByteArray(FILE_SIZE.toInt()) { (it % 251).toByte() }
    private val contentMd5: String = md5(content)

    @BeforeEach
    fun setUp() {
        File(STORAGE).deleteRecursively()
        uploadSessionRepository.deleteAll()
        aliceToken = jwtUtil.generateAccessToken(ALICE_ID, "13800000001")
        bobToken = jwtUtil.generateAccessToken(BOB_ID, "13800000002")
        stubRedisForJwtFilter()
    }

    @AfterEach
    fun tearDown() {
        File(STORAGE).deleteRecursively()
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubRedisForJwtFilter() {
        val valueOps = mock(ValueOperations::class.java) as ValueOperations<String, String>
        // 黑名单里没有这个 jti ⇒ token 有效
        `when`(valueOps.get(anyString())).thenReturn(null)
        `when`(stringRedisTemplate.opsForValue()).thenReturn(valueOps)
    }

    // ───────────────────────── 请求辅助 ─────────────────────────

    private fun initRequest(
        md5: String = contentMd5,
        fileSize: Long = FILE_SIZE,
        totalChunks: Int = 3,
        chunkSize: Long? = CHUNK_SIZE,
        fileName: String = "a.mp4"
    ): String = buildString {
        append("""{"fileName":"$fileName","fileSize":$fileSize,"fileMd5":"$md5","totalChunks":$totalChunks""")
        if (chunkSize != null) append(""","chunkSize":$chunkSize""")
        append("}")
    }

    private fun init(body: String, token: String = aliceToken) = mockMvc.post("/api/file/upload/init") {
        contentType = MediaType.APPLICATION_JSON
        content = body
        header("Authorization", "Bearer $token")
    }

    private fun uploadChunk(
        uploadId: String,
        index: Int,
        bytes: ByteArray,
        token: String = aliceToken,
        partMd5: String? = md5(bytes)
    ) = mockMvc.multipart("/api/file/upload/chunk") {
        param("uploadId", uploadId)
        param("chunkIndex", index.toString())
        // 默认带上正确的分片指纹（新客户端都会带）；传 null 模拟老客户端
        if (partMd5 != null) param("partMd5", partMd5)
        file(MockMultipartFile("file", "chunk$index", "application/octet-stream", bytes))
        header("Authorization", "Bearer $token")
    }

    private fun complete(uploadId: String, token: String = aliceToken) =
        mockMvc.post("/api/file/upload/complete") {
            param("uploadId", uploadId)
            header("Authorization", "Bearer $token")
        }

    private fun abort(uploadId: String, token: String = aliceToken) =
        mockMvc.post("/api/file/upload/abort") {
            param("uploadId", uploadId)
            header("Authorization", "Bearer $token")
        }

    /** 一个会话从 init 到"所有分片已上传"的完整过程，返回 uploadId */
    private fun uploadAllChunks(token: String = aliceToken): String {
        val uploadId = initJson(initRequest(), token)["uploadId"]!!
        for (index in 0 until 3) {
            uploadChunk(uploadId, index, chunkBytes(index), token).andExpect { status { isOk() } }
        }
        return uploadId
    }

    /** 发一个 init 并取回响应里的 data 字段（用 Jackson 解析，不拿正则啃 JSON） */
    private fun initJson(body: String, token: String = aliceToken): Map<String, String> {
        val json = init(body, token)
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val data = objectMapper.readTree(json)["data"]
        return mapOf(
            "uploadId" to data["uploadId"].asText(),
            "chunkSize" to data["chunkSize"].asText()
        )
    }

    /** 第 index 片的内容（最后一片是零头） */
    private fun chunkBytes(index: Int): ByteArray {
        val from = (index * CHUNK_SIZE).toInt()
        val to = minOf(from + CHUNK_SIZE.toInt(), content.size)
        return content.copyOfRange(from, to)
    }

    // ───────────────────────── init：分片契约 ─────────────────────────

    @Test
    fun `init returns the effective chunk size the client must use`() {
        init(initRequest()).andExpect {
            status { isOk() }
            jsonPath("$.data.chunkSize") { value(CHUNK_SIZE) }
            jsonPath("$.data.skipUpload") { value(false) }
        }
    }

    @Test
    fun `init falls back to default chunk size when client omits it`() {
        // 缺省 2MB：老版本客户端不会带 chunkSize，行为必须与改动前一致
        init(initRequest(chunkSize = null, totalChunks = 2, fileSize = 2 * 2 * ONE_MB)).andExpect {
            status { isOk() }
            jsonPath("$.data.chunkSize") { value(2 * ONE_MB) }
        }
    }

    @Test
    fun `init rejects a chunk size outside the allowed range`() {
        // 明确的拒绝，而不是静默钳制：客户端在发请求前就得算好 totalChunks，
        // 它无从知道服务端会钳成多少——钳制 + 一致性校验并存的话，
        // 越界的提议只会换来一个它无法自救的 400，钳制形同死代码
        init(initRequest(chunkSize = 64 * ONE_MB, totalChunks = 1, fileSize = ONE_MB)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
        init(initRequest(chunkSize = 4096, totalChunks = 1, fileSize = ONE_MB)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
    }

    @Test
    fun `init rejects chunks count inconsistent with size and chunk size`() {
        // 3MB 文件按 1MB 切片应为 3 片，客户端说 5 片 → 拒绝，而不是合并时才发现
        init(initRequest(totalChunks = 5)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
    }

    @Test
    fun `init rejects files beyond the configured maximum`() {
        // app.upload.video-max-size = 100MB，这条配置在改动前是死的（没有任何代码读它）
        val tooBig = 101L * 1024 * 1024
        init(initRequest(fileSize = tooBig, totalChunks = 101)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13001) }
        }
    }

    @Test
    fun `init reports skip upload for an already merged file`() {
        uploadSessionRepository.save(
            UploadSession(
                id = "done-session", userId = ALICE_ID, fileName = "a.mp4",
                fileSize = FILE_SIZE, fileMd5 = contentMd5, totalChunks = 3,
                chunkSize = CHUNK_SIZE, status = UploadStatus.DONE
            )
        )

        init(initRequest()).andExpect {
            status { isOk() }
            jsonPath("$.data.skipUpload") { value(true) }
            jsonPath("$.data.uploadId") { value("done-session") }
        }
    }

    // ───────────────────────── 续传与自愈 ─────────────────────────

    @Test
    fun `resume returns the same session and its uploaded chunks`() {
        val uploadId = initJson(initRequest())["uploadId"]!!
        uploadChunk(uploadId, 0, chunkBytes(0)).andExpect { status { isOk() } }
        uploadChunk(uploadId, 1, chunkBytes(1)).andExpect { status { isOk() } }

        init(initRequest()).andExpect {
            status { isOk() }
            jsonPath("$.data.uploadId") { value(uploadId) }
            jsonPath("$.data.uploadedChunks[0]") { value(0) }
            jsonPath("$.data.uploadedChunks[1]") { value(1) }
        }
    }

    @Test
    fun `resume with a changed chunk contract discards the old session instead of failing later`() {
        // App 升级改了分片大小：旧分片按旧大小落盘，与新分片混拼必然拼错。
        // 改动前这里会复用旧会话，然后在 complete 时报"分片缺失"——文案指向"你少传了片"，
        // 与真正的原因（契约变了）毫不相干。现在应当作废重建。
        val oldUploadId = initJson(initRequest())["uploadId"]!!
        uploadChunk(oldUploadId, 0, chunkBytes(0)).andExpect { status { isOk() } }

        val newChunkSize = 2 * ONE_MB
        val newTotalChunks = 2 // 2MB+12345 字节按 2MB 切片 = 2 片
        val newUploadId = initJson(
            initRequest(chunkSize = newChunkSize, totalChunks = newTotalChunks)
        )["uploadId"]!!

        assertFalse(newUploadId == oldUploadId, "分片契约变了就该换一个会话，而不是复用")
        assertTrue(
            uploadSessionRepository.findById(oldUploadId).isEmpty,
            "旧会话必须被作废（否则它的分片目录再没人认领，只能等过期清理）"
        )
        assertFalse(
            File("$STORAGE/chunks/$oldUploadId").exists(),
            "旧会话的分片目录也要删掉"
        )
        init(initRequest(chunkSize = newChunkSize, totalChunks = newTotalChunks)).andExpect {
            // 新会话是干净的：没有"继承"旧分片
            jsonPath("$.data.uploadedChunks") { isEmpty() }
        }
    }

    // ───────────────────────── 分片边界 ─────────────────────────

    @Test
    fun `upload chunk rejects an out of range index`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 3, chunkBytes(0)).andExpect {
            // 本次共 3 片，合法序号是 0..2
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
        uploadChunk(uploadId, -1, chunkBytes(0)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
        assertFalse(
            File("$STORAGE/chunks/$uploadId/3").exists(),
            "越界的分片不该落盘（改动前能往 9999 或负数写文件，永久占盘且永远不参与合并）"
        )
    }

    @Test
    fun `upload chunk rejects a part larger than the agreed chunk size`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, ByteArray((CHUNK_SIZE + 1).toInt())).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
    }

    @Test
    fun `upload chunk rejects an empty part`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, ByteArray(0)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13005) }
        }
    }


    // ───────────────────────── 分片指纹 ─────────────────────────

    @Test
    fun `a part whose checksum does not match is rejected and not recorded`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, chunkBytes(0), partMd5 = "0".repeat(32)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13006) }
            jsonPath("$.message") { value("分片校验失败，请重传该分片") }
        }

        assertFalse(
            File("$STORAGE/chunks/$uploadId/0").exists(),
            "校验失败的分片不能留在磁盘上（否则它会被当成已上传，最后只能靠整体 MD5 才发现）"
        )
        assertFalse(
            File("$STORAGE/chunks/$uploadId/0.tmp").exists(),
            "临时文件也要清掉"
        )
        // 拒收之后这一片不算已传：客户端重传它即可，不必整链重来
        assertTrue(
            uploadSessionRepository.findById(uploadId).isPresent,
            "被拒的分片不该让会话消失（客户端只是重传这一片）"
        )
    }

    @Test
    fun `a part with a correct checksum is accepted`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, chunkBytes(0)).andExpect {
            status { isOk() }
            jsonPath("$.code") { value(0) }
        }

        assertTrue(File("$STORAGE/chunks/$uploadId/0").exists(), "校验通过的分片要落盘")
    }

    @Test
    fun `a part without checksum is still accepted for older clients`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, chunkBytes(0), partMd5 = null).andExpect {
            status { isOk() }
            jsonPath("$.code") { value(0) }
        }
    }

    // ───────────────────────── 归属校验 ─────────────────────────

    @Test
    fun `another user cannot write to someone else's session`() {
        val uploadId = initJson(initRequest())["uploadId"]!!

        uploadChunk(uploadId, 0, chunkBytes(0), token = bobToken).andExpect {
            // uploadId 是 UUID 不易猜，但"不易猜"不是权限模型
            status { isForbidden() }
            jsonPath("$.code") { value(14001) }
        }
        assertFalse(File("$STORAGE/chunks/$uploadId/0").exists(), "别人的分片一个字节都不该落盘")
    }

    @Test
    fun `another user cannot complete list or abort someone else's session`() {
        val uploadId = uploadAllChunks()

        complete(uploadId, token = bobToken).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(14001) }
        }
        abort(uploadId, token = bobToken).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(14001) }
        }
        assertTrue(
            uploadSessionRepository.findById(uploadId).isPresent,
            "被拒绝的操作不能有副作用"
        )
    }

    // ───────────────────────── 合并 ─────────────────────────

    @Test
    fun `complete merges the chunks in order and cleans up`() {
        val uploadId = uploadAllChunks()

        val result = complete(uploadId).andExpect {
            status { isOk() }
            jsonPath("$.code") { value(0) }
        }.andReturn()

        val path = objectMapper.readTree(result.response.contentAsString)["data"].asText()
        val merged = File("$STORAGE/videos/$path")
        assertTrue(merged.exists(), "合并产物应当存在：$path")
        assertArrayEqualsByDigest(content, merged.readBytes())
        assertEquals(UploadStatus.DONE, uploadSessionRepository.findById(uploadId).get().status)
        assertFalse(File("$STORAGE/chunks/$uploadId").exists(), "合并成功后分片目录要清掉")
    }

    @Test
    fun `complete refuses when a chunk is missing`() {
        val uploadId = initJson(initRequest())["uploadId"]!!
        uploadChunk(uploadId, 0, chunkBytes(0)).andExpect { status { isOk() } }

        complete(uploadId).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13002) }
        }
    }

    @Test
    fun `complete refuses when a chunk vanished from disk`() {
        // 这条是"判据只有一个真相"的回归：改动前完整性的判据是 Redis、而拼接读的是磁盘，
        // 于是"Redis 说齐了、磁盘缺一片"会拼出一个长度合理但内容残缺的文件
        // （只能靠最后的整体 MD5 拦住，报的还是"文件校验失败"）。
        // 现在判据就是磁盘本身，缺片直接报缺片。
        val uploadId = uploadAllChunks()
        File("$STORAGE/chunks/$uploadId/1").delete()

        complete(uploadId).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13002) }
        }
    }

    @Test
    fun `complete detects a file whose content does not match the declared md5`() {
        // init 时声明的 MD5 与实际上传的内容不符（客户端算错/内容被改）
        val uploadId = initJson(initRequest(md5 = "0".repeat(32)))["uploadId"]!!
        for (index in 0 until 3) {
            uploadChunk(uploadId, index, chunkBytes(index)).andExpect { status { isOk() } }
        }

        complete(uploadId).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(13004) }
        }
        // 校验失败的产物不能留在 videos/ 里冒充正常文件
        val leftovers = File("$STORAGE/videos").walkTopDown()
            .filter { it.isFile && it.name.startsWith(uploadId) }
            .toList()
        assertTrue(leftovers.isEmpty(), "校验失败要删掉合并产物，实际残留：$leftovers")
    }

    // ───────────────────────── listParts 与 abort ─────────────────────────

    @Test
    fun `list parts returns the authoritative chunk list and contract`() {
        val uploadId = initJson(initRequest())["uploadId"]!!
        uploadChunk(uploadId, 0, chunkBytes(0)).andExpect { status { isOk() } }
        uploadChunk(uploadId, 2, chunkBytes(2)).andExpect { status { isOk() } }

        mockMvc.get("/api/file/upload/parts") {
            param("uploadId", uploadId)
            header("Authorization", "Bearer $aliceToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.uploadedChunks[0]") { value(0) }
            jsonPath("$.data.uploadedChunks[1]") { value(2) }
            jsonPath("$.data.chunkSize") { value(CHUNK_SIZE) }
            jsonPath("$.data.totalChunks") { value(3) }
            jsonPath("$.data.fileSize") { value(FILE_SIZE) }
        }
    }

    @Test
    fun `abort deletes the session and its chunks immediately`() {
        val uploadId = uploadAllChunks()

        abort(uploadId).andExpect {
            status { isOk() }
            jsonPath("$.code") { value(0) }
        }

        assertTrue(uploadSessionRepository.findById(uploadId).isEmpty, "会话行要删掉")
        assertFalse(File("$STORAGE/chunks/$uploadId").exists(), "分片目录要立刻释放，不必等过期清理")
    }

    @Test
    fun `abort refuses a completed session`() {
        // 已完成会话的合并产物是用户已经发布的视频，删它就是删用户的内容
        val uploadId = uploadAllChunks()
        complete(uploadId).andExpect { status { isOk() } }

        abort(uploadId).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(14001) }
        }
        assertTrue(uploadSessionRepository.findById(uploadId).isPresent)
    }
}

private fun md5(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

private fun assertArrayEqualsByDigest(expected: ByteArray, actual: ByteArray) {
    // JUnit 5 的签名是 assertEquals(expected, actual, message)——消息在**最后**，
    // 与 JUnit 4（消息在第一个）相反。本项目两种并存（Android 模块用 4、:backend 用 5）
    assertEquals(expected.size, actual.size, "长度也要一致")
    assertEquals(
        md5(expected), md5(actual),
        "合并产物必须与原始内容逐字节相同（按 MD5 比对，避免把 3MB 数组打进断言消息）"
    )
}
