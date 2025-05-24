package com.example.dodoprojectv2.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HomeViewModel : ViewModel() {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "HomeViewModel"
    
    // Arama sonuçları
    private val _searchResults = MutableLiveData<List<UserModel>>()
    val searchResults: LiveData<List<UserModel>> = _searchResults
    
    // Akış gönderileri
    private val _feedPosts = MutableLiveData<List<PostModel>>()
    val feedPosts: LiveData<List<PostModel>> = _feedPosts
    
    // Yükleniyor durumu
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Arama sonuçları boş durumu
    private val _isEmptySearchResults = MutableLiveData<Boolean>()
    val isEmptySearchResults: LiveData<Boolean> = _isEmptySearchResults
    
    // Akış boş durumu
    private val _isEmptyFeed = MutableLiveData<Boolean>()
    val isEmptyFeed: LiveData<Boolean> = _isEmptyFeed
    
    // Hata mesajı
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Arama durumu
    private val _isSearchMode = MutableLiveData<Boolean>()
    val isSearchMode: LiveData<Boolean> = _isSearchMode
    
    init {
        _isLoading.value = false
        _isEmptySearchResults.value = false
        _isEmptyFeed.value = false
        _isSearchMode.value = false
        loadFeedPosts()
    }
    
    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isEmptySearchResults.value = true
            _isSearchMode.value = false
            return
        }
        
        _isLoading.value = true
        _isSearchMode.value = true
        
        // Kullanıcı adı sorgusunu kullan (büyük/küçük harfe duyarsız arama için IndexedDB kuralları ayarlanmalı)
        firestore.collection("users")
            .orderBy("username")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false
                
                if (documents.isEmpty) {
                    _searchResults.value = emptyList()
                    _isEmptySearchResults.value = true
                    return@addOnSuccessListener
                }
                
                val usersList = documents.mapNotNull { doc ->
                    try {
                        UserModel(
                            userId = doc.id,
                            username = doc.getString("username") ?: "",
                            bio = doc.getString("bio") ?: "",
                            profilePhotoUrl = doc.getString("profilePhotoUrl") ?: "",
                            followers = doc.getLong("followers")?.toInt() ?: 0,
                            following = doc.getLong("following")?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Kullanıcı verisi dönüştürülürken hata: ${e.message}", e)
                        null
                    }
                }
                
                _searchResults.value = usersList
                _isEmptySearchResults.value = usersList.isEmpty()
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Kullanıcılar aranamadı: ${e.message}"
                Log.e(TAG, "Kullanıcılar aranırken hata: ${e.message}", e)
            }
    }
    
    fun clearSearch() {
        _searchResults.value = emptyList()
        _isEmptySearchResults.value = false
        _isSearchMode.value = false
    }
    
    fun loadFeedPosts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _isEmptyFeed.value = true
            return
        }
        
        _isLoading.value = true
        
        // Önce kullanıcının takip ettiği kullanıcıları bul
        firestore.collection("follows")
            .whereEqualTo("followerId", currentUser.uid)
            .get()
            .addOnSuccessListener { followDocuments ->
                // Takip edilen kullanıcı ID'leri
                val followingIds = followDocuments.documents.mapNotNull { doc ->
                    doc.getString("followingId")
                }.toMutableList()
                
                // Kendi gönderilerini de göster
                followingIds.add(currentUser.uid)
                
                if (followingIds.isEmpty()) {
                    _isLoading.value = false
                    _isEmptyFeed.value = true
                    _feedPosts.value = emptyList()
                    return@addOnSuccessListener
                }
                
                // Takip edilen kullanıcıların ve kendi gönderilerini getir
                firestore.collection("user_photos")
                    .whereIn("userId", followingIds)
                    .whereEqualTo("isPublic", true)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50) // Son 50 gönderi
                    .get()
                    .addOnSuccessListener { photoDocuments ->
                        _isLoading.value = false
                        
                        if (photoDocuments.isEmpty) {
                            _isEmptyFeed.value = true
                            _feedPosts.value = emptyList()
                            return@addOnSuccessListener
                        }
                        
                        // Kullanıcı bilgilerini depolamak için map
                        val userInfoMap = mutableMapOf<String, Pair<String, String>>() // userId -> (username, profilePhotoUrl)
                        
                        // Önce tüm kullanıcı bilgilerini almak için kullanıcı ID'lerini topla
                        val userIds = photoDocuments.documents.mapNotNull { it.getString("userId") }.distinct()
                        
                        // Kullanıcı bilgilerini yükle
                        firestore.collection("users")
                            .whereIn("userId", userIds)
                            .get()
                            .addOnSuccessListener { userDocuments ->
                                // Kullanıcı bilgilerini map'e kaydet
                                for (userDoc in userDocuments) {
                                    val userId = userDoc.getString("userId") ?: continue
                                    val username = userDoc.getString("username") ?: ""
                                    val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                                    userInfoMap[userId] = Pair(username, profilePhotoUrl)
                                }
                                
                                // Şimdi gönderileri oluştur
                                val posts = photoDocuments.mapNotNull { doc ->
                                    try {
                                        val userId = doc.getString("userId") ?: return@mapNotNull null
                                        val userInfo = userInfoMap[userId] ?: Pair("", "")
                                        
                                        PostModel(
                                            postId = doc.id,
                                            userId = userId,
                                            username = userInfo.first,
                                            userProfileUrl = userInfo.second,
                                            photoUrl = doc.getString("photoUrl") ?: "",
                                            taskId = doc.getString("taskId") ?: "",
                                            taskName = doc.getString("taskName") ?: "Görev",
                                            timestamp = doc.getLong("timestamp") ?: 0,
                                            likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
                                            commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
                                            isPublic = doc.getBoolean("isPublic") ?: true
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Gönderi verisi dönüştürülürken hata: ${e.message}", e)
                                        null
                                    }
                                }
                                
                                _feedPosts.value = posts
                                _isEmptyFeed.value = posts.isEmpty()
                            }
                            .addOnFailureListener { e ->
                                _isLoading.value = false
                                _errorMessage.value = "Kullanıcı bilgileri yüklenemedi: ${e.message}"
                                Log.e(TAG, "Kullanıcı bilgileri yüklenirken hata: ${e.message}", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        _isLoading.value = false
                        _errorMessage.value = "Gönderiler yüklenemedi: ${e.message}"
                        Log.e(TAG, "Gönderiler yüklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Takip edilen kullanıcılar yüklenemedi: ${e.message}"
                Log.e(TAG, "Takip edilen kullanıcılar yüklenirken hata: ${e.message}", e)
            }
    }
    
    fun followUser(userId: String, isFollowing: Boolean) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Oturum açılmamış"
            return
        }
        
        val followId = "${currentUser.uid}_${userId}"
        
        if (isFollowing) {
            // Kullanıcıyı takip et
            val followData = hashMapOf(
                "followerId" to currentUser.uid,
                "followingId" to userId,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("follows")
                .document(followId)
                .set(followData)
                .addOnSuccessListener {
                    // Takipçi ve takip sayılarını güncelle
                    updateFollowCounts(userId, currentUser.uid, 1)
                    
                    // Bildirim ekle
                    sendFollowNotification(userId, currentUser.uid)
                    
                    Log.d(TAG, "Kullanıcı takip edildi: $userId")
                }
                .addOnFailureListener { e ->
                    _errorMessage.value = "Takip edilemedi: ${e.message}"
                    Log.e(TAG, "Kullanıcı takip edilirken hata: ${e.message}", e)
                }
        } else {
            // Takipten çık
            firestore.collection("follows")
                .document(followId)
                .delete()
                .addOnSuccessListener {
                    // Takipçi ve takip sayılarını güncelle
                    updateFollowCounts(userId, currentUser.uid, -1)
                    
                    // Takipten çıkma bildirimi ekle
                    sendUnfollowNotification(userId, currentUser.uid)
                    
                    Log.d(TAG, "Takip bırakıldı: $userId")
                }
                .addOnFailureListener { e ->
                    _errorMessage.value = "Takipten çıkılamadı: ${e.message}"
                    Log.e(TAG, "Takipten çıkılırken hata: ${e.message}", e)
                }
        }
    }
    
    private fun updateFollowCounts(followedUserId: String, followerUserId: String, increment: Int) {
        // Takip edilen kullanıcının takipçi sayısını güncelle
        firestore.collection("users").document(followedUserId)
            .get()
            .addOnSuccessListener { document ->
                val currentFollowers = document.getLong("followers")?.toInt() ?: 0
                val newFollowers = (currentFollowers + increment).coerceAtLeast(0)
                
                firestore.collection("users").document(followedUserId)
                    .update("followers", newFollowers)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takipçi sayısı güncellenirken hata: ${e.message}", e)
                    }
            }
        
        // Takip eden kullanıcının takip ettiği sayısını güncelle
        firestore.collection("users").document(followerUserId)
            .get()
            .addOnSuccessListener { document ->
                val currentFollowing = document.getLong("following")?.toInt() ?: 0
                val newFollowing = (currentFollowing + increment).coerceAtLeast(0)
                
                firestore.collection("users").document(followerUserId)
                    .update("following", newFollowing)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip sayısı güncellenirken hata: ${e.message}", e)
                    }
            }
    }
    
    fun likePost(postId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Oturum açılmamış"
            return
        }
        
        val likeId = "${currentUser.uid}_${postId}"
        
        // Önce beğeni durumunu kontrol et
        firestore.collection("post_likes")
            .document(likeId)
            .get()
            .addOnSuccessListener { document ->
                val alreadyLiked = document.exists()
                
                if (alreadyLiked) {
                    // Beğeniyi kaldır
                    firestore.collection("post_likes")
                        .document(likeId)
                        .delete()
                        .addOnSuccessListener {
                            // Beğeni sayısını azalt
                            updatePostLikeCount(postId, -1)
                        }
                } else {
                    // Beğeni ekle
                    val likeData = hashMapOf(
                        "userId" to currentUser.uid,
                        "postId" to postId,
                        "timestamp" to System.currentTimeMillis()
                    )
                    
                    firestore.collection("post_likes")
                        .document(likeId)
                        .set(likeData)
                        .addOnSuccessListener {
                            // Beğeni sayısını artır
                            updatePostLikeCount(postId, 1)
                            
                            // Beğeni bildirimi gönder
                            // Fotoğrafın sahibini bul ve bildirim gönder
                            firestore.collection("user_photos").document(postId)
                                .get()
                                .addOnSuccessListener { photoDoc ->
                                    val photoOwnerId = photoDoc.getString("userId")
                                    if (photoOwnerId != null && photoOwnerId != currentUser.uid) {
                                        // Kendi gönderimizi beğenirsek bildirim gönderme
                                        sendLikeNotification(photoOwnerId, currentUser.uid, postId)
                                    }
                                }
                        }
                }
            }
    }
    
    private fun updatePostLikeCount(postId: String, increment: Int) {
        firestore.collection("user_photos").document(postId)
            .get()
            .addOnSuccessListener { document ->
                val currentLikes = document.getLong("likesCount")?.toInt() ?: 0
                val newLikes = (currentLikes + increment).coerceAtLeast(0)
                
                firestore.collection("user_photos").document(postId)
                    .update("likesCount", newLikes)
                    .addOnSuccessListener {
                        // Görsel güncellemeyi zaten kullanıcı arayüzünde yapıldığı için
                        // tüm akışı yeniden yüklemeye gerek yok
                        // Ancak veritabanına gerçek beğeni sayısını kaydediyoruz
                    }
            }
    }
    
    // Takip bildirimi gönder
    private fun sendFollowNotification(receiverId: String, senderId: String) {
        // Takip eden kullanıcının bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", senderId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "Takip eden kullanıcı bulunamadı")
                    return@addOnSuccessListener
                }
                
                val senderDoc = documents.documents.first()
                val senderUsername = senderDoc.getString("username") ?: "Bir kullanıcı"
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "follow",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername sizi takip etmeye başladı!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Takip bildirimi eklendi")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takip eden kullanıcı getirilirken hata: ${e.message}", e)
            }
    }
    
    // Takipten çıkma bildirimi gönder
    private fun sendUnfollowNotification(receiverId: String, senderId: String) {
        // Takipten çıkan kullanıcının bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", senderId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "Takipten çıkan kullanıcı bulunamadı")
                    return@addOnSuccessListener
                }
                
                val senderDoc = documents.documents.first()
                val senderUsername = senderDoc.getString("username") ?: "Bir kullanıcı"
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "unfollow",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername sizi takip etmeyi bıraktı!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Takipten çıkma bildirimi eklendi")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takipten çıkma bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takipten çıkan kullanıcı getirilirken hata: ${e.message}", e)
            }
    }
    
    // Beğeni bildirimi gönder
    private fun sendLikeNotification(receiverId: String, senderId: String, postId: String) {
        // Beğenen kullanıcının bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", senderId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "Beğenen kullanıcı bulunamadı")
                    return@addOnSuccessListener
                }
                
                val senderDoc = documents.documents.first()
                val senderUsername = senderDoc.getString("username") ?: "Bir kullanıcı"
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "like",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername fotoğrafınızı beğendi!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "relatedItemId" to postId
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Beğeni bildirimi eklendi")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Beğeni bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Beğenen kullanıcı getirilirken hata: ${e.message}", e)
            }
    }
    
    // Yorum bildirimi gönder
    private fun sendCommentNotification(receiverId: String, senderId: String, postId: String, commentText: String) {
        // Yorum yapan kullanıcının bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", senderId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.e(TAG, "Yorum yapan kullanıcı bulunamadı")
                    return@addOnSuccessListener
                }
                
                val senderDoc = documents.documents.first()
                val senderUsername = senderDoc.getString("username") ?: "Bir kullanıcı"
                
                // Yorum metni çok uzunsa kısalt
                val shortenedComment = if (commentText.length > 30) 
                    "${commentText.substring(0, 30)}..." 
                else 
                    commentText
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "comment",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername fotoğrafınıza yorum yaptı: \"$shortenedComment\"",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "relatedItemId" to postId
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Yorum bildirimi eklendi")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Yorum bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Yorum yapan kullanıcı getirilirken hata: ${e.message}", e)
            }
    }
    
    // Yorum sayısını güncelle
    private fun updatePostCommentCount(postId: String, increment: Int) {
        firestore.collection("user_photos").document(postId)
            .get()
            .addOnSuccessListener { document ->
                val currentComments = document.getLong("commentsCount")?.toInt() ?: 0
                val newComments = (currentComments + increment).coerceAtLeast(0)
                
                firestore.collection("user_photos").document(postId)
                    .update("commentsCount", newComments)
                    .addOnSuccessListener {
                        Log.d(TAG, "Gönderi yorum sayısı güncellendi")
                    }
            }
    }
    
    // Gönderi yorumlarını yükle
    private val _comments = MutableLiveData<List<CommentModel>>()
    val comments: LiveData<List<CommentModel>> = _comments
    
    private val _isLoadingComments = MutableLiveData<Boolean>()
    val isLoadingComments: LiveData<Boolean> = _isLoadingComments
    
    private val _isEmptyComments = MutableLiveData<Boolean>()
    val isEmptyComments: LiveData<Boolean> = _isEmptyComments
    
    fun loadComments(postId: String) {
        _isLoadingComments.value = true
        _isEmptyComments.value = false
        
        try {
            // İndeks hatası alındığında, Firebase sorgu yolunu değiştirelim
            // İndeks oluşturmak için Firebase konsolunu kullanın:
            // https://console.firebase.google.com/project/[PROJECT_ID]/firestore/indexes
            
            firestore.collection("post_comments")
                .whereEqualTo("postId", postId)
                .get()
                .addOnSuccessListener { documents ->
                    _isLoadingComments.value = false
                    
                    if (documents.isEmpty) {
                        _comments.value = emptyList()
                        _isEmptyComments.value = true
                        return@addOnSuccessListener
                    }
                    
                    // Kullanıcı bilgilerini depolamak için map
                    val userInfoMap = mutableMapOf<String, Pair<String, String>>() // userId -> (username, profilePhotoUrl)
                    
                    // Önce tüm kullanıcı bilgilerini almak için kullanıcı ID'lerini topla
                    val userIds = documents.documents.mapNotNull { it.getString("userId") }.distinct()
                    
                    if (userIds.isEmpty()) {
                        _comments.value = emptyList()
                        _isEmptyComments.value = true
                        return@addOnSuccessListener
                    }
                    
                    // Kullanıcı bilgilerini yükle
                    firestore.collection("users")
                        .whereIn("userId", userIds)
                        .get()
                        .addOnSuccessListener { userDocuments ->
                            // Kullanıcı bilgilerini map'e kaydet
                            for (userDoc in userDocuments) {
                                val userId = userDoc.getString("userId") ?: continue
                                val username = userDoc.getString("username") ?: ""
                                val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                                userInfoMap[userId] = Pair(username, profilePhotoUrl)
                            }
                            
                            // Şimdi yorumları oluştur
                            val commentsList = documents.mapNotNull { doc ->
                                try {
                                    val userId = doc.getString("userId") ?: return@mapNotNull null
                                    val userInfo = userInfoMap[userId] ?: Pair("", "")
                                    
                                    CommentModel(
                                        id = doc.id,
                                        postId = doc.getString("postId") ?: "",
                                        userId = userId,
                                        username = userInfo.first,
                                        userProfileUrl = userInfo.second,
                                        text = doc.getString("text") ?: "",
                                        timestamp = doc.getLong("timestamp") ?: 0
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Yorum verisi dönüştürülürken hata: ${e.message}", e)
                                    null
                                }
                            }
                            
                            // Yorumları zaman damgasına göre manuel olarak sırala (en yeniden en eskiye)
                            val sortedComments = commentsList.sortedByDescending { it.timestamp }
                            _comments.value = sortedComments
                            _isEmptyComments.value = sortedComments.isEmpty()
                        }
                        .addOnFailureListener { e ->
                            _isLoadingComments.value = false
                            _errorMessage.value = "Kullanıcı bilgileri yüklenemedi: ${e.message}"
                            Log.e(TAG, "Kullanıcı bilgileri yüklenirken hata: ${e.message}", e)
                        }
                }
                .addOnFailureListener { e ->
                    _isLoadingComments.value = false
                    _errorMessage.value = "Yorumlar yüklenemedi: ${e.message}"
                    Log.e(TAG, "Yorumlar yüklenirken hata: ${e.message}", e)
                    
                    // Firestore indeks hatası loglama
                    if (e.message?.contains("FAILED_PRECONDITION") == true || 
                        e.message?.contains("index") == true) {
                        Log.e(TAG, "Firebase indeks hatası. Firebase konsolunda indeks oluşturun.", e)
                    }
                }
        } catch (e: Exception) {
            _isLoadingComments.value = false
            _errorMessage.value = "Bir hata oluştu: ${e.message}"
            Log.e(TAG, "Yorumlar yüklenirken beklenmeyen hata: ${e.message}", e)
        }
    }
    
    fun addComment(postId: String, commentText: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Oturum açılmamış"
            return
        }
        
        if (commentText.trim().isEmpty()) {
            _errorMessage.value = "Yorum boş olamaz"
            return
        }
        
        _isLoadingComments.value = true
        
        // Önce kullanıcı bilgilerini alalım
        firestore.collection("users")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { userDocuments ->
                if (userDocuments.isEmpty) {
                    _errorMessage.value = "Kullanıcı bilgileri alınamadı"
                    _isLoadingComments.value = false
                    return@addOnSuccessListener
                }
                
                val userDoc = userDocuments.documents.first()
                val username = userDoc.getString("username") ?: ""
                val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                
                val commentData = hashMapOf(
                    "postId" to postId,
                    "userId" to currentUser.uid,
                    "text" to commentText.trim(),
                    "timestamp" to System.currentTimeMillis()
                )
                
                firestore.collection("post_comments")
                    .add(commentData)
                    .addOnSuccessListener { documentReference ->
                        // Yorum eklendi, şimdi yorum sayısını güncelle
                        updatePostCommentCount(postId, 1)
                        
                        // Yorumlar listesine yeni yorumu hemen ekle (UI için)
                        val newComment = CommentModel(
                            id = documentReference.id,
                            postId = postId,
                            userId = currentUser.uid,
                            username = username,
                            userProfileUrl = profilePhotoUrl,
                            text = commentText.trim(),
                            timestamp = System.currentTimeMillis()
                        )
                        
                        // Mevcut yorumları al ve en başa yeni yorumu ekle
                        val currentComments = _comments.value ?: emptyList()
                        val updatedComments = listOf(newComment) + currentComments
                        _comments.value = updatedComments
                        _isEmptyComments.value = false
                        _isLoadingComments.value = false
                        
                        // Fotoğraf sahibini bul ve bildirim gönder
                        firestore.collection("user_photos").document(postId)
                            .get()
                            .addOnSuccessListener { photoDoc ->
                                val photoOwnerId = photoDoc.getString("userId")
                                if (photoOwnerId != null && photoOwnerId != currentUser.uid) {
                                    // Kendi gönderimize yorum yaparsak bildirim gönderme
                                    sendCommentNotification(photoOwnerId, currentUser.uid, postId, commentText.trim())
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        _errorMessage.value = "Yorum eklenemedi: ${e.message}"
                        _isLoadingComments.value = false
                        Log.e(TAG, "Yorum eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Kullanıcı bilgileri alınamadı: ${e.message}"
                _isLoadingComments.value = false
                Log.e(TAG, "Kullanıcı bilgileri alınırken hata: ${e.message}", e)
            }
    }
    
    /**
     * Bir gönderiye beğeni yapan kullanıcıları yükler
     */
    fun loadPostLikes(postId: String, callback: (List<LikeUserModel>, String?) -> Unit) {
        _isLoading.value = true
        
        firestore.collection("post_likes")
            .whereEqualTo("postId", postId)
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false
                
                if (documents.isEmpty) {
                    callback(emptyList(), null)
                    return@addOnSuccessListener
                }
                
                // Kullanıcı ID'lerini topla
                val userIds = documents.documents.mapNotNull { it.getString("userId") }.distinct()
                
                if (userIds.isEmpty()) {
                    callback(emptyList(), null)
                    return@addOnSuccessListener
                }
                
                // Kullanıcı bilgilerini yükle
                firestore.collection("users")
                    .whereIn("userId", userIds)
                    .get()
                    .addOnSuccessListener { userDocuments ->
                        // Kullanıcı bilgilerini eşleştir
                        val usersMap = userDocuments.documents.associateBy(
                            { it.getString("userId") ?: "" },
                            { doc ->
                                LikeUserModel(
                                    userId = doc.getString("userId") ?: "",
                                    username = doc.getString("username") ?: "",
                                    profilePhotoUrl = doc.getString("profilePhotoUrl") ?: ""
                                )
                            }
                        )
                        
                        // Tüm beğenenleri kullanıcı bilgileriyle birleştir
                        val likeUsers = userIds.mapNotNull { userId -> usersMap[userId] }
                        
                        callback(likeUsers, null)
                    }
                    .addOnFailureListener { e ->
                        _isLoading.value = false
                        Log.e(TAG, "Kullanıcı bilgilerini yüklerken hata: ${e.message}", e)
                        callback(emptyList(), e.message)
                    }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                Log.e(TAG, "Beğeni bilgilerini yüklerken hata: ${e.message}", e)
                callback(emptyList(), e.message)
            }
    }
} 