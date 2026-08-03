package com.example.blue_book.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.feature_message.R

class MessageAdapter(
	private val onClick: (MessageItem) -> Unit
) : ListAdapter<MessageItem, MessageAdapter.VH>(DIFF) {

	init { setHasStableIds(true) }
	override fun getItemId(position: Int): Long = getItem(position).id

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
		return VH(view)
	}

	override fun onBindViewHolder(holder: VH, position: Int) {
		holder.bind(getItem(position), onClick)
	}

	class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val avatar: ImageView = itemView.findViewById(R.id.msg_avatar)
		private val title: TextView = itemView.findViewById(R.id.msg_title)
		private val content: TextView = itemView.findViewById(R.id.msg_content)
		private val time: TextView = itemView.findViewById(R.id.msg_time)
		private val unreadDot: View = itemView.findViewById(R.id.msg_unread_dot)
		private val typeIcon: ImageView = itemView.findViewById(R.id.msg_type_icon)

		fun bind(item: MessageItem, onClick: (MessageItem) -> Unit) {
			title.text = item.nickname
			content.text = item.content
			time.text = formatTime(item.time)
			unreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE

			// 按消息类型设置图标
			val iconRes = when (item.type) {
				MessageType.Follow -> R.drawable.ic_msg_follow
				MessageType.Like -> R.drawable.ic_msg_like
				MessageType.Comment -> R.drawable.ic_msg_comment
				MessageType.System -> R.drawable.ic_message_empty
			}
			typeIcon.setImageResource(iconRes)

			// 系统消息隐藏头像和时间
			if (item.type == MessageType.System) {
				avatar.visibility = View.GONE
				time.visibility = View.GONE
			} else {
				avatar.visibility = View.VISIBLE
				time.visibility = View.VISIBLE
			}

			itemView.setOnClickListener { onClick(item) }
		}

		private fun formatTime(timestamp: Long): String {
			val now = System.currentTimeMillis()
			val diff = now - timestamp
			return when {
				diff < 60_000 -> "刚刚"
				diff < 3600_000 -> "${diff / 60_000}分钟前"
				diff < 86400_000 -> "${diff / 3600_000}小时前"
				diff < 604800_000 -> "${diff / 86400_000}天前"
				else -> "${diff / 604800_000}周前"
			}
		}
	}

	companion object {
		val DIFF = object : DiffUtil.ItemCallback<MessageItem>() {
			override fun areItemsTheSame(a: MessageItem, b: MessageItem) = a.id == b.id
			override fun areContentsTheSame(a: MessageItem, b: MessageItem) = a == b
		}
	}
}
