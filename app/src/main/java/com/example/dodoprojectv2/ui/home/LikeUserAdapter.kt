package com.example.dodoprojectv2.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R

/**
 * Beğenen kullanıcıları RecyclerView'da göstermek için kullanılan adapter
 */
class LikeUserAdapter(
    private var users: List<LikeUserModel> = emptyList(),
    private val onUserClicked: (String) -> Unit
) : RecyclerView.Adapter<LikeUserAdapter.LikeUserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LikeUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_like_user, parent, false)
        return LikeUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: LikeUserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<LikeUserModel>) {
        users = newUsers
        notifyDataSetChanged()
    }

    inner class LikeUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageUserProfile: ImageView = itemView.findViewById(R.id.image_user_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)

        fun bind(user: LikeUserModel) {
            // Kullanıcı profil fotoğrafını yükle
            Glide.with(itemView.context)
                .load(user.profilePhotoUrl)
                .placeholder(R.drawable.default_profile)
                .circleCrop()
                .into(imageUserProfile)

            // Kullanıcı adını ayarla
            textUsername.text = user.username

            // Tıklama olayını ayarla
            itemView.setOnClickListener {
                if (!user.userId.isNullOrEmpty()) {
                    onUserClicked(user.userId)
                }
            }
        }
    }
} 