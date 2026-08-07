package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)
