package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val password: String,
    @field:NotBlank val nickname: String,
    @field:NotBlank val code: String
)
