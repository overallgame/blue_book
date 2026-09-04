package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val password: String
)
