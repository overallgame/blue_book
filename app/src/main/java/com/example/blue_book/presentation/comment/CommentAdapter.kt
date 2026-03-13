package com.example.blue_book.presentation.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.R
import com.example.blue_book.domain.model.Comment

class CommentAdapter(
    private val currentUserId: Long,
    private val onLikeClick: (Comment) -> Unit,
    private val onReplyClick: (Comment) -> Unit,
    private val onDeleteClick: (Comment) -> Unit,
    private val onLoadReplies: (Comment) -> Unit
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId, onLikeClick, onReplyClick, onDeleteClick, onLoadReplies)
    }

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: com.google.android.material.imageview.ShapeableImageView = itemView.findViewById(R.id.comment_avatar)
        private val nickname: TextView = itemView.findViewById(R.id.comment_nickname)
        private val time: TextView = itemView.findViewById(R.id.comment_time)
        private val content: TextView = itemView.findViewById(R.id.comment_content)
        private val likeBtn: ImageButton = itemView.findViewById(R.id.comment_like_btn)
        private val likeCount: TextView = itemView.findViewById(R.id.comment_like_count)
        private val replyBtn: TextView = itemView.findViewById(R.id.comment_reply_btn)
        private val deleteBtn: TextView = itemView.findViewById(R.id.comment_delete_btn)
        private val repliesRecycler: RecyclerView = itemView.findViewById(R.id.comment_replies_recycler)
        private val viewRepliesBtn: TextView = itemView.findViewById(R.id.comment_view_replies_btn)

        private var repliesAdapter: ReplyAdapter? = null

        fun bind(
            comment: Comment,
            currentUserId: Long,
            onLikeClick: (Comment) -> Unit,
            onReplyClick: (Comment) -> Unit,
            onDeleteClick: (Comment) -> Unit,
            onLoadReplies: (Comment) -> Unit
        ) {
            Glide.with(itemView.context).load(comment.avatar).placeholder(R.drawable.ic_launcher_background).into(avatar)
            nickname.text = comment.nickname
            time.text = formatTime(comment.createTime)
            content.text = comment.content
            likeCount.text = formatCount(comment.likeCount)
            likeBtn.setImageResource(if (comment.isLiked) R.drawable.like_icon3 else R.drawable.like_icon2)

            deleteBtn.visibility = if (comment.userId == currentUserId) View.VISIBLE else View.GONE

            likeBtn.setOnClickListener { onLikeClick(comment) }
            replyBtn.setOnClickListener { onReplyClick(comment) }
            deleteBtn.setOnClickListener { onDeleteClick(comment) }

            val hasReplies = comment.replies.isNotEmpty()

            if (hasReplies) {
                repliesRecycler.visibility = View.VISIBLE
                viewRepliesBtn.visibility = View.VISIBLE

                viewRepliesBtn.text = "收起回复"
                setupReplies(comment.replies, onLikeClick, onReplyClick)

                viewRepliesBtn.setOnClickListener {
                    if (repliesRecycler.visibility == View.VISIBLE) {
                        repliesRecycler.visibility = View.GONE
                        viewRepliesBtn.text = "展开回复"
                    } else {
                        repliesRecycler.visibility = View.VISIBLE
                        viewRepliesBtn.text = "收起回复"
                    }
                }
            } else {
                repliesRecycler.visibility = View.GONE
                viewRepliesBtn.visibility = View.GONE
            }
        }

        private fun setupReplies(replies: List<Comment>, onLikeClick: (Comment) -> Unit, onReplyClick: (Comment) -> Unit) {
            if (repliesAdapter == null) {
                repliesAdapter = ReplyAdapter(onLikeClick, onReplyClick)
                repliesRecycler.layoutManager = LinearLayoutManager(itemView.context)
                repliesRecycler.adapter = repliesAdapter
            }
            repliesAdapter?.submitList(replies)
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

    private class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}
