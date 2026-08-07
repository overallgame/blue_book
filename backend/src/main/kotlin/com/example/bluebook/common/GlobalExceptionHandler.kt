package com.example.bluebook.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ApiResponse<Any> =
        ApiResponse.fail(ex.code, ex.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ApiResponse<Any> {
        val msg = ex.bindingResult.fieldErrors
            .joinToString("; ") { (it as FieldError).let { f -> "${f.field}: ${f.defaultMessage}" } }
        return ApiResponse.fail(14001, msg)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ApiResponse<Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.fail(10005, "请先登录"))

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ApiResponse<Any> {
        log.error("未处理异常", ex)
        return ApiResponse.fail(14999, "服务器繁忙，请稍后再试")
    }
}
