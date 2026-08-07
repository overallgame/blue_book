package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class SendCodeRequest(
    @field:NotBlank val phone: String,
    val nickname: String? = null
)
