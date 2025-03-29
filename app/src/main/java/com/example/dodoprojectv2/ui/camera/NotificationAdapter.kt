package com.example.dodoprojectv2.ui.camera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dodoprojectv2.R

class NotificationAdapter(
    private var notifications: List<NotificationModel> = emptyList(),
    private val onNotificationClicked: (NotificationModel) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    override fun getItemCount(): Int = notifications.size

    fun updateNotifications(newNotifications: List<NotificationModel>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageNotificationType: ImageView = itemView.findViewById(R.id.image_notification_type)
        private val textNotificationMessage: TextView = itemView.findViewById(R.id.text_notification_message)
        private val textNotificationDate: TextView = itemView.findViewById(R.id.text_notification_date)

        fun bind(notification: NotificationModel) {
            // Bildirim mesajını ayarla
            textNotificationMessage.text = notification.message
            
            // Bildirim tarihini ayarla
            textNotificationDate.text = notification.getFormattedDate()
            
            // Bildirim türüne göre simgeyi ayarla
            when (notification.type) {
                "follow" -> imageNotificationType.setImageResource(R.drawable.ic_person_add)
                "unfollow" -> imageNotificationType.setImageResource(R.drawable.ic_person_remove)
                "like" -> imageNotificationType.setImageResource(R.drawable.ic_like_filled) // Eğer varsa
                "comment" -> imageNotificationType.setImageResource(R.drawable.ic_notification) // Eğer varsa
                else -> imageNotificationType.setImageResource(R.drawable.ic_notification)
            }
            
            // Öğe tıklama olayını ayarla
            itemView.setOnClickListener {
                onNotificationClicked(notification)
            }
        }
    }
} 