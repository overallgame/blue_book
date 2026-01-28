package com.example.blue_book.data.mapper

import com.example.blue_book.data.local.db.entity.UserEntity
import com.example.blue_book.di.NetworkModule
import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.data.remote.account.dto.UserProfileDto
import com.example.blue_book.data.remote.user.dto2.UserV2MeDto
import com.example.blue_book.data.remote.user.dto2.UserV2ProfileDto

fun UserEntity.toDomain(): UserAccount {
    return UserAccount(
        phone = phone,
        avatar = avatar,
        nickname = nickname,
        password = password,
        introduction = introduction,
        sex = sex,
        birthday = birthday,
        career = career,
        region = region,
        school = school,
        background = background
    )
}

fun UserV2ProfileDto.toDomain(phone: String = ""): UserAccount {
    val base = NetworkModule.BASE_URL.trimEnd('/')
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

fun UserAccount.toEntity(fallbackPassword: String = ""): UserEntity {
    return UserEntity(
        phone = phone,
        avatar = avatar,
        nickname = nickname,
        password = password ?: fallbackPassword,
        introduction = introduction,
        sex = sex,
        birthday = birthday,
        career = career,
        region = region,
        school = school,
        background = background,
        authToken = null,
        refreshToken = null,
        last_Login = System.currentTimeMillis()
    )
}

fun UserProfileDto.toDomain(): UserAccount {
    return UserAccount(
        phone = phone.orEmpty(),
        avatar = avatar,
        nickname = nickname,
        password = password,
        introduction = bio,
        sex = gender,
        birthday = birthday,
        career = occupation,
        region = region,
        school = school,
        background = backgroundImage
    )
}

fun UserV2MeDto.toDomain(): UserAccount {
    val base = NetworkModule.BASE_URL.trimEnd('/')
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
