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
                Log.e(TAG, "Gemini API görev oluşturamadı veya yeterli görev sayısına ulaşılamadı. Tekrar deneniyor.")
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
            
            // API'den tam olarak 3 görev geldiğini doğrula
            if (tasksList.size < 3) {
                Log.e(TAG, "API'den beklenenden az görev geldi (${tasksList.size}/3). Tekrar deneniyor.")
                return@withContext Result.retry()
            }
            
            try {
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
                Log.e(TAG, "Görevler Firestore'a kaydedilirken hata: ${e.message}", e)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Görev oluşturma hatası: ${e.message}", e)
            Result.retry()
        }
    }
    
    private suspend fun generateTasksWithGemini(): List<TaskResponse> {
        try {
            val prompt = """
                Günlük hayatta kolayca bulunabilecek nesnelerin veya durumların fotoğrafını çekme görevleri oluştur.
                Bu görevler, kullanıcıyı hem ev içinde hem de dışarıda (sokak, park, alışveriş merkezi gibi yerlerde) 
                aktif olmaya teşvik eden, çeşitli ve ilgi çekici görevler olmalı.
                
                ÇOK ÖNEMLİ KURALLAR:
                1. Her görev diğerlerinden açıkça farklı olmalı ve birbirini tekrar etmemeli.
                2. HER GÖREV BENZERSİZ OLMALI ve farklı kategorilerden seçilmeli.
                3. Görevlerin yaklaşık %60'ı kullanıcıyı evden dışarı çıkarmaya teşvik etmeli (dışarıda fotoğraf çekme, doğa, yürüyüş vb.)
                4. Görevler, kullanıcıya neşe, heyecan ve keşif duygusu vermeli.
                5. Görevler basit ancak ilgi çekici olmalı, kullanıcıyı fiziksel olarak aktif olmaya yönlendirmeli.
                
                Her görev Türkçe, kısa ve anlaşılır bir cümle olmalı.
                
                Dış mekan görev örnekleri:
                - "Bir park veya bahçeden manzara fotoğrafla" (doğa kategorisi)
                - "Sokakta yürüyen bir evcil hayvan fotoğrafla" (evcil hayvan kategorisi)
                - "Dışarıda ilginç bir mimari detay fotoğrafla" (mimari kategorisi)
                - "Bulutlu gökyüzü fotoğrafla" (hava kategorisi)
                - "Bir sokak tabelası fotoğrafla" (şehir kategorisi)
                - "Dışarıda park edilmiş renkli bir araç fotoğrafla" (ulaşım kategorisi)
                - "Bir çiçek veya ağaç fotoğrafla" (bitki kategorisi)
                - "3 adet kırmızı araba fotoğrafla" (otomobil kategorisi)
                - "Halka açık bir alanda bulunan bir heykel veya sanat eseri fotoğrafla" (sanat kategorisi)
                - "Bir cafe veya restoran tabelası fotoğrafla" (yeme-içme kategorisi)
                - "Dışarıda gördüğün ilginç bir tabela ya da yazı fotoğrafla" (yazı kategorisi)
                - "Parkta bir spor ekipmanı fotoğrafla" (spor kategorisi)
                
                İç mekan görev örnekleri:
                - "Evdeki favori kitabını fotoğrafla" (eğitim kategorisi)
                - "Mutfakta kullandığın bir aletin fotoğrafını çek" (mutfak kategorisi)
                - "Odanın en renkli köşesini fotoğrafla" (iç mekan kategorisi)
                - "Evdeki en sevdiğin tabloyu fotoğrafla" (dekorasyon kategorisi)
                - "Kullandığın bir teknolojik cihazı fotoğrafla" (teknoloji kategorisi)
                - "Evde yaptığın bir yemeği veya içeceği fotoğrafla" (yemek kategorisi)
                - "Evdeki en eski eşyayı fotoğrafla" (nostalji kategorisi)
                
                Tam olarak 3 farklı kategoriden görev oluştur. Bunlardan en az 1 veya 2 tanesi dış mekan görevi olmalı.
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
     * Varsayılan görevleri döndürür - Artık kullanılmıyor, tüm görevler API'den geliyor
     * Not: Bu metod artık kullanılmıyor, ancak kodun temiz tutulması için referansı korundu.
     */
    private fun getDefaultTasks(): List<TaskResponse> {
        Log.w(TAG, "getDefaultTasks metodu artık kullanılmıyor, tüm görevler API'den geliyor.")
        return emptyList()
    }
} 