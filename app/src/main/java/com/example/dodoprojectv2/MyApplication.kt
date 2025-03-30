package com.example.dodoprojectv2

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.dodoprojectv2.work.TaskScheduler
import com.google.firebase.FirebaseApp

/**
 * Uygulama sınıfı
 */
class MyApplication : Application() {
    
    companion object {
        private const val TAG = "MyApplication"
        
        lateinit var appContext: Context
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Uygulama context'ini kaydet
        appContext = applicationContext
        
        // Firebase'i başlat
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "Firebase başlatıldı")
        
        // Görev zamanlayıcısını başlat
        TaskScheduler.scheduleDailyTaskGeneration(this)
        Log.d(TAG, "Görev zamanlayıcısı başlatıldı")
        
        // İlk çalıştırmada görevleri oluştur
        TaskScheduler.generateTasksNow(this)
        Log.d(TAG, "İlk görev oluşturma işlemi tetiklendi")
    }
} 