package com.example.blue_book.presentation.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.R
import com.example.blue_book.domain.model.Comment

class ReplyAdapter(
    private val onLikeClick: (Comment) -> Unit,
    private val onReplyClick: (Comment) -> Unit
) : ListAdapter<Comment, ReplyAdapter.ReplyViewHolder>(ReplyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment_reply, parent, false)
        return ReplyViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
        holder.bind(getItem(position), onLikeClick, onReplyClick)
    }

    class ReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: com.google.android.material.imageview.ShapeableImageView = itemView.findViewById(R.id.reply_avatar)
        private val nickname: TextView = itemView.findViewById(R.id.reply_nickname)
        private val time: TextView = itemView.findViewById(R.id.reply_time)
        private val content: TextView = itemView.findViewById(R.id.reply_content)
        private val likeBtn: ImageButton = itemView.findViewById(R.id.reply_like_btn)
        private val likeCount: TextView = itemView.findViewById(R.id.reply_like_count)
        private val replyBtn: TextView = itemView.findViewById(R.id.reply_action_layout)

        fun bind(comment: Comment, onLikeClick: (Comment) -> Unit, onReplyClick: (Comment) -> Unit) {
            val displayName = if (comment.replyToNickname != null) {
                "${comment.nickname} 回复 @${comment.replyToNickname}"
            } else {
                comment.nickname
            }

            Glide.with(itemView.context).load(comment.avatar).placeholder(R.drawable.ic_launcher_background).into(avatar)
            nickname.text = displayName
            time.text = formatTime(comment.createTime)
            content.text = comment.content
            likeCount.text = formatCount(comment.likeCount)
            likeBtn.setImageResource(if (comment.isLiked) R.drawable.like_icon3 else R.drawable.like_icon2)

            likeBtn.setOnClickListener { onLikeClick(comment) }
            replyBtn.setOnClickListener { onReplyClick(comment) }
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

        private fun formatCount(count: Int): String {
            return when {
                count < 1000 -> count.toString()
                count < 10000 -> String.format("%.1fk", count / 1000.0)
                else -> String.format("%.1fw", count / 10000.0)
            }
        }
    }

    private class ReplyDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}
