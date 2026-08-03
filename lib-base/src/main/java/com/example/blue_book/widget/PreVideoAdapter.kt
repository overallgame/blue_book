package com.example.blue_book.widget

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.lib_base.R
import com.example.blue_book.data.VideoCardInfo

class PreVideoAdapter(
	private val onClickLike: (VideoCardInfo) -> Unit,
	private val onClickItem: (VideoCardInfo) -> Unit = {}
) : ListAdapter<VideoCardInfo, PreVideoAdapter.VH>(DIFF) {

	init { setHasStableIds(true) }

	override fun getItemId(position: Int): Long {
		val item = getItem(position)
		return (item.aid * 31L) + item.cid
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.pre_video_item_view, parent, false)
		return VH(view)
	}

	override fun onBindViewHolder(holder: VH, position: Int) {
		holder.bind(getItem(position), onClickLike, onClickItem)
	}

	override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
		if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
		holder.updateItem(getItem(position))
		val payload = payloads.lastOrNull() as? Payload
		if (payload is Payload.LikeChanged) {
			holder.bindLike(payload.isLike, payload.like)
		} else {
			onBindViewHolder(holder, position)
		}
	}

	fun submitAppend(items: List<VideoCardInfo>) { submitList(items) }

	fun updateVideoList(video: VideoCardInfo) {
		val idx = currentList.indexOfFirst { it.aid == video.aid && it.cid == video.cid }
		if (idx == -1) return
		val updated = currentList.toMutableList().apply { this[idx] = video }
		submitList(updated)
	}

	class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val cover: ImageView = itemView.findViewById(R.id.PreVideo_item_img)
		private val avatar: ImageView = itemView.findViewById(R.id.PreVideo_item_avatar)
		private val desc: TextView = itemView.findViewById(R.id.PreVideo_item_description)
		private val nickname: TextView = itemView.findViewById(R.id.PreVideo_item_nickname)
		private val likeIcon: ImageView = itemView.findViewById(R.id.PreVideo_item_isLove)
		private val likeNum: TextView = itemView.findViewById(R.id.PreVideo_item_isLoveNumber)
		private var currentItem: VideoCardInfo? = null

		@SuppressLint("SetTextI18n")
		fun bind(item: VideoCardInfo, onClickLike: (VideoCardInfo) -> Unit, onClickItem: (VideoCardInfo) -> Unit) {
			currentItem = item
			desc.text = item.description
			nickname.text = item.nickname
			bindLike(item.isLike, item.like)
			Glide.with(itemView).load(item.image).placeholder(R.drawable.default_avatar).into(cover)
			Glide.with(itemView).load(item.avatar).placeholder(R.drawable.ic_launcher_background).into(avatar)
			likeIcon.setOnClickListener { currentItem?.let(onClickLike) }
			itemView.setOnClickListener { currentItem?.let(onClickItem) }
		}

		fun updateItem(item: VideoCardInfo) { currentItem = item }

		fun bindLike(isLike: Boolean, like: Int) {
			likeNum.text = like.toString()
			likeIcon.setImageResource(if (isLike) R.drawable.like_icon3 else R.drawable.like_icon2)
		}
	}

	private sealed interface Payload {
		data class LikeChanged(val isLike: Boolean, val like: Int) : Payload
	}

	private companion object {
		val DIFF = object : DiffUtil.ItemCallback<VideoCardInfo>() {
			override fun areItemsTheSame(a: VideoCardInfo, b: VideoCardInfo) = a.aid == b.aid && a.cid == b.cid
			override fun areContentsTheSame(a: VideoCardInfo, b: VideoCardInfo) = a == b
			override fun getChangePayload(old: VideoCardInfo, new: VideoCardInfo): Any? {
				val changed = old.isLike != new.isLike || old.like != new.like
				return if (changed) Payload.LikeChanged(new.isLike, new.like) else null
			}
		}
	}
}
