package com.example.dodoprojectv2.ui.home

import java.util.Date

data class CommentModel(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val userProfileUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0
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