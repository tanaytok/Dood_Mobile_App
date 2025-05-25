package com.example.dodoprojectv2.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dodoprojectv2.api.Content
import com.example.dodoprojectv2.api.GeminiClient
import com.example.dodoprojectv2.api.GeminiRequest
import com.example.dodoprojectv2.api.Part
import com.example.dodoprojectv2.api.TaskResponse
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Günlük görevleri oluşturmak için WorkManager Worker sınıfı
 */
class TaskGeneratorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "TaskGeneratorWorker"
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Bugünün tarihini önce al ki hem try hem catch bloğunda kullanılabilsin
        val today = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = dateFormat.format(today)
        
        try {
            Log.d(TAG, "Görev oluşturma işlemi başlatıldı - Tarih: $dateString")
            
            // 1. Bugün için görevler zaten var mı kontrol et
            val dailyTasksSnapshot = firestore.collection("daily_tasks")
                .document(dateString)
                .get(Source.DEFAULT)
                .await()
                
            if (dailyTasksSnapshot.exists()) {
                Log.d(TAG, "Bugün için görevler zaten oluşturulmuş: $dateString")
                return@withContext Result.success()
            }
            
            // 2. Son API çağrısının üzerinden yeterli zaman geçti mi kontrol et
            if (!canMakeApiCall()) {
                Log.w(TAG, "API rate limit koruması aktif, fallback görevleri kullanılacak")
                val fallbackTasks = createFallbackTasks()
                return@withContext saveDailyTasks(dateString, fallbackTasks)
            }
            
            // 3. Gemini API'yi kullanarak görevleri oluştur
            Log.d(TAG, "Gemini API çağrısı yapılıyor...")
            val tasks = generateTasksWithGemini()
            
            if (tasks.isEmpty()) {
                Log.e(TAG, "Gemini API görev oluşturamadı. Fallback görevleri kullanılacak.")
                // API başarısız olduğunda fallback görevleri oluştur
                val fallbackTasks = createFallbackTasks()
                return@withContext saveDailyTasks(dateString, fallbackTasks)
            }
            
            // 4. Başarılı API çağrısını kaydet
            recordSuccessfulApiCall()
            
            // API'den gelen görevleri kaydet
            return@withContext saveDailyTasks(dateString, tasks)
        } catch (e: Exception) {
            Log.e(TAG, "Görev oluşturma hatası: ${e.message}", e)
            // Hata durumunda fallback görevleri oluştur
            val fallbackTasks = createFallbackTasks()
            return@withContext saveDailyTasks(dateString, fallbackTasks)
        }
    }
    
    private suspend fun saveDailyTasks(dateString: String, tasks: List<TaskResponse>): Result {
        return try {
            val dailyTasksRef = firestore.collection("daily_tasks").document(dateString)
            
            val tasksList = tasks.mapIndexed { index, task ->
                mapOf(
                    "id" to "task_${Date().time}_$index",
                    "title" to task.title,
                    "totalCount" to task.totalCount,
                    "completedCount" to 0,
                    "isCompleted" to false,
                    "timestamp" to Date().time,
                    "expiresAt" to Date().time + 24 * 60 * 60 * 1000, // 24 saat sonra
                    "points" to 100
                )
            }
            
            dailyTasksRef.set(
                mapOf(
                    "date" to com.google.firebase.Timestamp(Date()),
                    "tasks" to tasksList,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            
            Log.d(TAG, "Günlük görevler başarıyla oluşturuldu: $dateString (${tasks.size} görev)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Görevler Firestore'a kaydedilirken hata: ${e.message}", e)
            Result.failure()
        }
    }
    
    private fun createFallbackTasks(): List<TaskResponse> {
        Log.d(TAG, "Fallback görevleri oluşturuluyor...")
        
        val fallbackTasks = listOf(
            // Tek fotoğraf görevleri
            TaskResponse("Gün batımını fotoğrafla", 1),
            TaskResponse("Kırmızı bir araba fotoğrafla", 1),
            TaskResponse("Teknolojik cihaz fotoğrafla", 1),
            TaskResponse("Mutfak aleti fotoğrafla", 1),
            TaskResponse("Kitap veya dergi fotoğrafla", 1),
            TaskResponse("Çiçek fotoğrafla", 1),
            TaskResponse("Sokak tabelası fotoğrafla", 1),
            TaskResponse("Kapı fotoğrafla", 1),
            TaskResponse("Pencere fotoğrafla", 1),
            TaskResponse("Bardak veya fincan fotoğrafla", 1),
            TaskResponse("Ayakkabı fotoğrafla", 1),
            TaskResponse("Ağaç fotoğrafla", 1),
            TaskResponse("Bulut fotoğrafla", 1),
            TaskResponse("Bisiklet fotoğrafla", 1),
            TaskResponse("Çanta fotoğrafla", 1),
            TaskResponse("Saat fotoğrafla", 1),
            TaskResponse("Ayna fotoğrafla", 1),
            TaskResponse("Lambayu fotoğrafla", 1),
            TaskResponse("Masa fotoğrafla", 1),
            TaskResponse("Sandalye fotoğrafla", 1),
            
            // İki fotoğraf görevleri
            TaskResponse("2 adet yeşil yaprak fotoğrafla", 2),
            TaskResponse("2 farklı hayvan fotoğrafla", 2),
            TaskResponse("2 adet farklı çiçek fotoğrafla", 2),
            TaskResponse("2 farklı renkte araba fotoğrafla", 2),
            TaskResponse("2 adet elektronik cihaz fotoğrafla", 2),
            TaskResponse("2 farklı kitap fotoğrafla", 2),
            TaskResponse("2 adet yuvarlak obje fotoğrafla", 2),
            TaskResponse("2 farklı bina fotoğrafla", 2),
            TaskResponse("2 adet beyaz obje fotoğrafla", 2),
            TaskResponse("2 farklı sokak işareti fotoğrafla", 2),
            
            // Üç fotoğraf görevleri
            TaskResponse("3 adet mavi obje fotoğrafla", 3),
            TaskResponse("3 farklı meyve fotoğrafla", 3),
            TaskResponse("3 adet metal obje fotoğrafla", 3),
            TaskResponse("3 farklı ağaç türü fotoğrafla", 3),
            TaskResponse("3 adet cam obje fotoğrafla", 3),
            TaskResponse("3 farklı şekilde kapı fotoğrafla", 3),
            TaskResponse("3 adet sarı obje fotoğrafla", 3),
            TaskResponse("3 farklı ulaşım aracı fotoğrafla", 3),
            TaskResponse("3 adet küçük obje fotoğrafla", 3),
            TaskResponse("3 farklı yazı fotoğrafla", 3)
        )
        
        // Rastgele 3 görev seç ve çeşitlilik sağla
        val selectedTasks = mutableListOf<TaskResponse>()
        val shuffledTasks = fallbackTasks.shuffled()
        
        // Farklı totalCount'lara sahip görevler seçmeye çalış
        val onePhotoTasks = shuffledTasks.filter { it.totalCount == 1 }
        val twoPhotoTasks = shuffledTasks.filter { it.totalCount == 2 }
        val threePhotoTasks = shuffledTasks.filter { it.totalCount == 3 }
        
        // Birer tane seç her kategoriden
        if (onePhotoTasks.isNotEmpty()) selectedTasks.add(onePhotoTasks.random())
        if (twoPhotoTasks.isNotEmpty()) selectedTasks.add(twoPhotoTasks.random())
        if (threePhotoTasks.isNotEmpty()) selectedTasks.add(threePhotoTasks.random())
        
        // Eğer 3'e ulaşmadıysak kalan yerları doldur
        while (selectedTasks.size < 3 && selectedTasks.size < shuffledTasks.size) {
            val remainingTasks = shuffledTasks.filter { task -> 
                selectedTasks.none { it.title == task.title } 
            }
            if (remainingTasks.isNotEmpty()) {
                selectedTasks.add(remainingTasks.random())
            } else {
                break
            }
        }
        
        Log.d(TAG, "Fallback görevleri seçildi: ${selectedTasks.map { "${it.title} (${it.totalCount})" }}")
        
        return selectedTasks
    }
    
    private suspend fun generateTasksWithGemini(): List<TaskResponse> {
        try {
            // Haftada bir kez 7 günlük görev üret
            val prompt = """
                Günlük hayatta kolayca bulunabilecek nesnelerin veya durumların fotoğrafını çekme görevleri oluştur.
                Bu görevler, kullanıcıyı hem ev içinde hem de dışarıda (sokak, park, alışveriş merkezi gibi yerlerde) 
                aktif olmaya teşvik eden, çeşitli ve ilgi çekici görevler olmalı.
                
                7 gün için toplam 21 adet (günde 3'er) farklı görev oluştur. Her görev benzersiz olmalı.
                
                Görev türleri:
                - Dış mekan: "Kırmızı araba fotoğrafla", "Ağaç veya çiçek fotoğrafla", "Sokak tabelası fotoğrafla"
                - İç mekan: "Mutfak aleti fotoğrafla", "Kitap fotoğrafla", "Teknolojik cihaz fotoğrafla"
                - Çoklu: "3 adet mavi obje fotoğrafla", "2 farklı hayvan fotoğrafla"
                
                JSON formatında 21 görev döndür (günde 3 görev x 7 gün):
                [
                  {"title": "Görev açıklaması 1", "totalCount": 1},
                  {"title": "Görev açıklaması 2", "totalCount": 2},
                  {"title": "Görev açıklaması 3", "totalCount": 3},
                  {"title": "Görev açıklaması 4", "totalCount": 1},
                  ...21 görev toplam
                ]
            """.trimIndent()
            
            val request = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt)
                        )
                    )
                )
            )
            
            val response = GeminiClient.service.generateContent(request)
            
            if (response.isSuccessful && response.body() != null) {
                val responseText = response.body()!!.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: return emptyList()
                
                Log.d(TAG, "Gemini API yanıtı alındı (${responseText.length} karakter)")
                
                // JSON yanıtını parse et - batch modunda birden fazla gün için
                val allTasks = parseTasksFromJson(responseText)
                
                if (allTasks.size >= 3) {
                    // Bugün için sadece ilk 3 görevi döndür, kalanını batch olarak kaydet
                    saveBatchTasks(allTasks)
                    return allTasks.take(3)
                } else {
                    Log.w(TAG, "Batch API'den yeterli görev gelmedi: ${allTasks.size}")
                    return allTasks
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Gemini API hatası: $errorBody")
                
                // Quota hatası (429) kontrolü
                if (response.code() == 429 || errorBody?.contains("quota") == true || errorBody?.contains("RESOURCE_EXHAUSTED") == true) {
                    Log.w(TAG, "Gemini API quota limiti aşıldı, fallback görevleri kullanılacak.")
                }
                
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "API isteği sırasında hata: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Batch olarak üretilen görevleri gelecek günler için kaydeder
     */
    private suspend fun saveBatchTasks(allTasks: List<TaskResponse>) {
        try {
            if (allTasks.size < 6) return // En az 6 görev olmalı (bugün + yarın)
            
            Log.d(TAG, "Batch görevleri kaydediliyor: ${allTasks.size} görev")
            
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            var taskIndex = 3 // İlk 3 görev bugün için kullanıldı
            
            // Gelecek 6 gün için görevleri kaydet (günde 3'er)
            for (day in 1..6) {
                if (taskIndex + 2 >= allTasks.size) break
                
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val futureDateString = dateFormat.format(calendar.time)
                
                val dayTasks = allTasks.subList(taskIndex, minOf(taskIndex + 3, allTasks.size))
                
                // Bu gün için görevler zaten var mı kontrol et
                val existingDoc = firestore.collection("daily_tasks")
                    .document(futureDateString)
                    .get()
                    .await()
                    
                if (!existingDoc.exists()) {
                    saveDailyTasks(futureDateString, dayTasks)
                    Log.d(TAG, "Batch: $futureDateString için ${dayTasks.size} görev kaydedildi")
                } else {
                    Log.d(TAG, "Batch: $futureDateString için görevler zaten mevcut")
                }
                
                taskIndex += 3
            }
            
            Log.d(TAG, "Batch görev kaydetme tamamlandı")
        } catch (e: Exception) {
            Log.e(TAG, "Batch görev kaydetme hatası: ${e.message}", e)
        }
    }
    
    private fun parseTasksFromJson(jsonString: String): List<TaskResponse> {
        return try {
            // Önce doğrudan JSON'ı parse etmeyi dene
            try {
                val jsonArray = JSONArray(jsonString.trim())
                val taskList = mutableListOf<TaskResponse>()
                val uniqueTitles = mutableSetOf<String>() // Benzersiz başlıkları takip etmek için set
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val title = jsonObject.getString("title").trim()
                    val totalCount = jsonObject.optInt("totalCount", 1)
                    
                    // Aynı başlığa sahip bir görev zaten varsa, atlayıp geç
                    if (title.isEmpty() || !uniqueTitles.add(title)) {
                        continue
                    }
                    
                    taskList.add(TaskResponse(title, totalCount))
                }
                
                // En az bir görev başarıyla parse edildiyse, bunları döndür
                if (taskList.size >= 3) {
                    return taskList.take(3)
                } else if (taskList.isNotEmpty()) {
                    Log.w(TAG, "Gemini API yeterli görev oluşturamadı (${taskList.size}/3). Tekrar deneniyor.")
                    return emptyList() // Yetersiz görev sayısında boş liste döndür (retry tetiklenecek)
                }
            } catch (e: JSONException) {
                Log.d(TAG, "Doğrudan JSON parse edilemedi, regex ile deneniyor: ${e.message}")
                // İlk deneme başarısız olduysa, regex ile dene
            }
            
            // Regex ile JSON'ı ayıkla
            val jsonMatch = Regex("\\[\\s*\\{.*?\\}.*?\\]").find(jsonString)?.value
                ?: throw JSONException("Geçerli JSON bulunamadı")
            
            val jsonArray = JSONArray(jsonMatch)
            val taskList = mutableListOf<TaskResponse>()
            val uniqueTitles = mutableSetOf<String>() // Benzersiz başlıkları takip etmek için set
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val title = jsonObject.getString("title").trim()
                val totalCount = jsonObject.optInt("totalCount", 1)
                
                // Aynı başlığa sahip bir görev zaten varsa, atlayıp geç
                if (title.isEmpty() || !uniqueTitles.add(title)) {
                    continue
                }
                
                taskList.add(TaskResponse(title, totalCount))
            }
            
            // Yeterince görev yoksa boş liste döndür (retry tetiklenecek)
            if (taskList.size < 3) {
                Log.w(TAG, "Gemini API yeterli görev oluşturamadı (${taskList.size}/3). Tekrar deneniyor.")
                return emptyList()
            }
            
            // Benzersiz 3 görevi döndür
            taskList.take(3)
        } catch (e: JSONException) {
            Log.e(TAG, "JSON ayrıştırma hatası: ${e.message}", e)
            // JSON ayrıştırma hatasında da boş liste döndür (retry tetiklenecek)
            emptyList()
        }
    }
    
    /**
     * API çağrısı yapılıp yapılamayacağını kontrol eder
     * Rate limiting için son çağrının üzerinden en az 6 saat geçmiş olması gerekir
     */
    private suspend fun canMakeApiCall(): Boolean {
        return try {
            val lastCallDoc = firestore.collection("api_usage")
                .document("gemini_last_call")
                .get()
                .await()
                
            if (!lastCallDoc.exists()) {
                Log.d(TAG, "İlk API çağrısı, izin veriliyor")
                return true
            }
            
            val lastCallTime = lastCallDoc.getLong("timestamp") ?: 0
            val currentTime = System.currentTimeMillis()
            val timeDifference = currentTime - lastCallTime
            
            // 6 saat = 21600000 ms (quota koruması için uzun süre)
            val sixHours = 21600000L
            val canCall = timeDifference >= sixHours
            
            Log.d(TAG, "Son API çağrısından ${timeDifference / 3600000} saat geçti. API çağrısı: ${if (canCall) "İzinli" else "Engelli"}")
            
            return canCall
        } catch (e: Exception) {
            Log.e(TAG, "API çağrı kontrolü sırasında hata: ${e.message}")
            // Hata durumunda güvenli tarafta kalıp false döndür
            return false
        }
    }
    
    /**
     * Başarılı API çağrısını kaydeder
     */
    private suspend fun recordSuccessfulApiCall() {
        try {
            firestore.collection("api_usage")
                .document("gemini_last_call")
                .set(mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "success" to true
                ))
                .await()
            Log.d(TAG, "Başarılı API çağrısı kaydedildi")
        } catch (e: Exception) {
            Log.e(TAG, "API çağrı kaydı sırasında hata: ${e.message}")
        }
    }
    
    /**
     * Varsayılan görevleri döndürür - Artık kullanılmıyor, tüm görevler API'den geliyor
     * Not: Bu metod artık kullanılmıyor, ancak kodun temiz tutulması için referansı korundu.
     */
    private fun getDefaultTasks(): List<TaskResponse> {
        Log.w(TAG, "getDefaultTasks metodu artık kullanılmıyor, tüm görevler API'den geliyor.")
        return emptyList()
    }
} 