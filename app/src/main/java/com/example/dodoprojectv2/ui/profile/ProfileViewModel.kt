package com.example.dodoprojectv2.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProfileViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    // Username
    private val _username = MutableLiveData<String>()
    val username: LiveData<String> = _username
    
    // Bio
    private val _bio = MutableLiveData<String>()
    val bio: LiveData<String> = _bio
    
    // Profile Image URL
    private val _profileImageUrl = MutableLiveData<String>()
    val profileImageUrl: LiveData<String> = _profileImageUrl
    
    // Streak count
    private val _streak = MutableLiveData<Int>()
    val streak: LiveData<Int> = _streak
    
    // Followers count
    private val _followers = MutableLiveData<Int>()
    val followers: LiveData<Int> = _followers
    
    // Following count
    private val _following = MutableLiveData<Int>()
    val following: LiveData<Int> = _following
    
    // User Photos
    private val _photos = MutableLiveData<List<UserPhoto>>()
    val photos: LiveData<List<UserPhoto>> = _photos
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        loadUserData()
    }
    
    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            return
        }
        
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    _username.value = document.getString("username") ?: ""
                    _bio.value = document.getString("bio") ?: "Biyografi eklenmemiş"
                    _profileImageUrl.value = document.getString("profilePhotoUrl") ?: ""
                    _streak.value = document.getLong("streak")?.toInt() ?: 0
                    _followers.value = document.getLong("followers")?.toInt() ?: 0
                    _following.value = document.getLong("following")?.toInt() ?: 0
                } else {
                    _errorMessage.value = "Kullanıcı verisi bulunamadı"
                }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = e.message
            }
    }
    
    fun loadUserPhotos() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            return
        }
        
        _isLoading.value = true
        
        firestore.collection("user_photos")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isPublic", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20) // Son 20 fotoğrafı getir
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false
                
                if (documents.isEmpty) {
                    _photos.value = emptyList()
                    return@addOnSuccessListener
                }
                
                val photosList = documents.mapNotNull { doc ->
                    try {
                        UserPhoto(
                            id = doc.id,
                            photoUrl = doc.getString("photoUrl") ?: "",
                            taskId = doc.getString("taskId") ?: "",
                            taskName = doc.getString("taskName") ?: "Görev",
                            timestamp = doc.getLong("timestamp") ?: 0,
                            userId = doc.getString("userId") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                _photos.value = photosList
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.message
            }
    }
    
    fun updateBio(newBio: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            return
        }
        
        if (newBio.length > 80) {
            _errorMessage.value = "Biyografi en fazla 80 karakter olabilir"
            return
        }
        
        firestore.collection("users").document(userId)
            .update("bio", newBio)
            .addOnSuccessListener {
                _bio.value = newBio
            }
            .addOnFailureListener { e ->
                _errorMessage.value = e.message
            }
    }
} 