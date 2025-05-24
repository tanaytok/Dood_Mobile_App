package com.example.dodoprojectv2.ui.tasks

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.dodoprojectv2.MyApplication
import com.example.dodoprojectv2.work.TaskGeneratorWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

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
    
    private val _timeUntilReset = MutableLiveData<String>()
    val timeUntilReset: LiveData<String> = _timeUntilReset

    init {
        loadTasks()
        startTimeUntilResetCounter()
    }

    private fun isNetworkAvailable(): Boolean {
        val context = MyApplication.appContext
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    private fun getOptimalSource(): Source {
        return if (isNetworkAvailable()) {
            Log.d(TAG, "Network mevcut, SERVER kullanılıyor")
            Source.SERVER
        } else {
            Log.d(TAG, "Network yok, CACHE kullanılıyor")
            Source.CACHE
        }
    }

    fun loadTasks() {
        _isLoading.value = true
        _errorMessage.value = ""
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            _isLoading.value = false
            return
        }
        
        // Network durumunu kontrol et
        val isNetworkAvailable = isNetworkAvailable()
        Log.d(TAG, "Network durumu: $isNetworkAvailable")
        
        if (!isNetworkAvailable) {
            Log.w(TAG, "Network yok, direkt cache ve fallback görevlere geçiliyor")
            // Network yoksa direkt fallback görevleri göster
            _errorMessage.value = "İnternet bağlantısı yok. Örnek görevler gösteriliyor."
            createSampleTasksIfNeeded()
            return
        }
        
        // Bugünün tarihini al
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = dateFormat.format(today.time)
        
        // Önce kullanıcının bugünkü görevlerini kontrol et
        firestore.collection("tasks")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("dateString", dateString)
            .get(getOptimalSource())
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Kullanıcının bugünkü görevleri varsa, onları göster
                    var tasksList = documents.mapNotNull { doc ->
                        try {
                            Task(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                totalCount = doc.getLong("totalCount")?.toInt() ?: 1,
                                completedCount = doc.getLong("completedCount")?.toInt() ?: 0,
                                isCompleted = doc.getBoolean("isCompleted") ?: false,
                                timestamp = doc.getLong("timestamp") ?: 0,
                                expiresAt = doc.getLong("expiresAt") ?: 0,
                                points = doc.getLong("points")?.toInt() ?: 100,
                                userId = doc.getString("userId") ?: "",
                                dateString = doc.getString("dateString") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Görev verisi dönüştürülürken hata: ${e.message}", e)
                            null
                        }
                    }
                    
                    // Aynı başlıkta birden fazla görev varsa filtrele
                    tasksList = tasksList.distinctBy { it.title }
                    
                    // 3'ten fazla görev varsa, sadece en son eklenen 3 görevi göster
                    if (tasksList.size > 3) {
                        Log.w(TAG, "Kullanıcının gereğinden fazla görevi var: ${tasksList.size}, sadece 3 görev gösterilecek")
                        // Temizleme işlemi yap - sadece en son 3 görevi tut
                        val tasksToKeep = tasksList.sortedByDescending { it.timestamp }.take(3)
                        val tasksToDelete = tasksList.filter { task -> tasksToKeep.none { it.id == task.id } }
                        
                        // Fazla görevleri sil
                        val batch = firestore.batch()
                        tasksToDelete.forEach { task ->
                            batch.delete(firestore.collection("tasks").document(task.id))
                        }
                        
                        batch.commit()
                            .addOnSuccessListener {
                                Log.d(TAG, "${tasksToDelete.size} fazla görev silindi")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Fazla görevler silinirken hata: ${e.message}", e)
                            }
                        
                        // Sadece tutulacak görevleri göster
                        tasksList = tasksToKeep
                    }
                    
                    _tasks.value = tasksList
                    _isEmpty.value = tasksList.isEmpty()
                    _isLoading.value = false
                    Log.d(TAG, "Kullanıcının bugünkü görevleri yüklendi: ${tasksList.size} görev")
                } else {
                    // Kullanıcının görevleri yoksa, bugünkü görevleri "daily_tasks" koleksiyonundan kontrol et ve ata
                    Log.d(TAG, "Kullanıcının bugün için görevi bulunamadı, görevi atama işlemi yapılacak")
                    assignTasksToUser(currentUser.uid, dateString)
                }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Görevler yüklenirken hata oluştu: ${e.message}"
                Log.e(TAG, "Görevler yüklenirken hata: ${e.message}", e)
                
                // Hata tipine göre farklı stratejiler uygula
                when {
                    e.message?.contains("offline") == true -> {
                        Log.d(TAG, "Offline hatası tespit edildi, cache'den yükleniyor...")
                        tryLoadFromCacheDirectly(currentUser.uid, dateString)
                    }
                    e.message?.contains("network") == true -> {
                        Log.d(TAG, "Network hatası tespit edildi, retry yapılıyor...")
                        retryLoadTasks()
                    }
                    else -> {
                        Log.d(TAG, "Diğer hata, cache deneniyor...")
                        tryLoadFromCacheDirectly(currentUser.uid, dateString)
                    }
                }
            }
    }

    private fun assignTasksToUser(userId: String, dateString: String) {
        _isLoading.value = true
        
        // Önce cache'den kontrol et, offline ise cache kullan
        val source = getOptimalSource()
        
        firestore.collection("tasks")
            .whereEqualTo("userId", userId)
            .whereEqualTo("dateString", dateString)
            .get(source)
            .addOnSuccessListener { existingTasks ->
                // Eğer mevcut görevler varsa önce bunları silelim
                if (!existingTasks.isEmpty) {
                    Log.d(TAG, "Kullanıcının ${existingTasks.size()} mevcut görevi var, temizleniyor")
                    val batch = firestore.batch()
                    existingTasks.forEach { doc ->
                        batch.delete(firestore.collection("tasks").document(doc.id))
                    }
                    
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "Mevcut görevler temizlendi, yeni görevler atanıyor")
                            // Şimdi yeni görevleri oluşturalım
                            fetchDailyTasksAndAssign(userId, dateString)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Mevcut görevler temizlenirken hata: ${e.message}", e)
                            // Offline ise cache'den görevleri yükle
                            tryLoadFromCache(userId, dateString)
                        }
                } else {
                    // Mevcut görev yoksa direkt yeni görevleri ata
                    fetchDailyTasksAndAssign(userId, dateString)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Mevcut görevler kontrol edilirken hata: ${e.message}", e)
                // Offline ise cache'den görevleri yükle
                tryLoadFromCache(userId, dateString)
            }
    }
    
    private fun fetchDailyTasksAndAssign(userId: String, dateString: String) {
        // Önce cache'den kontrol et, offline ise cache kullan
        val source = getOptimalSource()
        
        // Önce bugünün görevlerini daily_tasks koleksiyonundan kontrol et
        firestore.collection("daily_tasks")
            .document(dateString)
            .get(source)
            .addOnSuccessListener { document ->
                if (document.exists() && document.get("tasks") != null) {
                    try {
                        Log.d(TAG, "Bugün için daily_tasks koleksiyonunda görevler bulundu")
                        // Günlük görevler varsa, bunları kullanıcıya atayacağız
                        val tasksData = document.get("tasks") as List<Map<String, Any>>
                        Log.d(TAG, "Toplam günlük görev sayısı: ${tasksData.size}")
                        
                        // Görevleri karıştır ve en fazla 3 tane seç
                        val shuffledTasks = tasksData.shuffled()
                        val selectedTasks = if (shuffledTasks.size >= 3) {
                            shuffledTasks.take(3)
                        } else {
                            Log.w(TAG, "Yeterli günlük görev yok, mevcut tüm görevler kullanılıyor: ${shuffledTasks.size}")
                            shuffledTasks
                        }
                        
                        Log.d(TAG, "Seçilen görevler: ${selectedTasks.map { it["title"] }}")
                        
                        // Benzersiz görevler olduğundan emin ol
                        val uniqueTasks = selectedTasks.distinctBy { it["title"] as String }
                        if (uniqueTasks.size < selectedTasks.size) {
                            Log.w(TAG, "Bazı görevler tekrarlıyor! Benzersiz görev sayısı: ${uniqueTasks.size}")
                        }
                        
                        // Görevleri Firestore'a kaydet
                        val batch = firestore.batch()
                        val userTasks = mutableListOf<Task>()
                        
                        for (taskData in uniqueTasks) {
                            val taskId = taskData["id"] as? String ?: UUID.randomUUID().toString()
                            val taskRef = firestore.collection("tasks").document()
                            
                            val task = Task(
                                id = taskRef.id,
                                title = taskData["title"] as? String ?: "",
                                totalCount = (taskData["totalCount"] as? Number)?.toInt() ?: 1,
                                completedCount = 0,
                                isCompleted = false,
                                timestamp = System.currentTimeMillis(),
                                expiresAt = System.currentTimeMillis() + 24*60*60*1000, // 24 saat geçerli
                                points = 100,
                                userId = userId,
                                dateString = dateString
                            )
                            
                            userTasks.add(task)
                            
                            val taskMap = hashMapOf(
                                "id" to task.id,
                                "title" to task.title,
                                "totalCount" to task.totalCount,
                                "completedCount" to 0,
                                "isCompleted" to false,
                                "timestamp" to task.timestamp,
                                "expiresAt" to task.expiresAt,
                                "points" to task.points,
                                "userId" to userId,
                                "dateString" to dateString,
                                "assignedAt" to FieldValue.serverTimestamp()
                            )
                            
                            batch.set(taskRef, taskMap)
                        }
                        
                        batch.commit()
                            .addOnSuccessListener {
                                _tasks.value = userTasks
                                _isEmpty.value = userTasks.isEmpty()
                                _isLoading.value = false
                                Log.d(TAG, "Kullanıcıya görevler başarıyla atandı: ${userTasks.size} görev")
                            }
                            .addOnFailureListener { e ->
                                _isLoading.value = false
                                _errorMessage.value = "Görevler atanırken hata oluştu: ${e.message}"
                                _isEmpty.value = true
                                Log.e(TAG, "Görevler atanırken hata: ${e.message}", e)
                                
                                // Hata durumunda örnek görevleri göster
                                createSampleTasksIfNeeded()
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Görev verisi işlenirken hata oluştu", e)
                        _isLoading.value = false
                        _errorMessage.value = "Görev verisi işlenirken hata oluştu: ${e.message}"
                        _isEmpty.value = true
                        
                        // Hata durumunda örnek görevleri göster
                        createSampleTasksIfNeeded()
                    }
                } else {
                    Log.d(TAG, "Bugün için görevler oluşturulmamış, AI görev oluşturucuyu başlatıyorum")
                    // Görev oluşturucuyu başlat
                    triggerTaskGeneration(userId, dateString)
                }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Görevler kontrol edilirken hata oluştu: ${e.message}"
                _isEmpty.value = true
                Log.e(TAG, "Görevler kontrol edilirken hata: ${e.message}", e)
                
                // Offline ise cache'den görevleri yükle
                tryLoadFromCache(userId, dateString)
            }
    }

    private fun tryLoadFromCache(userId: String, dateString: String) {
        Log.d(TAG, "Cache'den görevler yüklenmeye çalışılıyor...")
        
        // Cache'den mevcut görevleri yüklemeyi dene
        firestore.collection("tasks")
            .whereEqualTo("userId", userId)
            .whereEqualTo("dateString", dateString)
            .get(getOptimalSource())
            .addOnSuccessListener { cachedTasks ->
                if (!cachedTasks.isEmpty) {
                    Log.d(TAG, "Cache'den ${cachedTasks.size()} görev bulundu")
                    val tasksList = cachedTasks.mapNotNull { doc ->
                        try {
                            Task(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                totalCount = doc.getLong("totalCount")?.toInt() ?: 1,
                                completedCount = doc.getLong("completedCount")?.toInt() ?: 0,
                                isCompleted = doc.getBoolean("isCompleted") ?: false,
                                timestamp = doc.getLong("timestamp") ?: 0,
                                expiresAt = doc.getLong("expiresAt") ?: 0,
                                points = doc.getLong("points")?.toInt() ?: 100,
                                userId = doc.getString("userId") ?: "",
                                dateString = doc.getString("dateString") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Cache'den görev verisi dönüştürülürken hata: ${e.message}", e)
                            null
                        }
                    }
                    
                    _tasks.value = tasksList
                    _isEmpty.value = tasksList.isEmpty()
                    _isLoading.value = false
                    _errorMessage.value = "Offline modunda cache'den yüklendi"
                    Log.d(TAG, "Cache'den ${tasksList.size} görev başarıyla yüklendi")
                } else {
                    Log.d(TAG, "Cache'de görev bulunamadı, sample görevler gösteriliyor")
                    fallbackToSampleTasks()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Cache'den görev yüklenirken hata: ${e.message}", e)
                fallbackToSampleTasks()
            }
    }
    
    private fun fallbackToSampleTasks() {
        Log.d(TAG, "Fallback: Sample görevler gösteriliyor")
        _isLoading.value = false
        _errorMessage.value = "İnternet bağlantısı gerekli. Sample görevler gösteriliyor."
        _isEmpty.value = true
        // Sample görevleri göster
        createSampleTasksIfNeeded()
    }

    private fun triggerTaskGeneration(userId: String, dateString: String) {
        _isLoading.value = true
        
        // WorkManager ile görev oluşturmayı başlat
        val workRequest = OneTimeWorkRequestBuilder<TaskGeneratorWorker>()
            .build()
        
        val workManager = WorkManager.getInstance(MyApplication.appContext)
        workManager.enqueue(workRequest)
        
        // İşlem başladıktan 3 saniye sonra görevleri yeniden yüklemeyi dene
        Handler(Looper.getMainLooper()).postDelayed({
            loadTasks()
        }, 3000)
    }

    private fun createSampleTasksIfNeeded() {
        // Örnek görevleri oluştur (geliştirme aşamasında)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1) // 1 gün geçerli
        val expiresAt = calendar.timeInMillis
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = dateFormat.format(calendar.time)
        
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: ""
        
        val sampleTasks = listOf(
            Task(
                id = UUID.randomUUID().toString(),
                title = "3 adet mavi obje fotoğrafla",
                totalCount = 3,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100,
                userId = userId,
                dateString = dateString
            ),
            Task(
                id = UUID.randomUUID().toString(),
                title = "Gün batımını fotoğrafla",
                totalCount = 1,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100,
                userId = userId,
                dateString = dateString
            ),
            Task(
                id = UUID.randomUUID().toString(),
                title = "Kırmızı bir araba fotoğrafla",
                totalCount = 1,
                completedCount = 0,
                isCompleted = false,
                timestamp = System.currentTimeMillis(),
                expiresAt = expiresAt,
                points = 100,
                userId = userId,
                dateString = dateString
            )
        )
        
        _tasks.value = sampleTasks
        _isEmpty.value = false
        Log.d(TAG, "Örnek görevler oluşturuldu")
    }
    
    private fun startTimeUntilResetCounter() {
        val handler = Handler(Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                updateTimeUntilReset()
                handler.postDelayed(this, 60000) // Her dakika güncelle
            }
        }
        handler.post(updateRunnable)
    }
    
    private fun updateTimeUntilReset() {
        val now = Calendar.getInstance()
        val nextReset = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1) // Yarın
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffMillis = nextReset.timeInMillis - now.timeInMillis
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
        
        _timeUntilReset.value = if (minutes > 0) {
            "Yeni görevlere: $hours saat $minutes dakika"
        } else {
            "Yeni görevlere: $hours saat"
        }
    }

    fun selectTask(task: Task) {
        _selectedTask.value = task
    }

    fun updateTaskProgress(taskId: String, newCount: Int, isCompleted: Boolean) {
        Log.d(TAG, "updateTaskProgress çağrıldı: taskId=$taskId, newCount=$newCount, isCompleted=$isCompleted")
        
        if (taskId.isEmpty()) {
            Log.e(TAG, "updateTaskProgress: taskId boş, güncelleme yapılamıyor")
            return
        }
        
        try {
            // Mevcut görevleri güncelle - daha hızlı UI yanıtı için
            val currentTasks = _tasks.value?.toMutableList() ?: mutableListOf()
            val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
            
            if (taskIndex != -1) {
                // Görevi UI'da güncelle
                val updatedTask = currentTasks[taskIndex].copy(
                    completedCount = newCount,
                    isCompleted = isCompleted
                )
                currentTasks[taskIndex] = updatedTask
                _tasks.value = currentTasks
                
                Log.d(TAG, "Görev yerel olarak güncellendi: $updatedTask")
                
                // Eğer görev tamamlandıysa, kullanıcıya puanları ver ve haftalık skoru güncelle
                if (isCompleted && !currentTasks[taskIndex].isCompleted) {
                    val pointsToAdd = updatedTask.points
                    addPointsToUser(pointsToAdd)
                    updateWeeklyScore(pointsToAdd)
                    Log.d(TAG, "Görev tamamlandı, $pointsToAdd puan ekleniyor ve haftalık skor güncelleniyor")
                }
            }
            
            // Firestore'da güncelle
            val taskRef = firestore.collection("tasks").document(taskId)
            val updates = hashMapOf<String, Any>(
                "completedCount" to newCount,
                "isCompleted" to isCompleted
            )
            
            // Tamamlanmışsa ek bilgiler ekle
            if (isCompleted) {
                updates["status"] = "completed"
                updates["completedAt"] = System.currentTimeMillis()
            }
            
            taskRef.update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Görev Firestore'da güncellendi: taskId=$taskId, newCount=$newCount, isCompleted=$isCompleted")
                    
                    // Görevleri yenile
                    loadTasks()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Görev güncellenirken hata: ${e.message}", e)
                    _errorMessage.value = "Görev güncellenirken hata oluştu: ${e.message}"
                }
        } catch (e: Exception) {
            Log.e(TAG, "updateTaskProgress sırasında hata: ${e.message}", e)
            _errorMessage.value = "Görev güncellenirken hata oluştu: ${e.message}"
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
                "points" to task.points,
                "dateString" to task.dateString
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
    
    private fun updateWeeklyScore(points: Int) {
        val currentUser = auth.currentUser ?: return
        
        try {
            // Haftalık skor tablosunu güncelle
            val weeklyScoreRef = firestore.collection("weekly_scores").document(currentUser.uid)
            
            // Mevcut haftalık skoru kontrol et ve güncelle
            weeklyScoreRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Mevcut skoru al ve güncelle
                        val currentScore = document.getLong("score")?.toInt() ?: 0
                        val newScore = currentScore + points
                        
                        weeklyScoreRef.update(
                            mapOf(
                                "score" to newScore,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                            .addOnSuccessListener {
                                Log.d(TAG, "Haftalık skor güncellendi: $newScore (+$points)")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Haftalık skor güncellenirken hata: ${e.message}", e)
                            }
                    } else {
                        // Doküman yoksa yeni oluştur
                        val userDisplayName = currentUser.displayName ?: "Anonim Kullanıcı"
                        val weeklyScoreData = hashMapOf(
                            "userId" to currentUser.uid,
                            "username" to userDisplayName,
                            "score" to points,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        
                        weeklyScoreRef.set(weeklyScoreData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Yeni haftalık skor kaydı oluşturuldu: $points")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Haftalık skor kaydı oluşturulurken hata: ${e.message}", e)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Haftalık skor bilgisi alınırken hata: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "updateWeeklyScore sırasında hata: ${e.message}", e)
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

    private fun retryLoadTasks() {
        Log.d(TAG, "3 saniye sonra tekrar deneniyor...")
        Handler(Looper.getMainLooper()).postDelayed({
            if (isNetworkAvailable()) {
                Log.d(TAG, "Network tekrar mevcut, tekrar yükleniyor...")
                loadTasks()
            } else {
                Log.d(TAG, "Network hala yok, cache kullanılıyor...")
                val currentUser = auth.currentUser ?: return@postDelayed
                val today = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateString = dateFormat.format(today.time)
                tryLoadFromCacheDirectly(currentUser.uid, dateString)
            }
        }, 3000)
    }
    
    private fun tryLoadFromCacheDirectly(userId: String, dateString: String) {
        Log.d(TAG, "Cache'den direkt yükleme deneniyor...")
        
        firestore.collection("tasks")
            .whereEqualTo("userId", userId)
            .whereEqualTo("dateString", dateString)
            .get(Source.CACHE)
            .addOnSuccessListener { cachedTasks ->
                if (!cachedTasks.isEmpty) {
                    Log.d(TAG, "Cache'den ${cachedTasks.size()} görev bulundu")
                    val tasksList = cachedTasks.mapNotNull { doc ->
                        try {
                            Task(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                totalCount = doc.getLong("totalCount")?.toInt() ?: 1,
                                completedCount = doc.getLong("completedCount")?.toInt() ?: 0,
                                isCompleted = doc.getBoolean("isCompleted") ?: false,
                                timestamp = doc.getLong("timestamp") ?: 0,
                                expiresAt = doc.getLong("expiresAt") ?: 0,
                                points = doc.getLong("points")?.toInt() ?: 100,
                                userId = doc.getString("userId") ?: "",
                                dateString = doc.getString("dateString") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Cache'den görev verisi dönüştürülürken hata: ${e.message}", e)
                            null
                        }
                    }
                    
                    _tasks.value = tasksList
                    _isEmpty.value = tasksList.isEmpty()
                    _isLoading.value = false
                    _errorMessage.value = "Offline modunda cache'den yüklendi"
                    Log.d(TAG, "Cache'den ${tasksList.size} görev başarıyla yüklendi")
                } else {
                    Log.d(TAG, "Cache'de görev bulunamadı, sample görevler gösteriliyor")
                    fallbackToSampleTasks()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Cache'den görev yüklenirken hata: ${e.message}", e)
                fallbackToSampleTasks()
            }
    }

    fun forceRefresh() {
        Log.d(TAG, "Force refresh tetiklendi")
        _isLoading.value = true
        _errorMessage.value = ""
        
        // Cache'i bypass et ve server'dan direkt yükle
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Kullanıcı oturum açmamış"
            _isLoading.value = false
            return
        }
        
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = dateFormat.format(today.time)
        
        firestore.collection("tasks")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("dateString", dateString)
            .get(Source.SERVER)
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
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
                                points = doc.getLong("points")?.toInt() ?: 100,
                                userId = doc.getString("userId") ?: "",
                                dateString = doc.getString("dateString") ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Force refresh'te görev verisi dönüştürülürken hata: ${e.message}", e)
                            null
                        }
                    }
                    
                    _tasks.value = tasksList.distinctBy { it.title }
                    _isEmpty.value = tasksList.isEmpty()
                    _isLoading.value = false
                    _errorMessage.value = ""
                    Log.d(TAG, "Force refresh başarılı: ${tasksList.size} görev yüklendi")
                } else {
                    Log.d(TAG, "Force refresh'te görev bulunamadı, yeni görevler atanıyor")
                    assignTasksToUser(currentUser.uid, dateString)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Force refresh başarısız: ${e.message}", e)
                _isLoading.value = false
                _errorMessage.value = "Yenileme başarısız: ${e.message}"
                
                // Server erişimi başarısız, cache'e geri dön
                tryLoadFromCacheDirectly(currentUser.uid, dateString)
            }
    }
    
    fun clearCacheAndReload() {
        Log.d(TAG, "Cache temizleme ve yeniden yükleme başlatıldı")
        
        // Firestore client'ı yeniden başlatmak için settings'i yenile
        try {
            // Manuel cache clear - available görevleri temizle
            _tasks.value = emptyList()
            _isEmpty.value = true
            _isLoading.value = true
            _errorMessage.value = "Cache temizleniyor..."
            
            // 2 saniye bekle ve yeniden yükle
            Handler(Looper.getMainLooper()).postDelayed({
                loadTasks()
            }, 2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Cache temizleme sırasında hata: ${e.message}", e)
            loadTasks()
        }
    }

    fun testConnectivity() {
        Log.d(TAG, "=== CONNECTIVITY TEST BAŞLADI ===")
        _isLoading.value = true
        _errorMessage.value = "Bağlantı test ediliyor..."
        
        // Basit bir test query
        firestore.collection("users")
            .limit(1)
            .get(Source.SERVER)
            .addOnSuccessListener { documents ->
                Log.d(TAG, "✅ Firestore bağlantısı BAŞARILI")
                _errorMessage.value = "Firestore bağlantısı başarılı!"
                _isLoading.value = false
                
                // Başarılıysa görevleri yükle
                loadTasks()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore bağlantısı BAŞARISIZ: ${e.message}")
                _errorMessage.value = "Firestore bağlantısı başarısız: ${e.message}"
                _isLoading.value = false
                
                // Bağlantı yoksa sample görevleri göster
                createSampleTasksIfNeeded()
            }
    }
} 