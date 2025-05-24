package com.example.dodoprojectv2.ui

import android.app.Dialog
import android.content.Context
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.ui.profile.PhotoAdapter
import com.example.dodoprojectv2.ui.profile.UserPhoto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

/**
 * Kullanıcı profili popup'ını gösteren utility sınıfı
 */
class UserProfilePopup(private val context: Context) {
    
    private val TAG = "UserProfilePopup"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Kullanıcı profili popup'ını gösterir
     * @param userId Gösterilecek kullanıcının ID'si
     */
    fun showUserProfile(userId: String) {
        if (userId.isBlank()) {
            Log.e(TAG, "showUserProfile: userId boş")
            return
        }
        
        try {
            // Bottom Sheet olarak göstermek için Dialog oluştur
            val dialog = Dialog(context, R.style.BottomSheetDialogTheme)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_user_profile)
            dialog.window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setGravity(android.view.Gravity.BOTTOM)
                attributes.windowAnimations = R.style.BottomSheetAnimation
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            
            // Dialog bileşenlerini bul
            val buttonBack = dialog.findViewById<ImageButton>(R.id.button_back)
            val textTitle = dialog.findViewById<TextView>(R.id.text_title)
            val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar)
            val scrollView = dialog.findViewById<ScrollView>(R.id.scroll_view)
            val imageProfile = dialog.findViewById<CircleImageView>(R.id.image_profile)
            val textUsername = dialog.findViewById<TextView>(R.id.text_username)
            val textBio = dialog.findViewById<TextView>(R.id.text_bio)
            val buttonFollow = dialog.findViewById<Button>(R.id.button_follow)
            val textStreakCount = dialog.findViewById<TextView>(R.id.text_streak_count)
            val textFollowersCount = dialog.findViewById<TextView>(R.id.text_followers_count)
            val textFollowingCount = dialog.findViewById<TextView>(R.id.text_following_count)
            val recyclerViewPhotos = dialog.findViewById<RecyclerView>(R.id.recycler_view_photos)
            val textEmptyPhotos = dialog.findViewById<TextView>(R.id.text_empty_photos)
            
            // Geri butonunu ayarla
            buttonBack.setOnClickListener {
                dialog.dismiss()
            }
            
            // RecyclerView'ı ayarla
            val photoAdapter = PhotoAdapter(emptyList()) { photo ->
                // Fotoğraf detayını göster (isteğe bağlı)
                // showPhotoDetailDialog(photo)
            }
            recyclerViewPhotos.apply {
                layoutManager = GridLayoutManager(context, 3)
                adapter = photoAdapter
            }
            
            // Kullanıcı verilerini yükle
            loadUserData(userId, dialog, progressBar, scrollView, imageProfile, textUsername, 
                        textBio, buttonFollow, textStreakCount, textFollowersCount, 
                        textFollowingCount, photoAdapter, textEmptyPhotos)
            
