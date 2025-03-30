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
        try {
            Log.d(TAG, "Görev oluşturma işlemi başlatıldı")
            
            // Bugünün tarihini al
            val today = Calendar.getInstance().time
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateString = dateFormat.format(today)
            
            // Bugün için görevler zaten var mı kontrol et
            val dailyTasksSnapshot = firestore.collection("daily_tasks")
                .document(dateString)
                .get()
                .await()
                
            if (dailyTasksSnapshot.exists()) {
                Log.d(TAG, "Bugün için görevler zaten oluşturulmuş: $dateString")
                return@withContext Result.success()
            }
            
            // Gemini API'yi kullanarak görevleri oluştur
            val tasks = generateTasksWithGemini()
            if (tasks.isEmpty()) {
                Log.e(TAG, "Gemini API görev oluşturamadı")
                return@withContext Result.retry()
            }
            
            // Görevleri Firestore'a kaydet
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
            
            Log.d(TAG, "Günlük görevler başarıyla oluşturuldu: $dateString")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Görev oluşturma hatası: ${e.message}", e)
            Result.retry()
        }
    }
    
    private suspend fun generateTasksWithGemini(): List<TaskResponse> {
        try {
            val prompt = """
                Günlük hayatta kolayca bulunabilecek nesnelerin veya durumların fotoğrafını çekme görevleri oluştur.
                Bu görevler insanların ev, okul veya sokakta kolayca tamamlayabileceği basit ve FARKLI görevler olmalı.
                
                ÇOK ÖNEMLİ: Her görev diğerlerinden açıkça farklı olmalı ve birbirini tekrar etmemeli.
                HER GÖREV BENZERSİZ OLMALI ve farklı kategorilerden seçilmeli.
                Örneğin, birden fazla yiyecek görevi veya birden fazla renk görevi olmamalı.
                
                Görevler, kullanıcıların günlük hayatlarında kolayca bulabilecekleri ve fotoğraflayabilecekleri nesneler veya durumlar olmalı.
                Her görev Türkçe, kısa ve anlaşılır bir cümle olmalı.
                
                Farklı kategorilerden görev örnekleri:
                - "2 adet kırmızı nesne fotoğrafla" (renk kategorisi)
                - "Bir ağaç fotoğrafla" (doğa kategorisi)
                - "Evdeki bir elektronik cihazı fotoğrafla" (elektronik kategorisi)
                - "Bir çiçek fotoğrafla" (bitki kategorisi)
                - "Masa üzerindeki 3 farklı nesneyi fotoğrafla" (yer kategorisi)
                - "Bir kitap fotoğrafla" (eğitim kategorisi)
                - "Gökyüzü fotoğrafla" (hava kategorisi)
                - "Bir sokak tabelası fotoğrafla" (şehir kategorisi)
                - "Dışarıda park edilmiş bir araç fotoğrafla" (ulaşım kategorisi)
                - "Bir kap içinde su fotoğrafla" (sıvı kategorisi)
                - "Bir müzik aleti fotoğrafla" (müzik kategorisi)
                - "Bir spor ekipmanı fotoğrafla" (spor kategorisi)
                - "Yuvarlak şekilli bir nesne fotoğrafla" (şekil kategorisi)
                - "Üzerinde yazı olan bir nesne fotoğrafla" (yazı kategorisi)
                - "Mutfakta kullanılan bir eşya fotoğrafla" (mutfak kategorisi)
                - "İki farklı ayakkabı fotoğrafla" (kıyafet kategorisi)
                - "Bir bardak içecek fotoğrafla" (içecek kategorisi)
                - "Dışarıda bir bina fotoğrafla" (mimari kategorisi)
                
                Tam olarak 3 farklı kategori ve farklı konularda görev oluştur. İçlerinden hiçbir ikisi benzer olmamalı.
                Her görev için gereken fotoğraf sayısı (1-3 arasında) belirtin.
                Sadece 3 benzersiz görevi JSON formatında döndür:
                [
                  {"title": "Görev açıklaması", "totalCount": 1},
                  {"title": "Görev açıklaması", "totalCount": 2},
                  {"title": "Görev açıklaması", "totalCount": 3}
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
                
                Log.d(TAG, "Gemini API yanıtı: $responseText")
                
                // JSON yanıtını parse et
                return parseTasksFromJson(responseText)
            } else {
                Log.e(TAG, "Gemini API hatası: ${response.errorBody()?.string()}")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "API isteği sırasında hata: ${e.message}", e)
            return emptyList()
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
                    // Eğer 3'ten az benzersiz görev bulunduysa, varsayılan görevlerle tamamla
                    val result = taskList.toMutableList()
                    val defaultTasks = getDefaultTasks()
                    
                    // Eksik görevleri varsayılan görevlerle doldur
                    for (defaultTask in defaultTasks) {
                        if (result.size >= 3) break
                        if (result.none { it.title == defaultTask.title }) {
                            result.add(defaultTask)
                        }
                    }
                    
                    return result
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
            
            // Başarısız olursa veya yeterli benzersiz görev bulunamazsa varsayılan görevleri kullan
            if (taskList.size < 3) {
                Log.w(TAG, "Yeterli benzersiz görev bulunamadı, varsayılan görevlerle tamamlanıyor")
                val result = taskList.toMutableList()
                val defaultTasks = getDefaultTasks()
                
                // Eksik görevleri varsayılan görevlerle doldur
                for (defaultTask in defaultTasks) {
                    if (result.size >= 3) break
                    if (result.none { it.title == defaultTask.title }) {
                        result.add(defaultTask)
                    }
                }
                
                return result
            }
            
            // Benzersiz 3 görevi döndür
            taskList.take(3)
        } catch (e: JSONException) {
            Log.e(TAG, "JSON ayrıştırma hatası: ${e.message}", e)
            // Varsayılan görevleri döndür
            getDefaultTasks()
        }
    }
    
    /**
     * Varsayılan görevleri döndürür
     */
    private fun getDefaultTasks(): List<TaskResponse> {
        return listOf(
            TaskResponse("Kırmızı bir nesne fotoğrafla", 1),
            TaskResponse("Evdeki bir elektronik aletini fotoğrafla", 1),
            TaskResponse("Bir çift ayakkabı fotoğrafla", 1),
            TaskResponse("Ağaç veya bitki fotoğrafla", 1),
            TaskResponse("Mavi renkli bir eşya fotoğrafla", 1),
            TaskResponse("Masa üzerindeki üç farklı nesneyi fotoğrafla", 3),
            TaskResponse("En sevdiğin yiyeceği fotoğrafla", 1),
            TaskResponse("Kitap veya dergi fotoğrafla", 1),
            TaskResponse("Evcil hayvan veya dışarıda bir hayvan fotoğrafla", 1),
            TaskResponse("İki farklı renkteki kalem veya kırtasiye ürünü fotoğrafla", 2)
        )
    }
} 