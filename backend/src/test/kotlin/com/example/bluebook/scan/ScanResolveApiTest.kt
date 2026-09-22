package com.example.bluebook.scan

import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.video.entity.TranscodeStatus
import com.example.bluebook.video.entity.Video
import com.example.bluebook.video.entity.VideoStatus
import com.example.bluebook.video.repository.VideoRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * `GET /api/v2/scan/resolve` 的**接口级测试**（真 Spring 上下文 + H2）。
 *
 * 这是设计方案 4.5 第 4 步「curl 各一次」的自动化版本，也是更有用的那一版：
 * curl 只能证明"这一台、这一次"是对的，而这组用例把 6.2 契约里的每个状态码都钉成了
 * 可重复的断言——**包括中文文案**。文案不是装饰：客户端把它原样显示给用户，
 * 改文案等于改用户看到的话，所以它值得被测试，而不是只写在文档里。
 *
 * Redis / RabbitMQ 用 @MockBean 顶掉（与 `BlueBookApplicationTests` 同一手法）：
 * 本接口不碰它们，没必要为跑测试起中间件。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanResolveApiTest {

	@MockBean
	lateinit var stringRedisTemplate: StringRedisTemplate

	@MockBean
	lateinit var rabbitTemplate: RabbitTemplate

	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var videoRepository: VideoRepository

	@Autowired
	lateinit var userRepository: UserRepository

	private lateinit var author: User
	private lateinit var published: Video
	private lateinit var reviewing: Video
	private lateinit var deleted: Video

	@BeforeEach
	fun setUp() {
		// 手机号是唯一约束（length=20），用自增计数而不是时间戳，避免同一毫秒内撞号
		author = userRepository.save(
			User(
				phone = "1380000%04d".format(phoneSeq++),
				nickname = "扫码测试作者",
				passwordHash = "x",
				bio = "简介"
			)
		)
		published = videoRepository.save(newVideo(VideoStatus.PUBLISHED))
		reviewing = videoRepository.save(newVideo(VideoStatus.REVIEWING))
		deleted = videoRepository.save(newVideo(VideoStatus.DELETED))
	}

	private fun newVideo(status: VideoStatus) = Video(
		uploaderId = author.id,
		title = "标题",
		description = "描述",
		coverUrl = "2026-09-22/cover.jpg",
		hlsUrl = "2026-09-22/index.m3u8",
		transcodeStatus = TranscodeStatus.DONE,
		status = status
	)

	private fun resolve(payload: String) = mockMvc.get("/api/v2/scan/resolve") {
		param("payload", payload)
	}

	// ───────────── 成功路径 ─────────────

	@Test
	fun `resolves published video code`() {
		resolve(ScanCodeFormat.videoUrl(published.id))
			.andExpect {
				status { isOk() }
				jsonPath("$.code") { value(0) }
				jsonPath("$.data.type") { value("VIDEO") }
				jsonPath("$.data.targetId") { value(published.id) }
				jsonPath("$.data.title") { value("标题") }
				// 作者昵称作副标题：扫码结果与播放页都要显示"这是谁的视频"
				jsonPath("$.data.subtitle") { value("扫码测试作者") }
				// 封面下发**相对路径**，由客户端拼 BASE_URL——与 /videos/*/dto 完全一致，
				// 免得同一个封面在两套接口里一个是绝对地址一个是相对地址
				jsonPath("$.data.cover") { value("/hls/2026-09-22/cover.jpg") }
			}
	}

	/**
	 * **逐字节钉住响应体的形状**（`strict = true`：多一个字段、少一个字段、改一个名字都会失败）。
	 *
	 * 上面那条用例逐个 `jsonPath` 断言，只保证"这些路径存在且值对"——**字段被改名时它不会响**
	 * （客户端 Gson 读到 null，映射失败，用户看到"暂不支持该二维码"，而服务端测试全绿）。
	 * 跨语言契约必须从两侧各钉一次：这边钉住**产出的名字**，
	 * 客户端 `ScanResolveDtoTest` 钉住**消费的名字**，两个名字的来源是同一段 JSON。
	 *
	 * 改这段 JSON 就同时改那个测试——它们是一份契约的两半。
	 */
	@Test
	fun `response body shape is pinned for the client contract`() {
		mockMvc.get("/api/v2/scan/resolve") {
			param("payload", ScanCodeFormat.videoUrl(published.id))
		}.andExpect {
			status { isOk() }
			content {
				json(
					"""
					{
					  "code": 0,
					  "message": "success",
					  "ttl": 0,
					  "data": {
					    "type": "VIDEO",
					    "targetId": ${published.id},
					    "title": "标题",
					    "subtitle": "扫码测试作者",
					    "cover": "/hls/2026-09-22/cover.jpg"
					  }
					}
					""".trimIndent(),
					true
				)
			}
		}
	}

	@Test
	fun `resolves user code`() {
		resolve(ScanCodeFormat.userUrl(author.id))
			.andExpect {
				status { isOk() }
				jsonPath("$.code") { value(0) }
				jsonPath("$.data.type") { value("USER") }
				jsonPath("$.data.targetId") { value(author.id) }
				jsonPath("$.data.title") { value("扫码测试作者") }
			}
	}

	@Test
	fun `falls back to description when title is blank`() {
		val untitled = videoRepository.save(newVideo(VideoStatus.PUBLISHED).apply { title = null })
		resolve(ScanCodeFormat.videoUrl(untitled.id))
			.andExpect {
				status { isOk() }
				// 与客户端 VideoMappers 同一规则：不能下发空标题让扫码结果卡片变成一片空白
				jsonPath("$.data.title") { value("描述") }
			}
	}

	@Test
	fun `tolerates query fragment and trailing slash`() {
		val base = ScanCodeFormat.videoUrl(published.id)
		listOf("$base/", "$base?from=wechat", "$base#top").forEach { payload ->
			resolve(payload).andExpect {
				status { isOk() }
				jsonPath("$.data.targetId") { value(published.id) }
			}
		}
	}

	// ───────────── 失败路径：400 / 403 / 404 ─────────────

	@Test
	fun `rejects non bluebook code as 400`() {
		listOf(
			"https://evil.com/v/1",
			"https://${ScanCodeFormat.CODE_HOST}.evil.com/v/1",
			"https://a.${ScanCodeFormat.CODE_HOST}/v/1",
			"https://${ScanCodeFormat.CODE_HOST}/t/1",
			"javascript:alert(1)",
			"这就是一段普通文本"
		).forEach { payload ->
			resolve(payload).andExpect {
				status { isBadRequest() }
				jsonPath("$.code") { value(15001) }
				jsonPath("$.message") { value("这不是小蓝书的二维码") }
				// 失败时不带任何 data：客户端只认 message
				jsonPath("$.data") { value(nullValue()) }
			}
		}
	}

	@Test
	fun `reports missing video as 404`() {
		resolve(ScanCodeFormat.videoUrl(999_999_999L))
			.andExpect {
				status { isNotFound() }
				jsonPath("$.code") { value(15003) }
				jsonPath("$.message") { value("视频不存在或已被删除") }
			}
	}

	@Test
	fun `reports deleted video as 404`() {
		resolve(ScanCodeFormat.videoUrl(deleted.id))
			.andExpect {
				status { isNotFound() }
				jsonPath("$.code") { value(15003) }
			}
	}

	@Test
	fun `reports reviewing video as 403 not 404`() {
		// 「有但你看不到」与「没有」是两件事：都报 404 会让审核中的内容表现为"已删除"，
		// 用户于是去重新发布，而它其实还在。
		resolve(ScanCodeFormat.videoUrl(reviewing.id))
			.andExpect {
				status { isForbidden() }
				jsonPath("$.code") { value(15002) }
				jsonPath("$.message") { value("该内容暂时无法查看") }
			}
	}

	@Test
	fun `reports unknown user as 404`() {
		resolve(ScanCodeFormat.userUrl(999_999_999L))
			.andExpect {
				status { isNotFound() }
				jsonPath("$.code") { value(15003) }
				jsonPath("$.message") { value("用户不存在") }
			}
	}

	@Test
	fun `rejects malformed input as 400 rather than 500`() {
		// 畸形输入绝不该表现为"服务器繁忙"——那会把客户端入参问题变成服务端告警，
		// 而用户看到的是误导性的文案（与 GlobalExceptionHandler 里那段注释同一个理由）
		listOf(
			"https://${ScanCodeFormat.CODE_HOST}/v/${"9".repeat(2000)}",
			"https://${ScanCodeFormat.CODE_HOST}/v/0",
			"https://${ScanCodeFormat.CODE_HOST}/v/abc",
			"",
			"https://${ScanCodeFormat.CODE_HOST}/v/1" + "x".repeat(2048)
		).forEach { payload ->
			resolve(payload).andExpect {
				status { isBadRequest() }
				jsonPath("$.code") { value(15001) }
			}
		}
	}

	@Test
	fun `missing payload is a client error`() {
		mockMvc.get("/api/v2/scan/resolve")
			.andExpect {
				status { isBadRequest() }
				jsonPath("$.code") { value(14003) }
			}
	}

	// ───────────── 权限：游客可扫 ─────────────

	@Test
	fun `guest can resolve without authorization header`() {
		// 本类所有用例都没带 Authorization 头，本身就证明了这一点；这条把它写成显式断言，
		// 免得将来有人把接口挪出鉴权白名单时无人察觉（游客扫码会静默变成 401）
		resolve(ScanCodeFormat.videoUrl(published.id))
			.andExpect { status { isOk() } }
	}

	private companion object {
		var phoneSeq = 0
	}
}
