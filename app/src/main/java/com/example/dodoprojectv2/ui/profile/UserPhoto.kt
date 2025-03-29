package com.example.dodoprojectv2.ui.profile

/**
 * Kullanıcının beğendiği veya paylaştığı fotoğrafların görüntülenmesi için kullanılan veri sınıfı
 */
data class UserPhoto(
    val id: String = "",               // Fotoğrafın benzersiz kimliği
    val photoUrl: String = "",         // Fotoğrafın depolama URL'si
    val taskId: String = "",           // Fotoğrafın ilişkili olduğu görevin kimliği
    val taskName: String = "",         // Fotoğrafın ilişkili olduğu görevin adı
    val timestamp: Long = 0,           // Fotoğrafın paylaşıldığı/beğenildiği zaman
    val userId: String = ""            // Fotoğrafı paylaşan/beğenen kullanıcının kimliği
) 