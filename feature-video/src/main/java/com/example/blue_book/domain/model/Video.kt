package com.example.blue_book.domain.model

data class Video(
	val aid: Long,
	val cid: Long,
	val like: Int,
	val image: String,
	val avatar: String,
	val collection: Int,
	val nickname: String,
	val description: String
)


