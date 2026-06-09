package com.example.blue_book.auth.data.mapper

import com.example.blue_book.auth.data.remote.dto.UserProfile
import com.example.blue_book.common.bean.UserAccount
import com.example.blue_book.room.entity.UserEntity

fun UserProfile.toDomain(): UserAccount {
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
