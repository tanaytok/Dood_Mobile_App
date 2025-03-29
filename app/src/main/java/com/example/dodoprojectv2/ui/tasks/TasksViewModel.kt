package com.example.dodoprojectv2.ui.tasks

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.UUID

class TasksViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "TasksViewModel"

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _selectedTask = MutableLiveData<Task>()
    val selectedTask: LiveData<Task> = _selectedTask

    init {
        loadTasks()
    }

    fun loadTasks() {
        _isLoading.value = true
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            _isLoading.value = false
            return
        }
        
        // Firestore'dan görevleri yükle
        firestore.collection("tasks")
            .whereEqualTo("userId", currentUser.uid)
            .whereGreaterThan("expiresAt", Calendar.getInstance().timeInMillis) // Süresi geçmemiş görevler
            .get()
            .addOnSuccessListener { documents ->
                _isLoading.value = false
                
                if (documents.isEmpty) {
                    // Firestore'da görev yoksa örnek görevleri göster
                    createSampleTasksIfNeeded()
                    return@addOnSuccessListener
                }
                
                val tasksList = documents.mapNotNull { doc ->
                    try {
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            totalCount = doc.getLong("totalCount")?.toInt() ?: 1,
                            completedCount = doc.getLong("completedCount")?.toInt() ?: 0,
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            timestamp = doc.getLong("timestamp") ?: 0,
                            expiresAt = doc.getLong("expiresAt") ?: 0,
                            points = doc.getLong("points")?.toInt() ?: 100
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Görev verisi dönüştürülürken hata: ${e.message}", e)
                        null
                    }
                }
                
                _tasks.value = tasksList
                _isEmpty.value = tasksList.isEmpty()
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Görevler yüklenirken hata oluştu: ${e.message}"
                Log.e(TAG, "Görevler yüklenirken hata: ${e.message}", e)
                
                // Hata durumunda örnek görevleri göster
                createSampleTasksIfNeeded()
            }
    }

    private fun createSampleTasksIfNeeded() {
        // Örnek görevleri oluştur (geliştirme aşamasında)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1) // 1 gün geçerli
        val expiresAt = calendar.timeInMillis
        
        val sampleTasks = listOf(
            Task(
                id = UUID.randomUUID().toString(),
                title = "3 adet mavi obje fotoğrafla",
                totalCount = 3,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100
            ),
            Task(
                id = UUID.randomUUID().toString(),
                title = "Gün batımını fotoğrafla",
                totalCount = 1,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100
            ),
            Task(
                id = UUID.randomUUID().toString(),
                title = "Kırmızı bir araba fotoğrafla",
                totalCount = 1,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100
            )
        )
        
        _tasks.value = sampleTasks
        _isEmpty.value = false
    }

    fun selectTask(task: Task) {
        _selectedTask.value = task
    }

    fun updateTaskProgress(taskId: String, newCount: Int) {
        val currentTasks = _tasks.value ?: return
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
        
        if (taskIndex != -1) {
            val updatedTasks = currentTasks.toMutableList()
            val currentTask = updatedTasks[taskIndex]
            
            val updatedTask = currentTask.copy(
                completedCount = newCount,
                isCompleted = newCount >= currentTask.totalCount
            )
            
            updatedTasks[taskIndex] = updatedTask
            _tasks.value = updatedTasks
            
            // Firestore'da güncelleyelim (eğer gerçek bir görev ise)
            updateTaskInFirestore(updatedTask)
            
            // Eğer görev tamamlandıysa kullanıcıya puanları veriyoruz
            if (updatedTask.isCompleted && !currentTask.isCompleted) {
                addPointsToUser(updatedTask.points)
                incrementUserStreak()
            }
        }
    }
    
    private fun updateTaskInFirestore(task: Task) {
        val currentUser = auth.currentUser ?: return
        
        firestore.collection("tasks").document(task.id)
            .set(mapOf(
                "userId" to currentUser.uid,
                "title" to task.title,
                "totalCount" to task.totalCount,
                "completedCount" to task.completedCount,
                "isCompleted" to task.isCompleted,
                "timestamp" to task.timestamp,
                "expiresAt" to task.expiresAt,
                "points" to task.points
            ))
            .addOnSuccessListener {
                Log.d(TAG, "Görev başarıyla güncellendi: ${task.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Görev güncellenirken hata: ${e.message}", e)
            }
    }
    
    private fun addPointsToUser(points: Int) {
        val currentUser = auth.currentUser ?: return
        
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val currentPoints = documentSnapshot.getLong("points")?.toInt() ?: 0
                val newPoints = currentPoints + points
                
                firestore.collection("users").document(currentUser.uid)
                    .update("points", newPoints)
                    .addOnSuccessListener {
                        Log.d(TAG, "Kullanıcıya $points puan eklendi. Yeni toplam: $newPoints")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Puan eklenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı bilgileri alınırken hata: ${e.message}", e)
            }
    }
    
    private fun incrementUserStreak() {
        val currentUser = auth.currentUser ?: return
        
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val currentStreak = documentSnapshot.getLong("streak")?.toInt() ?: 0
                val newStreak = currentStreak + 1
                
                firestore.collection("users").document(currentUser.uid)
                    .update("streak", newStreak)
                    .addOnSuccessListener {
                        Log.d(TAG, "Kullanıcının serisi 1 arttırıldı. Yeni seri: $newStreak")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Seri güncellenirken hata: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı bilgileri alınırken hata: ${e.message}", e)
            }
    }
} 