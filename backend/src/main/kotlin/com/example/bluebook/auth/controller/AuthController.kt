package com.example.bluebook.auth.controller

import com.example.bluebook.auth.dto.*
import com.example.bluebook.auth.service.AuthService
import com.example.bluebook.common.ApiResponse
import com.example.bluebook.common.JwtUtil
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtUtil: JwtUtil
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.login(request))

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.register(request))

    @PostMapping("/code")
    fun sendCode(@Valid @RequestBody request: SendCodeRequest): ApiResponse<String> =
        ApiResponse.ok(authService.sendCode(request))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization") authHeader: String): ApiResponse<Any> {
        val token = authHeader.removePrefix("Bearer ")
        val userId = jwtUtil.getUserId(token)
        val jti = jwtUtil.getJti(token)
        authService.logout(userId)
        authService.addToBlacklist(jti)
        SecurityContextHolder.clearContext()
        return ApiResponse.ok()
    }
}
