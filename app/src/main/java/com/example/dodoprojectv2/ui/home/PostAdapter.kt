package com.example.dodoprojectv2.ui.home

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.text.format.DateFormat
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
        private val imageLikeAnimation: ImageView = itemView.findViewById(R.id.image_like_animation)
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

            // Çift tıklama için GestureDetector oluştur
            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // Kalp animasyonunu göster
                    showLikeAnimation()
                    
                    // Çift tıklama ile beğeni
                    onLikeClicked(post)
                    
                    // Görsel geri bildirimi hemen göster
                    val currentLiked = likedPostsMap[post.postId] ?: false
                    val newLikedState = !currentLiked
                    likedPostsMap[post.postId] = newLikedState
                    
                    buttonLike.setImageResource(
                        if (newLikedState) R.drawable.ic_like_filled else R.drawable.ic_like_outline
                    )
                    
                    val currentLikes = post.likesCount
                    val newLikes = if (newLikedState) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)
                    textLikeCount.text = newLikes.toString()
                    
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // Tek tıklama ile gönderi detayı
                    onPostClicked(post)
                    return true
                }
            })

            // Fotoğrafa çift tıklama dinleyicisi ekle
            imagePost.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }

            // Tıklama olaylarını ayarla
            imageUserProfile.setOnClickListener {
                try {
                    if (!post.userId.isNullOrEmpty()) {
                        onUserClicked(post.userId)
                    } else {
                        Log.e("PostAdapter", "Kullanıcı ID boş veya null")
                    }
                } catch (e: Exception) {
                    Log.e("PostAdapter", "Kullanıcı profiline gitme hatası: ${e.message}", e)
                }
            }

            textUsername.setOnClickListener {
                try {
                    if (!post.userId.isNullOrEmpty()) {
                        onUserClicked(post.userId)
                    } else {
                        Log.e("PostAdapter", "Kullanıcı ID boş veya null")
                    }
                } catch (e: Exception) {
                    Log.e("PostAdapter", "Kullanıcı profiline gitme hatası: ${e.message}", e)
                }
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

        private fun showLikeAnimation() {
            // Animasyon başlamadan önce kalbi görünür yap
            imageLikeAnimation.visibility = View.VISIBLE
            
            // Scale ve alpha animasyonları oluştur
            val scaleXUp = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleX", 0.5f, 1.2f)
            val scaleYUp = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleY", 0.5f, 1.2f)
            val alphaUp = ObjectAnimator.ofFloat(imageLikeAnimation, "alpha", 0f, 1f)
            
            val scaleXDown = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleX", 1.2f, 1f)
            val scaleYDown = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleY", 1.2f, 1f)
            
            val scaleXOut = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleX", 1f, 0.5f)
            val scaleYOut = ObjectAnimator.ofFloat(imageLikeAnimation, "scaleY", 1f, 0.5f)
            val alphaOut = ObjectAnimator.ofFloat(imageLikeAnimation, "alpha", 1f, 0f)
            
            // İlk animasyon seti (büyüme ve görünme)
            val animatorSetUp = AnimatorSet().apply {
                playTogether(scaleXUp, scaleYUp, alphaUp)
                duration = 200
            }
            
            // İkinci animasyon seti (normal boyuta dönme)
            val animatorSetDown = AnimatorSet().apply {
                playTogether(scaleXDown, scaleYDown)
                duration = 100
            }
            
            // Son animasyon seti (küçülme ve kaybolma)
            val animatorSetOut = AnimatorSet().apply {
                playTogether(scaleXOut, scaleYOut, alphaOut)
                duration = 200
                startDelay = 300 // Biraz bekle sonra kaybol
            }
            
            // Tüm animasyonları sırayla çalıştır
            val finalAnimatorSet = AnimatorSet().apply {
                playSequentially(animatorSetUp, animatorSetDown, animatorSetOut)
            }
            
            // Animasyon bitince kalbi gizle
            finalAnimatorSet.doOnEnd {
                imageLikeAnimation.visibility = View.INVISIBLE
                // Değerleri sıfırla
                imageLikeAnimation.alpha = 0f
                imageLikeAnimation.scaleX = 0.5f
                imageLikeAnimation.scaleY = 0.5f
            }
            
            finalAnimatorSet.start()
        }
    }
}

// AnimatorSet.doOnEnd extension function
private fun AnimatorSet.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) {
            action()
        }
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
    })
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