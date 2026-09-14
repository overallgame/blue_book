package com.example.bluebook.common

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.format.DateTimeParseException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Any>> {
        val status = when (ex.code) {
            in 10001..10005 -> HttpStatus.UNAUTHORIZED
            in 11001..11999 -> HttpStatus.NOT_FOUND
            14001 -> HttpStatus.FORBIDDEN
            14999 -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.code, ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Any>> {
        val msg = ex.bindingResult.fieldErrors
            .joinToString("; ") { (it as? FieldError)?.let { f -> "${f.field}: ${f.defaultMessage}" } ?: (it.defaultMessage ?: "") }
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
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.badRequest().body(ApiResponse.fail(14003, "请求参数有误"))

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("未处理异常", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(14999, "服务器繁忙，请稍后再试"))
    }
}
