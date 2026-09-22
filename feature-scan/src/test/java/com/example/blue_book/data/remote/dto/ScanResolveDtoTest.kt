package com.example.blue_book.data.remote.dto

import com.example.blue_book.data.mapper.toScannedContent
import com.example.blue_book.domain.model.ContentKind
import com.example.blue_book.network.data.ApiResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **用真实响应体**验证客户端 DTO 的字段名。
 *
 * ## 为什么需要这个测试
 *
 * 字段名写错是跨语言契约里最典型的 bug，而它的表现**完全不响**：
 * Gson 读到的是 null（Kotlin 的非空声明拦不住反射反序列化），
 * 映射层再把 null 翻成"暂不支持该二维码"——测试全绿，用户那里永远扫不出来。
 *
 * 上面这段 [REAL_RESPONSE_BODY] 与后端
 * `backend/src/test/kotlin/com/example/bluebook/scan/ScanResolveApiTest.kt` 里
 * `response body shape is pinned for the client contract` 那条用例中的 JSON
 * **逐字相同**（那边用 `strict = true` 钉住产出、这边钉住消费）。
 * 改任何一侧都必须同时改另一侧——它就是契约本身。
 *
 * 解析走的是**真实管线**：`ApiResponse<ScanResolveDto>` 的信封（与 Retrofit +
 * GsonConverterFactory 按方法返回类型反序列化的方式一致），而不是直接喂内层对象——
 * 信封字段名（code/message/ttl/data）同样是契约的一部分。
 */
class ScanResolveDtoTest {

	private val gson = Gson()

	/** 与 Retrofit 一样按泛型返回类型反序列化，而不是拿 raw type 再手工取字段 */
	private fun parse(json: String): ScanResolveDto {
		val type = object : TypeToken<ApiResponse<ScanResolveDto>>() {}.type
		val envelope: ApiResponse<ScanResolveDto> = gson.fromJson(json, type)
		assertEquals("信封的 code 必须是 0", 0, envelope.code)
		return envelope.data ?: throw AssertionError("信封里没有 data，无法继续断言")
	}

	@Test
	fun `parses the real backend response body`() {
		val dto = parse(REAL_RESPONSE_BODY)

		assertEquals("type 字段名必须与后端一致", "VIDEO", dto.type)
		assertEquals("targetId 字段名必须与后端一致", 7L, dto.targetId)
		assertEquals("title 字段名必须与后端一致", "标题", dto.title)
		assertEquals("subtitle 字段名必须与后端一致", "扫码测试作者", dto.subtitle)
		assertEquals("cover 字段名必须与后端一致", "/hls/2026-09-22/cover.jpg", dto.cover)
	}

	@Test
	fun `real response body maps to domain content end to end`() {
		// 从真实 JSON 一路走到 domain：这一步过了，"字段名对不上"这一类问题才算真的排除
		val content = parse(REAL_RESPONSE_BODY).toScannedContent("http://192.168.17.128:8080")

		assertNotNull("真实响应必须能映射出可跳转的目标", content)
		assertEquals(ContentKind.VIDEO, content!!.kind)
		assertEquals(7L, content.targetId)
		assertEquals("封面要拼成绝对地址", "http://192.168.17.128:8080/hls/2026-09-22/cover.jpg", content.cover)
	}

	@Test
	fun `ignores unknown fields added by a newer backend`() {
		// 后端加字段（比如 uploaderId）不能弄坏老客户端：Gson 默认忽略未知字段。
		// 这条钉住的是"加字段是安全的"——否则每次后端加字段都得发版
		val dto = parse(
			"""{"code":0,"message":"success","ttl":0,"data":{"type":"USER","targetId":3,""" +
				""""title":"昵称","futureField":{"a":1},"another":[1,2]}}"""
		)

		assertEquals(ContentKind.USER, dto.toScannedContent("http://host")?.kind)
	}

	@Test
	fun `tolerates missing optional fields`() {
		// subtitle/cover 服务端可以不下发（作者没昵称、没头像）
		val dto = parse("""{"code":0,"message":"success","ttl":0,"data":{"type":"USER","targetId":3,"title":"昵称"}}""")

		assertNull(dto.subtitle)
		assertNull(dto.cover)
		assertEquals(ContentKind.USER, dto.toScannedContent("http://host")?.kind)
	}

	@Test
	fun `json null becomes null not a crash`() {
		// 服务端若显式下发 null（而不是省略字段），Gson 给的是 null 而不是抛异常——
		// DTO 全字段可空正是为了这个（见 ScanResolveDto 的注释）
		val dto = parse(
			"""{"code":0,"message":"success","ttl":0,"data":{"type":null,"targetId":null,""" +
				""""title":null,"subtitle":null,"cover":null}}"""
		)

		assertNull("映射不出来就必须给 null，交给仓库翻成明确提示", dto.toScannedContent("http://host"))
	}

	private companion object {

		/** 与后端 `ScanResolveApiTest.response body shape is pinned for the client contract` 逐字相同 */
		const val REAL_RESPONSE_BODY: String = """
		{
		  "code": 0,
		  "message": "success",
		  "ttl": 0,
		  "data": {
		    "type": "VIDEO",
		    "targetId": 7,
		    "title": "标题",
		    "subtitle": "扫码测试作者",
		    "cover": "/hls/2026-09-22/cover.jpg"
		  }
		}
		"""
	}
}
