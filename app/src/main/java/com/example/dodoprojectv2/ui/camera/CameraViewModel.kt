package com.example.dodoprojectv2.ui.camera

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CameraViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "CameraViewModel"

    private val _text = MutableLiveData<String>().apply {
        value = "Bildirimler"
    }
    val text: LiveData<String> = _text
    
    private val _notifications = MutableLiveData<List<NotificationModel>>()
    val notifications: LiveData<List<NotificationModel>> = _notifications
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount
    
    init {
        loadNotifications()
        checkUnreadNotifications()
    }
    
    fun loadNotifications(markAsRead: Boolean = false) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _isEmpty.value = true
            return
        }
        
        _isLoading.value = true
        
        firestore.collection("notifications")
            .whereEqualTo("receiverId", currentUser.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false
                
                if (documents.isEmpty) {
                    _notifications.value = emptyList()
                    _isEmpty.value = true
                    return@addOnSuccessListener
                }
                
                val notificationsList = documents.mapNotNull { doc ->
                    try {
                        NotificationModel(
                            id = doc.id,
                            type = doc.getString("type") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            senderUsername = doc.getString("senderUsername") ?: "",
                            receiverId = doc.getString("receiverId") ?: "",
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0,
                            isRead = doc.getBoolean("isRead") ?: false,
                            relatedItemId = doc.getString("relatedItemId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Bildirim verisi dönüştürülürken hata: ${e.message}", e)
                        null
                    }
                }
                
                _notifications.value = notificationsList
                _isEmpty.value = notificationsList.isEmpty()
                
                // Eğer markAsRead true ise bildirimleri okunmuş olarak işaretle
                if (markAsRead) {
                    // Okunmamış bildirimleri okunmuş olarak işaretle
                    for (notification in notificationsList) {
                        if (!notification.isRead) {
                            markAsRead(notification.id)
                        }
                    }
                }
                
                // Okunmamış bildirimlerin sayısını güncelle
                checkUnreadNotifications()
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Bildirimler yüklenemedi: ${e.message}"
                Log.e(TAG, "Bildirimler yüklenirken hata: ${e.message}", e)
            }
    }
    
    private fun markAsRead(notificationId: String) {
        firestore.collection("notifications")
            .document(notificationId)
            .update("isRead", true)
            .addOnFailureListener { e ->
                Log.e(TAG, "Bildirim okundu olarak işaretlenirken hata: ${e.message}", e)
            }
    }
    
    // Tüm bildirimleri temizle
    fun clearAllNotifications() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Oturum açılmamış"
            return
        }
        
        _isLoading.value = true
        
        firestore.collection("notifications")
            .whereEqualTo("receiverId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()
                
                for (document in documents) {
                    batch.delete(document.reference)
                }
                
                batch.commit()
                    .addOnSuccessListener {
                        _isLoading.value = false
                        _notifications.value = emptyList()
                        _isEmpty.value = true
                        _unreadCount.value = 0
                        Log.d(TAG, "Tüm bildirimler başarıyla silindi")
                        
                        // Badge'in anında güncellenmesi için unreadCount LiveData'sını sıfırla
                        _unreadCount.postValue(0)
                    }
                    .addOnFailureListener { e ->
                        _isLoading.value = false
                        _errorMessage.value = "Bildirimler silinirken hata oluştu: ${e.message}"
                        Log.e(TAG, "Bildirimler silinirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Bildirimler yüklenemedi: ${e.message}"
                Log.e(TAG, "Bildirimler yüklenirken hata: ${e.message}", e)
            }
    }
    
    // Okunmamış bildirimleri kontrol et
    fun checkUnreadNotifications() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _unreadCount.value = 0
            return
        }
        
        firestore.collection("notifications")
            .whereEqualTo("receiverId", currentUser.uid)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { documents ->
                _unreadCount.value = documents.size()
                Log.d(TAG, "Okunmamış bildirim sayısı: ${documents.size()}")
            }
            .addOnFailureListener { e ->
                _unreadCount.value = 0
                Log.e(TAG, "Okunmamış bildirimler kontrol edilirken hata: ${e.message}", e)
            }
    }
    
    // Tüm bildirimleri okunmuş olarak işaretle
    fun markAllAsRead() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return
        }
        
        // Öncelikle badge'i hemen gizle ve kullanıcıya hızlı geribildirim sağla
        _unreadCount.postValue(0)
        
        firestore.collection("notifications")
            .whereEqualTo("receiverId", currentUser.uid)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    return@addOnSuccessListener
                }
                
                val batch = firestore.batch()
                
                for (document in documents) {
                    batch.update(document.reference, "isRead", true)
                }
                
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Tüm bildirimler okundu olarak işaretlendi")
                        
                        // Bildirimleri tekrar yükle
                        loadNotifications(false)
                        
                        // Ekstra güvenlik için unreadCount'u 0 olarak ayarla
                        _unreadCount.postValue(0)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Bildirimler okundu olarak işaretlenirken hata: ${e.message}", e)
                        // Hata durumunda okunmamış bildirimleri tekrar kontrol et
                        checkUnreadNotifications()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Okunmamış bildirimler alınırken hata: ${e.message}", e)
                // Hata durumunda okunmamış bildirimleri tekrar kontrol et
                checkUnreadNotifications()
            }
    }
} 