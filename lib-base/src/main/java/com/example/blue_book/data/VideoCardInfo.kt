package com.example.blue_book.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoCardInfo(
	val aid: Long,
	val cid: Long,
	var like: Int,
	val image: String,
	val avatar: String,
	var collection: Int,
	val nickname: String,
	val description: String,
	val playUrl: String,
	var isLike: Boolean,
	var isCollect: Boolean,
	var commentCount: Int = 0,
	val uploaderId: Long = 0,
	/** 当前用户是否已关注该作者（进入播放页前由服务端下发） */
	var isFollowed: Boolean = false
): Parcelable


