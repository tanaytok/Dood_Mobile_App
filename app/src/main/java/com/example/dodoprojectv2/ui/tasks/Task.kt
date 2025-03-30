package com.example.dodoprojectv2.ui.tasks

/**
 * Görev veri modeli
 */
data class Task(
    val id: String = "",           // Görevin benzersiz kimliği
    val title: String = "",        // Görev başlığı / tanımı
    val totalCount: Int = 1,       // Görevin tamamlanması için gereken toplam fotoğraf sayısı
    var completedCount: Int = 0,   // Kullanıcının şu ana kadar tamamladığı fotoğraf sayısı
    var isCompleted: Boolean = false, // Görevin tamamlanma durumu
    val timestamp: Long = 0,       // Görevin oluşturulma zamanı
    val expiresAt: Long = 0,       // Görevin son kullanma tarihi
    val points: Int = 100,         // Görevin tamamlanmasıyla kazanılacak puan
    val userId: String = "",       // Görevin atandığı kullanıcının ID'si 
    val dateString: String = ""    // Görevin oluşturulduğu tarihin string gösterimi (YYYY-MM-DD)
) 