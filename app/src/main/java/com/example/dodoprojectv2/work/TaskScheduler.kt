package com.example.dodoprojectv2.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Görev zamanlayıcısı - Quota koruması ile optimize edilmiş
 */
object TaskScheduler {
    private const val TASK_GENERATOR_WORK_NAME = "daily_task_generator"
    private const val IMMEDIATE_TASK_WORK_NAME = "immediate_task_generator"
    
    /**
     * Günlük görev oluşturma işini zamanlar
     * Quota koruma için günde sadece bir kez çalışır
     */
    fun scheduleDailyTaskGeneration(context: Context) {
        // Network bağlantısı olduğunda çalışacak şekilde ayarla
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true) // Batarya düşükken çalışmasın
            .build()
        
        // Her 24 saatte bir çalışacak iş planı oluştur
        // Sistem otomatik olarak uygun zamanlarda çalıştıracak
        val taskGeneratorWorkRequest = PeriodicWorkRequestBuilder<TaskGeneratorWorker>(
            24, TimeUnit.HOURS, // 24 saatte bir
            6, TimeUnit.HOURS   // +/- 6 saat esnek zaman
        )
        .setConstraints(constraints)
        .setInitialDelay(1, TimeUnit.HOURS) // İlk çalışma için 1 saat bekle
        .build()
        
        // İşi planla (uygulama her başlatıldığında kontrol edilecek)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TASK_GENERATOR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Zaten varsa, mevcut planı koru
            taskGeneratorWorkRequest
        )
    }
    
    /**
     * Görevleri hemen oluştur (test için veya ilk başlatmada)
     * Bu fonksiyon quota koruması olan TaskGeneratorWorker'ı çağırır
     */
    fun generateTasksNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val taskGeneratorWorkRequest = OneTimeWorkRequestBuilder<TaskGeneratorWorker>()
            .setConstraints(constraints)
            .addTag(IMMEDIATE_TASK_WORK_NAME)
            .build()
            
        WorkManager.getInstance(context).enqueue(taskGeneratorWorkRequest)
    }
    
    /**
     * Tüm task generation işlerini iptal eder
     */
    fun cancelAllTaskGeneration(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(TASK_GENERATOR_WORK_NAME)
        workManager.cancelAllWorkByTag(IMMEDIATE_TASK_WORK_NAME)
    }
    
    /**
     * WorkManager durumunu kontrol et
     */
    fun getWorkStatus(context: Context) = 
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(TASK_GENERATOR_WORK_NAME)
} 