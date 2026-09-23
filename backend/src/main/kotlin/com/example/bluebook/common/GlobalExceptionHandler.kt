package com.example.bluebook.common

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.time.format.DateTimeParseException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Any>> {
        val status = when (ex.code) {
            in 10001..10005 -> HttpStatus.UNAUTHORIZED
            in 11001..11999 -> HttpStatus.NOT_FOUND
            14001, 15002 -> HttpStatus.FORBIDDEN
            // 扫一扫：码合法但内容不在了。15001（不是本站码）刻意不在此列——它落到 else 的 400，
            // 「这不是小蓝书的二维码」在语义上就是"入参不是我们认的东西"，不是权限也不是 404
            15003 -> HttpStatus.NOT_FOUND
            14999 -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code, ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Any>> {
        // fieldErrors 的元素本来就是 FieldError，原先的 `it as? FieldError` 是多余转换
        // （编译器报 "No cast needed"）。defaultMessage 可能为 null，仍按原样兜成空串。
        val msg = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: ""}" }
        return ResponseEntity.badRequest().body(ApiResponse.fail(14002, msg))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiResponse<Any>> {
        val msg = ex.constraintViolations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
        return ResponseEntity.badRequest().body(ApiResponse.fail(14002, msg))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.fail(10005, "请先登录"))

    /**
     * 客户端入参错误统一映射为 400。
     * 这些异常原先全部落到 handleUnknown 变成 500「服务器繁忙」，用户看到误导性文案，
     * 而服务端日志被这些噪音淹没（缺参数、类型不匹配、请求体畸形、分页参数越界等）。
     */
    @ExceptionHandler(
        ServletRequestBindingException::class,          // 缺少必填查询参数
        MethodArgumentTypeMismatchException::class,     // ?size=abc / ?liked=xyz
        HttpMessageNotReadableException::class,         // 请求体畸形或必填字段为 null
        IllegalArgumentException::class,                // PageRequest.of(size<=0)、List.take(-1) 等
        DateTimeParseException::class                   // 日期字符串无法解析
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiResponse<Any>> {
        // 必须留日志：这个处理器同时兜住 IllegalArgumentException 这类宽泛类型，
        // 若服务端真的抛了 IAE（断言、类型不匹配、URL 解析等）也会走到这里，
        // 不留痕迹的话真实缺陷会以 400 的形式彻底消失
        log.warn("请求参数异常（若为服务端抛出请按缺陷排查）: {}", ex.toString())
        // 堆栈放 debug：客户端入参错误会很多，但排查服务端 IAE 时需要调用点
        log.debug("请求参数异常堆栈", ex)
        return ResponseEntity.badRequest().body(ApiResponse.fail(14003, "请求参数有误"))
    }

    /**
     * 上传体超过 multipart 限制：客户端把分片切大了，或声明的大小与实际不符，属入参问题，返回 400。
     *
     * 分片上传的客户端按服务端下发的 `chunkSize` 切片，所以走到这里通常意味着客户端没遵守契约。
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(ex: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Any>> {
        log.warn("上传体超过 multipart 限制: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(13001, "分片过大，请减小分片大小后重试"))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("未处理异常", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(14999, "服务器繁忙，请稍后再试"))
    }
}