            // Dialog'u göster
            dialog.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı profili popup gösterilirken hata: ${e.message}", e)
            Toast.makeText(context, "Kullanıcı profili açılamadı", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadUserData(
        userId: String,
        dialog: Dialog,
        progressBar: ProgressBar,
        scrollView: ScrollView,
        imageProfile: CircleImageView,
        textUsername: TextView,
        textBio: TextView,
        buttonFollow: Button,
        textStreakCount: TextView,
        textFollowersCount: TextView,
        textFollowingCount: TextView,
        photoAdapter: PhotoAdapter,
        textEmptyPhotos: TextView
    ) {
        // Yükleniyor durumunu göster
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        
        // Kullanıcı bilgilerini Firestore'dan yükle
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                try {
                    if (document.exists()) {
                        Log.d(TAG, "Kullanıcı verisi bulundu: $userId")
                        
                        val username = document.getString("username") ?: "Kullanıcı"
                        val bio = document.getString("bio") ?: "Biyografi eklenmemiş"
                        val profilePhotoUrl = document.getString("profilePhotoUrl") ?: ""
                        
                        // ProfileFragment ile aynı field adlarını kullan
                        val streakCount = document.getLong("streak")?.toInt() ?: 0
                        val followersCount = document.getLong("followers")?.toInt() ?: 0
                        val followingCount = document.getLong("following")?.toInt() ?: 0
                        
                        Log.d(TAG, "Veriler: username=$username, bio=$bio, streak=$streakCount, followers=$followersCount, following=$followingCount")
                        
                        // UI'ı güncelle
                        textUsername.text = username
                        textBio.text = bio
                        textStreakCount.text = streakCount.toString()
                        textFollowersCount.text = followersCount.toString()
                        textFollowingCount.text = followingCount.toString()
                        
                        // Profil fotoğrafını yükle
                        if (profilePhotoUrl.isNotEmpty()) {
                            Glide.with(context)
                                .load(profilePhotoUrl)
                                .placeholder(R.drawable.default_profile)
                                .into(imageProfile)
                        } else {
                            imageProfile.setImageResource(R.drawable.default_profile)
                        }
                        
                        // Takip butonunu ayarla (kendi profilimiz değilse göster)
                        if (userId != auth.currentUser?.uid) {
                            setupFollowButton(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                        } else {
                            buttonFollow.visibility = View.GONE
                            // Kendi profilimizse fotoğrafları direkt yükle
                            loadUserPhotos(userId, photoAdapter, textEmptyPhotos, true)
                        }
                        
                    } else {
                        Log.e(TAG, "Kullanıcı dokümanı bulunamadı: $userId")
                        Toast.makeText(context, "Kullanıcı bulunamadı", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kullanıcı verisi işlenirken hata: ${e.message}", e)
                    Toast.makeText(context, "Veri yüklenirken hata oluştu", Toast.LENGTH_SHORT).show()
                } finally {
                    progressBar.visibility = View.GONE
                    scrollView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı verisi yüklenirken hata: ${e.message}", e)
                Toast.makeText(context, "Kullanıcı bilgileri yüklenemedi", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                dialog.dismiss()
            }
    }
    
    private fun setupFollowButton(userId: String, buttonFollow: Button, photoAdapter: PhotoAdapter, textEmptyPhotos: TextView) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        buttonFollow.visibility = View.VISIBLE
        
        // ProfileFragment ile aynı follow strukture kullan
        firestore.collection("follows")
            .document("${currentUserId}_${userId}")
            .get()
            .addOnSuccessListener { document ->
                val isFollowing = document.exists()
                
                if (isFollowing) {
                    // Zaten takip ediliyor
                    buttonFollow.text = "Takibi Bırak"
                    buttonFollow.setBackgroundResource(R.drawable.button_purple_outline_background)
                    buttonFollow.setTextColor(context.getColor(R.color.purple))
                    buttonFollow.setOnClickListener {
                        unfollowUser(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                    }
                    // Takip ediyorsak fotoğrafları yükle
                    loadUserPhotos(userId, photoAdapter, textEmptyPhotos, true)
                } else {
                    // Takip edilmiyor
                    buttonFollow.text = "Takip Et"
                    buttonFollow.setBackgroundResource(R.drawable.button_purple_outline_background)
                    buttonFollow.setTextColor(context.getColor(R.color.purple))
                    buttonFollow.setOnClickListener {
                        followUser(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                    }
                    // Takip etmiyorsak fotoğrafları gizle
                    loadUserPhotos(userId, photoAdapter, textEmptyPhotos, false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takip durumu kontrol edilirken hata: ${e.message}", e)
                // Hata durumunda takip etmiyor olarak ayarla
                buttonFollow.text = "Takip Et"
                buttonFollow.setBackgroundResource(R.drawable.button_purple_outline_background)
                buttonFollow.setTextColor(context.getColor(R.color.purple))
                buttonFollow.setOnClickListener {
                    followUser(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                }
                loadUserPhotos(userId, photoAdapter, textEmptyPhotos, false)
            }
    }
    
    private fun followUser(userId: String, buttonFollow: Button, photoAdapter: PhotoAdapter, textEmptyPhotos: TextView) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        buttonFollow.isEnabled = false
        
        // ProfileFragment ile aynı follow structure kullan
        val followId = "${currentUserId}_${userId}"
        val followData = hashMapOf(
            "followerId" to currentUserId,
            "followingId" to userId,
            "timestamp" to System.currentTimeMillis()
        )
        
        firestore.collection("follows")
            .document(followId)
            .set(followData)
            .addOnSuccessListener {
                buttonFollow.text = "Takibi Bırak"
                buttonFollow.setBackgroundResource(R.drawable.button_purple_outline_background)
                buttonFollow.setTextColor(context.getColor(R.color.purple))
                buttonFollow.setOnClickListener {
                    unfollowUser(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                }
                
                // Takip sayısını güncelle
                updateFollowCounts(userId, currentUserId, 1)
                
                // Fotoğrafları yükle
                loadUserPhotos(userId, photoAdapter, textEmptyPhotos, true)
                
                // Bildirim gönder
                sendFollowNotification(userId, currentUserId)
                
                Toast.makeText(context, "Kullanıcı takip edildi", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takip etme işlemi başarısız: ${e.message}", e)
                Toast.makeText(context, "Takip etme işlemi başarısız", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                buttonFollow.isEnabled = true
            }
    }
    
    private fun unfollowUser(userId: String, buttonFollow: Button, photoAdapter: PhotoAdapter, textEmptyPhotos: TextView) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        buttonFollow.isEnabled = false
        
        // ProfileFragment ile aynı follow structure kullan
        val followId = "${currentUserId}_${userId}"
        
        firestore.collection("follows")
            .document(followId)
            .delete()
            .addOnSuccessListener {
                buttonFollow.text = "Takip Et"
                buttonFollow.setBackgroundResource(R.drawable.button_purple_outline_background)
                buttonFollow.setTextColor(context.getColor(R.color.purple))
                buttonFollow.setOnClickListener {
                    followUser(userId, buttonFollow, photoAdapter, textEmptyPhotos)
                }
                
                // Takip sayısını güncelle
                updateFollowCounts(userId, currentUserId, -1)
                
                // Fotoğrafları gizle
                loadUserPhotos(userId, photoAdapter, textEmptyPhotos, false)
                
                // Bildirim gönder
                sendUnfollowNotification(userId, currentUserId)
                
                Toast.makeText(context, "Takip bırakıldı", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takibi bırakma işlemi başarısız: ${e.message}", e)
                Toast.makeText(context, "Takibi bırakma işlemi başarısız", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                buttonFollow.isEnabled = true
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
            }
        
        // Takip eden kullanıcının takip ettiği sayısını güncelle
        firestore.collection("users").document(followerUserId)
            .get()
            .addOnSuccessListener { document ->
                val currentFollowing = document.getLong("following")?.toInt() ?: 0
                val newFollowing = (currentFollowing + increment).coerceAtLeast(0)
                
                firestore.collection("users").document(followerUserId)
                    .update("following", newFollowing)
            }
    }
    
    private fun sendFollowNotification(receiverId: String, senderId: String) {
        // Takip eden kullanıcının bilgilerini al
        firestore.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val senderUsername = document.getString("username") ?: "Bir kullanıcı"
                
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
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
    }
    
    private fun sendUnfollowNotification(receiverId: String, senderId: String) {
        // Takipten çıkan kullanıcının bilgilerini al
        firestore.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val senderUsername = document.getString("username") ?: "Bir kullanıcı"
                
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
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takipten çıkma bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
    }
    
    private fun loadUserPhotos(userId: String, photoAdapter: PhotoAdapter, textEmptyPhotos: TextView, canViewPhotos: Boolean) {
        if (!canViewPhotos) {
            // Takip etmiyorsa fotoğrafları gizle
            textEmptyPhotos.text = "Bu kullanıcının fotoğraflarını görmek için takip edin"
            textEmptyPhotos.visibility = View.VISIBLE
            photoAdapter.updatePhotos(emptyList())
            return
        }
        
        // ProfileFragment ile aynı collection ve field adlarını kullan
        firestore.collection("user_photos")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isPublic", true)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(12) // Son 12 fotoğrafı göster
            .get()
            .addOnSuccessListener { documents ->
                val photos = mutableListOf<UserPhoto>()
                
                for (document in documents) {
                    try {
                        val photoUrl = document.getString("photoUrl")
                        val taskName = document.getString("taskName")
                        val timestamp = document.getLong("timestamp")
                        val taskId = document.getString("taskId")
                        
                        if (!photoUrl.isNullOrEmpty()) {
                            photos.add(UserPhoto(
                                id = document.id,
                                photoUrl = photoUrl,
                                taskId = taskId ?: "",
                                taskName = taskName ?: "Görev",
                                timestamp = timestamp ?: 0L,
                                userId = userId
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fotoğraf verisi dönüştürülürken hata: ${e.message}", e)
                    }
                }
                
                if (photos.isEmpty()) {
                    textEmptyPhotos.text = "Henüz fotoğraf yok"
                    textEmptyPhotos.visibility = View.VISIBLE
                } else {
                    textEmptyPhotos.visibility = View.GONE
                    photoAdapter.updatePhotos(photos)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı fotoğrafları yüklenirken hata: ${e.message}", e)
                textEmptyPhotos.text = "Fotoğraflar yüklenemedi"
                textEmptyPhotos.visibility = View.VISIBLE
            }
    }
} 