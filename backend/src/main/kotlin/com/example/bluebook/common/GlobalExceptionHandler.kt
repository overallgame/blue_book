package com.example.bluebook.common

import jakarta.validation.ConstraintViolationException
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

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("未处理异常", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(14999, "服务器繁忙，请稍后再试"))
    }
}
