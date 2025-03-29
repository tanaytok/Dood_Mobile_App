package com.example.dodoprojectv2.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserSearchAdapter(
    private var users: List<UserModel> = emptyList(),
    private val onUserClicked: (UserModel) -> Unit,
    private val onFollowClicked: (UserModel, Boolean) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.UserViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""
    private val followingMap = mutableMapOf<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<UserModel>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun checkFollowStatus() {
        if (currentUserId.isEmpty() || users.isEmpty()) return

        // Her kullanıcı için takip durumunu kontrol et
        for (user in users) {
            firestore.collection("follows")
                .document("${currentUserId}_${user.userId}")
                .get()
                .addOnSuccessListener { document ->
                    val isFollowing = document.exists()
                    followingMap[user.userId] = isFollowing
                    notifyDataSetChanged()
                }
        }
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageProfile: ImageView = itemView.findViewById(R.id.image_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)
        private val textBio: TextView = itemView.findViewById(R.id.text_bio)
        private val buttonFollow: Button = itemView.findViewById(R.id.button_follow)

        fun bind(user: UserModel) {
            // Profil fotoğrafını yükle
            Glide.with(itemView.context)
                .load(user.profilePhotoUrl)
                .placeholder(R.drawable.default_profile)
                .into(imageProfile)

            // Kullanıcı adını ve biyografiyi ayarla
            textUsername.text = user.username
            textBio.text = user.bio.ifEmpty { "Biyografi eklenmemiş" }

            // Takip et butonunu yapılandır
            val isFollowing = followingMap[user.userId] ?: false
            if (isFollowing) {
                buttonFollow.text = "Takibi Bırak"
                buttonFollow.setBackgroundResource(R.drawable.button_secondary_background)
            } else {
                buttonFollow.text = "Takip Et"
                buttonFollow.setBackgroundResource(R.drawable.button_primary_background)
            }

            // Kendisi ise takip düğmesini gizle
            if (user.userId == currentUserId) {
                buttonFollow.visibility = View.GONE
            } else {
                buttonFollow.visibility = View.VISIBLE
            }

            // Tıklama olaylarını ayarla
            itemView.setOnClickListener {
                onUserClicked(user)
            }

            buttonFollow.setOnClickListener {
                val newFollowingState = !isFollowing
                followingMap[user.userId] = newFollowingState
                
                // Düğme görünümünü güncelle
                if (newFollowingState) {
                    buttonFollow.text = "Takibi Bırak"
                    buttonFollow.setBackgroundResource(R.drawable.button_secondary_background)
                } else {
                    buttonFollow.text = "Takip Et"
                    buttonFollow.setBackgroundResource(R.drawable.button_primary_background)
                }
                
                // Takip/takibi bırak işlemini bildir
                onFollowClicked(user, newFollowingState)
            }
        }
    }
}

data class UserModel(
    val userId: String = "",
    val username: String = "",
    val bio: String = "",
    val profilePhotoUrl: String = "",
    val followers: Int = 0,
    val following: Int = 0
) 