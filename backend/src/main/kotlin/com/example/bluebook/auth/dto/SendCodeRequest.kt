package com.example.bluebook.auth.dto

import jakarta.validation.constraints.NotBlank

data class SendCodeRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank val nickname: String
)
