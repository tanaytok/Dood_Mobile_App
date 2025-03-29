package com.example.dodoprojectv2.ui.leaderboard

/**
 * Skor tablosunda gösterilen kullanıcıların veri modelini temsil eder.
 */
data class LeaderboardUser(
    val userId: String = "",
    val username: String = "",
    val profilePhotoUrl: String = "",
    val score: Int = 0,
    val rank: Int = 0  // Sıralama, adapter tarafından doldurulur
) 