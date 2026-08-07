package com.example.bluebook.user.dto

data class UserV2UpdateRequestDto(
    val nickname: String?,
    val bio: String?,
    val gender: String?,
    val birthday: String?,
    val occupation: String?,
    val region: String?,
    val school: String?,
    val backgroundImage: String?
)
