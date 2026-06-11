package com.example.blue_book.data.mapper

import com.example.blue_book.network.ApiGateway
import com.example.blue_book.data.UserAccount
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto

fun UserV2ProfileDto.toDomain(phone: String = ""): UserAccount {
    val base = ApiGateway.BASE_URL.trimEnd('/')
    fun n(s: String?): String? {
        val v = s?.trim().orEmpty()
        return v.ifBlank { null }
    }
    fun abs(url: String?): String? {
        val v = n(url) ?: return null
        if (v.startsWith("http://") || v.startsWith("https://")) return v
        return if (v.startsWith("/")) "$base$v" else "$base/$v"
    }
    return UserAccount(
        phone = phone,
        avatar = abs(avatar),
        nickname = n(nickname),
        password = null,
        introduction = n(bio),
        sex = n(gender),
        birthday = n(birthday),
        career = n(occupation),
        region = n(region),
        school = n(school),
        background = abs(backgroundImage)
    )
}

fun UserV2MeDto.toDomain(): UserAccount {
    val base = ApiGateway.BASE_URL.trimEnd('/')
    fun n(s: String?): String? {
        val v = s?.trim().orEmpty()
        return v.ifBlank { null }
    }
    fun abs(url: String?): String? {
        val v = n(url) ?: return null
        if (v.startsWith("http://") || v.startsWith("https://")) return v
        return if (v.startsWith("/")) "$base$v" else "$base/$v"
    }
    return UserAccount(
        phone = phone,
        avatar = abs(avatar),
        nickname = n(nickname),
        password = null,
        introduction = n(bio),
        sex = n(gender),
        birthday = n(birthday),
        career = n(occupation),
        region = n(region),
        school = n(school),
        background = abs(backgroundImage)
    )
}
