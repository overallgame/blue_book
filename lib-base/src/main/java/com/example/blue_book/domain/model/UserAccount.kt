package com.example.blue_book.domain.model

data class UserAccount(
    val phone: String,
    val avatar: String?,
    val nickname: String?,
    val password: String?,
    val introduction: String?,
    val sex: String?,
    val birthday: String?,
    val career: String?,
    val region: String?,
    val school: String?,
    val background: String?
)

