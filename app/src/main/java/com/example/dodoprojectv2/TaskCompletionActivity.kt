package com.example.dodoprojectv2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.databinding.ActivityTaskCompletionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.UUID

class TaskCompletionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTaskCompletionBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    
    private var photoUri: String? = null
    private var taskTitle: String? = null
    private var taskPoints: Int = 0
    private var isTaskCompleted: Boolean = true
    private var taskProgress: String? = null
    
    private val TAG = "TaskCompletionActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Firebase'i başlat
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        
        // Intent'ten verileri al
        photoUri = intent.getStringExtra("PHOTO_URI")
        taskTitle = intent.getStringExtra("TASK_TITLE")
        taskPoints = intent.getIntExtra("TASK_POINTS", 100)
        isTaskCompleted = intent.getBooleanExtra("TASK_COMPLETED", true)
        taskProgress = intent.getStringExtra("TASK_PROGRESS")
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        if (isTaskCompleted) {
            // Görev tamamlandı
            binding.textTaskTitle.text = "Görev Tamamlandı!"
            binding.textTaskPoints.text = "+$taskPoints puan kazandınız!"
            binding.buttonBackToTasks.text = "Görevlere Dön"
        } else {
            // Görev devam ediyor
            binding.textTaskTitle.text = "Fotoğraf Kaydedildi!"
            binding.textTaskPoints.text = taskProgress ?: "Görev devam ediyor..."
            binding.buttonBackToTasks.text = "Devam Et"
        }
        
        // Fotoğrafı yükle
        photoUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .into(binding.imagePhoto)
        }
    }
    
    private fun setupClickListeners() {
        // Görevlere dön/Devam et butonu
        binding.buttonBackToTasks.setOnClickListener {
            if (isTaskCompleted) {
                backToTasks()
            } else {
                // Görev devam ediyor, kameraya geri dön
                backToCamera()
            }
        }
        
        // Profilinde paylaş butonu
        binding.buttonShareToProfile.setOnClickListener {
            shareToProfile()
        }
    }
    
    private fun backToTasks() {
        // MainActivity'e dön ve Tasks fragmentını göster
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("SHOW_TASKS_FRAGMENT", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
    
    private fun backToCamera() {
        // Kameraya geri dön (görev devam ediyor)
        val cameraIntent = Intent(this, CameraActivity::class.java).apply {
            putExtra("TASK_ID", intent.getStringExtra("TASK_ID"))
            putExtra("TASK_TITLE", taskTitle)
            putExtra("TASK_TOTAL_COUNT", intent.getIntExtra("TASK_TOTAL_COUNT", 1))
            putExtra("TASK_CURRENT_COUNT", intent.getIntExtra("TASK_CURRENT_COUNT", 0))
        }
        startActivity(cameraIntent)
        finish()
    }
    
    private fun shareToProfile() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (photoUri == null) {
            Toast.makeText(this, "Paylaşılacak fotoğraf bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Loading göster
        binding.buttonShareToProfile.isEnabled = false
        binding.buttonShareToProfile.text = "Paylaşılıyor..."
        
        // Fotoğrafı profilde ve ana sayfada paylaş
        sharePhotoToFeed(userId, photoUri!!)
    }
    
    private fun sharePhotoToFeed(userId: String, photoUri: String) {
        Log.d(TAG, "Fotoğraf ana sayfaya paylaşılıyor...")
        
        // Ana sayfa postu oluştur
        val postData = hashMapOf(
            "id" to UUID.randomUUID().toString(),
            "userId" to userId,
            "photoUrl" to photoUri,
            "caption" to "Görev tamamlandı: $taskTitle",
            "timestamp" to FieldValue.serverTimestamp(),
            "likes" to 0,
            "comments" to 0,
            "isTaskPhoto" to true,
            "taskTitle" to taskTitle,
            "taskPoints" to taskPoints
        )
        
        firestore.collection("posts")
            .add(postData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Post ana sayfaya eklendi: ${documentReference.id}")
                
                // Kullanıcının fotoğraf listesine de ekle
                updateUserPhotosList(userId, photoUri, documentReference.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Post paylaşılırken hata: ${e.message}", e)
                binding.buttonShareToProfile.isEnabled = true
                binding.buttonShareToProfile.text = "Profilinde Paylaş"
                Toast.makeText(this, "Paylaşım başarısız: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateUserPhotosList(userId: String, photoUri: String, postId: String) {
        // Kullanıcının fotoğraf koleksiyonuna ekle
        val photoData = hashMapOf(
            "photoUrl" to photoUri,
            "timestamp" to FieldValue.serverTimestamp(),
            "caption" to "Görev: $taskTitle",
            "postId" to postId,
            "isTaskPhoto" to true,
            "taskTitle" to taskTitle
        )
        
        firestore.collection("users")
            .document(userId)
            .collection("photos")
            .add(photoData)
            .addOnSuccessListener {
                Log.d(TAG, "Fotoğraf kullanıcı galerisine eklendi")
                Toast.makeText(this, "Fotoğraf profilinizde paylaşıldı!", Toast.LENGTH_SHORT).show()
                
                // UI'yı güncelle
                binding.buttonShareToProfile.text = "Paylaşıldı ✓"
                binding.buttonShareToProfile.isEnabled = false
                
                // 2 saniye sonra görevlere dön
                binding.buttonShareToProfile.postDelayed({
                    backToTasks()
                }, 2000)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Fotoğraf kullanıcı galerisine eklenirken hata: ${e.message}", e)
                binding.buttonShareToProfile.isEnabled = true
                binding.buttonShareToProfile.text = "Profilinde Paylaş"
                Toast.makeText(this, "Profil güncelleme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 