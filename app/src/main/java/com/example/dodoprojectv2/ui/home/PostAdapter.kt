package com.example.dodoprojectv2.ui.home

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class PostAdapter(
    private var posts: List<PostModel> = emptyList(),
    private val onPostClicked: (PostModel) -> Unit,
    private val onLikeClicked: (PostModel) -> Unit,
    private val onLikeLongClicked: (PostModel, View) -> Boolean,
    private val onUserClicked: (String) -> Unit,
    private val onCommentsClicked: (PostModel) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""
    private val likedPostsMap = mutableMapOf<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.bind(post)
    }

    override fun getItemCount(): Int = posts.size

    fun updatePosts(newPosts: List<PostModel>) {
        posts = newPosts
        notifyDataSetChanged()
        checkLikeStatus()
    }

    private fun checkLikeStatus() {
        if (currentUserId.isEmpty() || posts.isEmpty()) return

        // Tüm gönderi beğenilerini kontrol et
        for (post in posts) {
            firestore.collection("post_likes")
                .document("${currentUserId}_${post.postId}")
                .get()
                .addOnSuccessListener { document ->
                    val isLiked = document.exists()
                    likedPostsMap[post.postId] = isLiked
                    notifyDataSetChanged()
                }
        }
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageUserProfile: ImageView = itemView.findViewById(R.id.image_user_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)
        private val textDate: TextView = itemView.findViewById(R.id.text_date)
        private val textTaskName: TextView = itemView.findViewById(R.id.text_task_name)
        private val imagePost: ImageView = itemView.findViewById(R.id.image_post)
        private val buttonLike: ImageButton = itemView.findViewById(R.id.button_like)
        private val textLikeCount: TextView = itemView.findViewById(R.id.text_like_count)
        private val buttonComment: ImageButton = itemView.findViewById(R.id.button_comment)
        private val textCommentCount: TextView = itemView.findViewById(R.id.text_comment_count)

        fun bind(post: PostModel) {
            // Kullanıcı profil fotoğrafını yükle
            Glide.with(itemView.context)
                .load(post.userProfileUrl)
                .placeholder(R.drawable.default_profile)
                .into(imageUserProfile)

            // Kullanıcı adını ayarla
            textUsername.text = post.username

            // Tarihi formatlayıp göster
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = post.timestamp
            val dateFormat = DateFormat.format("dd MMMM", calendar).toString()
            textDate.text = dateFormat

            // Görev adını göster
            textTaskName.text = post.taskName

            // Gönderi fotoğrafını yükle
            Glide.with(itemView.context)
                .load(post.photoUrl)
                .placeholder(R.drawable.placeholder_image)
                .into(imagePost)

            // Beğeni sayısını göster
            textLikeCount.text = post.likesCount.toString()
            
            // Yorum sayısını göster
            textCommentCount.text = post.commentsCount.toString()

            // Beğeni butonunu yapılandır
            val isLiked = likedPostsMap[post.postId] ?: false
            buttonLike.setImageResource(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like_outline
            )

            // Tıklama olaylarını ayarla
            imageUserProfile.setOnClickListener {
                onUserClicked(post.userId)
            }

            textUsername.setOnClickListener {
                onUserClicked(post.userId)
            }

            imagePost.setOnClickListener {
                onPostClicked(post)
            }

            buttonLike.setOnClickListener {
                onLikeClicked(post)
                // Görsel geri bildirimi hemen göster (geçici UI güncellemesi)
                val currentLiked = likedPostsMap[post.postId] ?: false
                val newLikedState = !currentLiked
                likedPostsMap[post.postId] = newLikedState
                
                buttonLike.setImageResource(
                    if (newLikedState) R.drawable.ic_like_filled else R.drawable.ic_like_outline
                )
                
                val currentLikes = post.likesCount
                val newLikes = if (newLikedState) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)
                textLikeCount.text = newLikes.toString()
            }
            
            // Beğeni butonuna uzun basma olayını ayarla
            buttonLike.setOnLongClickListener {
                onLikeLongClicked(post, buttonLike)
            }
            
            // Beğeni sayısına uzun basma olayını da ekleyelim
            textLikeCount.setOnLongClickListener {
                onLikeLongClicked(post, textLikeCount)
            }
            
            buttonComment.setOnClickListener {
                onCommentsClicked(post)
            }
        }
    }
}

data class PostModel(
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val userProfileUrl: String = "",
    val photoUrl: String = "",
    val taskId: String = "",
    val taskName: String = "",
    val timestamp: Long = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isPublic: Boolean = true
) 