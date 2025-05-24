package com.example.dodoprojectv2.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R

class CommentAdapter(
    private var comments: List<CommentModel> = emptyList(),
    private val onUserClicked: (String) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.bind(comment)
    }

    override fun getItemCount(): Int = comments.size

    fun updateComments(newComments: List<CommentModel>) {
        comments = newComments
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageUserProfile: ImageView = itemView.findViewById(R.id.image_user_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)
        private val textComment: TextView = itemView.findViewById(R.id.text_comment)
        private val textDate: TextView = itemView.findViewById(R.id.text_comment_date)

        fun bind(comment: CommentModel) {
            // Kullanıcı profil fotoğrafını yükle
            Glide.with(itemView.context)
                .load(comment.userProfileUrl)
                .placeholder(R.drawable.default_profile)
                .into(imageUserProfile)

            // Kullanıcı adını ve yorumunu ayarla
            textUsername.text = comment.username
            textComment.text = comment.text
            
            // Tarihi ayarla
            textDate.text = comment.getFormattedDate()

            // Kullanıcıya tıklama olayını ayarla
            imageUserProfile.setOnClickListener {
                onUserClicked(comment.userId)
            }
            
            textUsername.setOnClickListener {
                onUserClicked(comment.userId)
            }
        }
    }
} 