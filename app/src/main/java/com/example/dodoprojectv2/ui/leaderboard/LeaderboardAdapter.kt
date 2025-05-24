package com.example.dodoprojectv2.ui.leaderboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R

/**
 * Skor tablosundaki kullanıcıları listelemek için kullanılan adapter
 */
class LeaderboardAdapter(
    private val onUserClicked: (String) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {
    
    private var users: List<LeaderboardUser> = emptyList()
    
    /**
     * Kullanıcı listesini güncelleyerek, kullanıcıların sıralamalarını da ayarlar.
     */
    fun updateUsers(newUsers: List<LeaderboardUser>) {
        // Kullanıcılar zaten skor sırasına göre gelmiş olmalı
        val rankedUsers = newUsers.mapIndexed { index, user ->
            user.copy(rank = index + 1)  // Sıralamayı ayarla (1'den başlar)
        }
        this.users = rankedUsers
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_leaderboard_user, parent, false
        )
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user)
    }
    
    override fun getItemCount(): Int = users.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textRank: TextView = itemView.findViewById(R.id.text_rank)
        private val imageProfile: ImageView = itemView.findViewById(R.id.image_profile)
        private val textUsername: TextView = itemView.findViewById(R.id.text_username)
        private val textScore: TextView = itemView.findViewById(R.id.text_score)
        
        fun bind(user: LeaderboardUser) {
            textRank.text = user.rank.toString()
            textUsername.text = user.username
            textScore.text = user.score.toString()
            
            // Profil fotoğrafını yükle
            if (user.profilePhotoUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(user.profilePhotoUrl)
                    .placeholder(R.drawable.default_profile)
                    .into(imageProfile)
            } else {
                imageProfile.setImageResource(R.drawable.default_profile)
            }
            
            // İlk üç sıradaki kullanıcıların sıralama numaralarını vurgula
            when (user.rank) {
                1 -> textRank.setTextColor(itemView.context.getColor(android.R.color.holo_orange_light))
                2 -> textRank.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                3 -> textRank.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                else -> textRank.setTextColor(itemView.context.getColor(android.R.color.black))
            }
            
            // Kullanıcı adına ve profil fotoğrafına tıklama olayı ekle
            textUsername.setOnClickListener {
                if (!user.userId.isNullOrEmpty()) {
                    onUserClicked(user.userId)
                }
            }
            
            imageProfile.setOnClickListener {
                if (!user.userId.isNullOrEmpty()) {
                    onUserClicked(user.userId)
                }
            }
        }
    }
} 