package com.example.blue_book.auth.data.mapper

import com.example.blue_book.auth.data.remote.dto.UserProfile
import com.example.blue_book.data.UserAccount

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

