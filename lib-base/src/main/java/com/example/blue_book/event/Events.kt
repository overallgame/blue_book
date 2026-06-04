package com.example.blue_book.event

data class LoginEvent(
    val phone: String,
    val nickname: String,
    val avatar: String
)

object LogoutEvent

data class AvatarUpdateEvent(val avatarUrl: String)

data class LikeToggleEvent(val videoId: String, val isLiked: Boolean)

data class CollectToggleEvent(val videoId: String, val isCollected: Boolean)
