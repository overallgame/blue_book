package com.example.bluebook.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * 业务异常 → HTTP 状态码的映射表。
 *
 * 大部分映射在接口测试里被间接覆盖了（那里断言的是 400/403/404 + 业务码），
 * 但**有一条测不到**：`MaxUploadSizeExceededException` 由 servlet 的 multipart 解析器抛出，
 * 而 MockMvc 的 `multipart {}` 直接把文件对象交给控制器、绕过了解析器，
 * 所以"超限的请求体"在 MockMvc 里根本触发不了。只能直接调处理器。
 *
 * 它值得一条用例：改动前这个异常没有任何处理器，落到 `handleUnknown` 变成
 * **500「服务器繁忙」**——而它其实是客户端把分片切大了，是入参问题。
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `oversized multipart body is a client error not a server error`() {
        val response = handler.handleUploadTooLarge(MaxUploadSizeExceededException(10_485_760L))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(13001, response.body?.code)
        assertEquals("分片过大，请减小分片大小后重试", response.body?.message)
    }

    @Test
    fun `not a bluebook code stays 400 while forbidden and not found are distinct`() {
        // 这三条与分片无关，但同属这张映射表：把它们钉在一起，
        // 将来有人调整 `when` 的分支时能一眼看出哪几条是有意为之
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleBusiness(NotBlueBookCodeException()).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, handler.handleBusiness(ForbiddenException()).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, handler.handleBusiness(ScanContentForbiddenException()).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, handler.handleBusiness(ScanTargetNotFoundException("用户不存在")).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, handler.handleBusiness(VideoNotFoundException()).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleBusiness(InvalidUploadParamsException("越界")).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleBusiness(ChunkMissingException()).statusCode)
    }
}
