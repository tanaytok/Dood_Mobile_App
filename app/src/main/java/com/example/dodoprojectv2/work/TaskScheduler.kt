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
 * Görev zamanlayıcısı
 */
object TaskScheduler {
    private const val TASK_GENERATOR_WORK_NAME = "daily_task_generator"
    
    /**
     * Günlük görev oluşturma işini zamanlar
     */
    fun scheduleDailyTaskGeneration(context: Context) {
        // Network bağlantısı olduğunda çalışacak şekilde ayarla
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Her 24 saatte bir çalışacak iş planı oluştur
        val taskGeneratorWorkRequest = PeriodicWorkRequestBuilder<TaskGeneratorWorker>(
            24, TimeUnit.HOURS
        )
        .setConstraints(constraints)
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
     */
    fun generateTasksNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val taskGeneratorWorkRequest = OneTimeWorkRequestBuilder<TaskGeneratorWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueue(taskGeneratorWorkRequest)
    }
} 