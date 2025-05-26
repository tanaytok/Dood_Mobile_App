package com.example.dodoprojectv2.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

class FollowUserAdapter(
    private var users: List<FollowUser>,
    private val onUserClicked: (String) -> Unit
) : RecyclerView.Adapter<FollowUserAdapter.FollowUserViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""
    private val followStates = mutableMapOf<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_user, parent, false)
        return FollowUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowUserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<FollowUser>) {
        users = newUsers
        // Load follow states for new users
        loadFollowStates()
        notifyDataSetChanged()
    }

    private fun loadFollowStates() {
        for (user in users) {
            if (user.userId != currentUserId) {
                checkFollowStatus(user.userId)
            }
        }
    }

    private fun checkFollowStatus(userId: String) {
        if (currentUserId.isNotEmpty()) {
            firestore.collection("follows")
                .document("${currentUserId}_${userId}")
                .get()
                .addOnSuccessListener { document ->
                    followStates[userId] = document.exists()
                    notifyDataSetChanged()
                }
        }
    }

    inner class FollowUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageProfile: CircleImageView = itemView.findViewById(R.id.image_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)
        private val textBio: TextView = itemView.findViewById(R.id.text_bio)
        private val buttonFollow: Button = itemView.findViewById(R.id.button_follow_user)
        private val buttonFollowing: Button = itemView.findViewById(R.id.button_following)

        fun bind(user: FollowUser) {
            // Load profile image
            Glide.with(itemView.context)
                .load(user.profilePhotoUrl)
                .placeholder(R.drawable.default_profile)
                .into(imageProfile)

            // Set username
            textUsername.text = user.username

            // Set bio (show if not empty)
            if (user.bio.isNotEmpty()) {
                textBio.text = user.bio
                textBio.visibility = View.VISIBLE
            } else {
                textBio.visibility = View.GONE
            }

            // Handle follow button visibility and state
            if (user.userId == currentUserId) {
                // Own profile - hide follow buttons
                buttonFollow.visibility = View.GONE
                buttonFollowing.visibility = View.GONE
            } else {
                // Other user - show appropriate follow button
                val isFollowing = followStates[user.userId] ?: false
                
                if (isFollowing) {
                    buttonFollow.visibility = View.GONE
                    buttonFollowing.visibility = View.VISIBLE
                    
                    buttonFollowing.setOnClickListener {
                        unfollowUser(user)
                    }
                } else {
                    buttonFollow.visibility = View.VISIBLE
                    buttonFollowing.visibility = View.GONE
                    
                    buttonFollow.setOnClickListener {
                        followUser(user)
                    }
                }
            }

            // Set click listener for the whole item
            itemView.setOnClickListener {
                onUserClicked(user.userId)
            }
        }

        private fun followUser(user: FollowUser) {
            if (currentUserId.isEmpty()) return

            val followId = "${currentUserId}_${user.userId}"
            val followData = hashMapOf(
                "followerId" to currentUserId,
                "followingId" to user.userId,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("follows")
                .document(followId)
                .set(followData)
                .addOnSuccessListener {
                    followStates[user.userId] = true
                    notifyDataSetChanged()
                    
                    // Update follow counts
                    updateFollowCounts(user.userId, currentUserId, 1)
                }
        }

        private fun unfollowUser(user: FollowUser) {
            if (currentUserId.isEmpty()) return

            val followId = "${currentUserId}_${user.userId}"

            firestore.collection("follows")
                .document(followId)
                .delete()
                .addOnSuccessListener {
                    followStates[user.userId] = false
                    notifyDataSetChanged()
                    
                    // Update follow counts
                    updateFollowCounts(user.userId, currentUserId, -1)
                }
        }

        private fun updateFollowCounts(followingUserId: String, followerUserId: String, increment: Int) {
            // Gerçek takip sayılarını hesaplayarak güncelle
            
            // Takip eden kullanıcının takip ettiği sayısını yeniden hesapla
            firestore.collection("follows")
                .whereEqualTo("followerId", followerUserId)
                .get()
                .addOnSuccessListener { followingDocuments ->
                    val realFollowingCount = followingDocuments.size()
                    
                    firestore.collection("users").document(followerUserId)
                        .update("following", realFollowingCount)
                }

            // Takip edilen kullanıcının takipçi sayısını yeniden hesapla
            firestore.collection("follows")
                .whereEqualTo("followingId", followingUserId)
                .get()
                .addOnSuccessListener { followerDocuments ->
                    val realFollowersCount = followerDocuments.size()
                    
                    firestore.collection("users").document(followingUserId)
                        .update("followers", realFollowersCount)
                }
        }
    }
} 