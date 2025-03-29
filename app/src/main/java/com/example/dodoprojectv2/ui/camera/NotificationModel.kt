package com.example.dodoprojectv2.ui.camera

import java.util.Date

data class NotificationModel(
    val id: String = "",
    val type: String = "",  // follow, like, comment vb.
    val senderId: String = "",
    val senderUsername: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,
    val relatedItemId: String = ""  // Örneğin gönderi ID, kullanıcı ID vb.
) {
    fun getFormattedDate(): String {
        val now = Date().time
        val diffMillis = now - timestamp
        
        // Zaman farkını hesapla
        val diffSeconds = diffMillis / 1000
        val diffMinutes = diffSeconds / 60
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24
        
        return when {
            diffDays > 0 -> "$diffDays gün önce"
            diffHours > 0 -> "$diffHours saat önce"
            diffMinutes > 0 -> "$diffMinutes dakika önce"
            else -> "Az önce"
        }
    }
} 