package com.example.dodoprojectv2.ui.leaderboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*
import java.util.concurrent.TimeUnit

class LeaderboardViewModel : ViewModel() {

    private val TAG = "LeaderboardViewModel"
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Skor tablosu kullanıcıları
    private val _leaderboardUsers = MutableLiveData<List<LeaderboardUser>>()
    val leaderboardUsers: LiveData<List<LeaderboardUser>> = _leaderboardUsers
    
    // Boş durum kontrolü
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty
    
    // Yükleniyor durumu
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Hata mesajları
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Sıfırlanmaya kalan süre
    private val _timeRemaining = MutableLiveData<String>()
    val timeRemaining: LiveData<String> = _timeRemaining
    
    init {
        loadLeaderboard()
        calculateTimeRemaining()
    }
    
    fun loadLeaderboard() {
        _isLoading.value = true
        Log.d(TAG, "Skor tablosu yükleniyor")
        
        // Mevcut kullanıcı ID'sini al
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _isLoading.value = false
            _isEmpty.value = true
            _errorMessage.value = "Kullanıcı girişi yapılmamış"
            Log.e(TAG, "Kullanıcı girişi yapılmamış")
            return
        }
        
        // Takip sisteminin çalışıp çalışmadığını kontrol edelim
        firestore.collection("follows")
            .whereEqualTo("followerId", currentUserId)
            .limit(1) // Sadece bir kayıt olup olmadığını kontrol etmek için
            .get()
            .addOnSuccessListener { followDocs ->
                if (followDocs.isEmpty) {
                    // Takip kaydı yoksa, boş liste göster
                    _isLoading.value = false
                    _isEmpty.value = true
                    _leaderboardUsers.value = emptyList()
                    Log.d(TAG, "Takip kaydı bulunamadı, boş liste gösteriliyor")
                } else {
                    // Takip kaydı varsa, takip edilen kullanıcıları göster
                    loadFollowedUsers(currentUserId)
                    Log.d(TAG, "Takip kaydı bulundu, takip edilen kullanıcılar gösteriliyor")
                }
            }
            .addOnFailureListener { e ->
                // Hata durumunda boş liste göster
                _isLoading.value = false
                _isEmpty.value = true
                _leaderboardUsers.value = emptyList()
                _errorMessage.value = "Takip bilgisi alınamadı: ${e.message}"
                Log.e(TAG, "Takip bilgisi alınamadı: ${e.message}")
            }
    }
    
    // Takip edilen kullanıcıları yükle
    private fun loadFollowedUsers(currentUserId: String) {
        firestore.collection("follows")
            .whereEqualTo("followerId", currentUserId)
            .get()
            .addOnSuccessListener { followDocs ->
                Log.d(TAG, "Takip edilen kullanıcılar alındı, sayı: ${followDocs.size()}")
                
                // Takip edilen kullanıcı ID'lerini topla
                val followingIds = mutableSetOf<String>()
                
                // Kendi ID'mizi de ekle (kendimizi her zaman görmeliyiz)
                followingIds.add(currentUserId)
                
                // Takip edilen kullanıcıları ekle
                for (doc in followDocs) {
                    val followedId = doc.getString("followingId")
                    if (followedId != null && followedId.isNotEmpty()) {
                        followingIds.add(followedId)
                    }
                }
                
                Log.d(TAG, "Takip edilen kullanıcı sayısı: ${followingIds.size}")
                
                loadUsersByIds(followingIds)
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _isEmpty.value = true
                _errorMessage.value = "Takip edilen kullanıcılar alınamadı: ${e.message}"
                Log.e(TAG, "Takip edilen kullanıcılar alınamadı: ${e.message}")
            }
    }
    
    // Tüm kullanıcıları yükle
    private fun loadAllUsers(currentUserId: String) {
        firestore.collection("users")
            .get()
            .addOnSuccessListener { userDocs ->
                val users = mutableListOf<LeaderboardUser>()
                
                if (userDocs.isEmpty) {
                    _isEmpty.value = true
                    _isLoading.value = false
                    _leaderboardUsers.value = emptyList()
                    Log.d(TAG, "Hiç kullanıcı bulunamadı")
                    return@addOnSuccessListener
                }
                
                Log.d(TAG, "Tüm kullanıcılar alındı, sayı: ${userDocs.size()}")
                
                // Tüm kullanıcıları ekle (test için)
                for (userDoc in userDocs) {
                    val userId = userDoc.id
                    val username = userDoc.getString("username") ?: "İsimsiz Kullanıcı"
                    val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                    
                    // Tüm kullanıcıların puanı 0 olarak ekleniyor
                    val user = LeaderboardUser(
                        userId = userId,
                        username = username,
                        profilePhotoUrl = profilePhotoUrl,
                        score = 0
                    )
                    users.add(user)
                }
                
                // Kullanıcıları alfabetik sırala
                val sortedUsers = users.sortedBy { it.username.lowercase() }
                
                _leaderboardUsers.value = sortedUsers
                _isEmpty.value = sortedUsers.isEmpty()
                _isLoading.value = false
                
                Log.d(TAG, "Skor tablosu yüklendi, kullanıcı sayısı: ${sortedUsers.size}")
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _isEmpty.value = true
                _errorMessage.value = "Kullanıcılar yüklenemedi: ${e.message}"
                Log.e(TAG, "Kullanıcılar yüklenemedi: ${e.message}")
            }
    }
    
    // Belirli ID'lere sahip kullanıcıları yükle (10'ar 10'ar sorgu yapar)
    private fun loadUsersByIds(userIds: Set<String>) {
        val users = mutableListOf<LeaderboardUser>()
        var processedUsers = 0
        
        // userIds'i 10'ar 10'ar parçalara ayır (whereIn en fazla 10 değer alabilir)
        val chunks = userIds.chunked(10)
        
        for (chunk in chunks) {
            firestore.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { userDocs ->
                    processedUsers += userDocs.size()
                    
                    // Kullanıcı bilgilerini al
                    for (userDoc in userDocs) {
                        val userId = userDoc.id
                        val username = userDoc.getString("username") ?: "İsimsiz Kullanıcı"
                        val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                        
                        // Tüm kullanıcıların puanı 0 olarak ekleniyor
                        val user = LeaderboardUser(
                            userId = userId,
                            username = username,
                            profilePhotoUrl = profilePhotoUrl,
                            score = 0
                        )
                        users.add(user)
                    }
                    
                    // Tüm sorgular tamamlandıysa, sonuçları göster
                    if (processedUsers >= userIds.size) {
                        // Kullanıcıları alfabetik sırala
                        val sortedUsers = users.sortedBy { it.username.lowercase() }
                        
                        _leaderboardUsers.value = sortedUsers
                        _isEmpty.value = sortedUsers.isEmpty()
                        _isLoading.value = false
                        
                        Log.d(TAG, "Skor tablosu yüklendi, kullanıcı sayısı: ${sortedUsers.size}")
                    }
                }
                .addOnFailureListener { e ->
                    _isLoading.value = false
                    _errorMessage.value = "Kullanıcılar yüklenemedi: ${e.message}"
                    Log.e(TAG, "Kullanıcılar yüklenemedi: ${e.message}")
                }
        }
    }
    
    private fun calculateTimeRemaining() {
        val currentTime = Calendar.getInstance()
        val nextReset = Calendar.getInstance().apply {
            // Pazar günü 23:59 olarak ayarla
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            
            // Eğer bugün pazar ve saat 23:59'dan sonraysa, bir sonraki haftaya geç
            if (currentTime.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && 
                currentTime.get(Calendar.HOUR_OF_DAY) >= 23 && 
                currentTime.get(Calendar.MINUTE) >= 59) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
            
            // Eğer bugün pazardan sonraki bir günse, bir sonraki haftaya geç
            if (currentTime.get(Calendar.DAY_OF_WEEK) > Calendar.SUNDAY) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        
        // Kalan süreyi hesapla
        val remainingMillis = nextReset.timeInMillis - currentTime.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis) % 24
        
        _timeRemaining.value = "Sıfırlanmaya kalan süre: $days gün $hours saat"
    }
}
