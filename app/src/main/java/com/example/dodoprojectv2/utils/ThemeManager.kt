package com.example.dodoprojectv2.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Karanlık mod tema yönetimi için utility sınıfı
 */
object ThemeManager {
    private const val PREF_NAME = "theme_preferences"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Temayı uygular
     */
    fun applyTheme(context: Context, isDarkMode: Boolean) {
        val nightMode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        
        // Local SharedPreferences'a da kaydet (offline için)
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }
        
        // Firebase'e kaydet
        saveThemeToFirebase(isDarkMode)
    }
    
    /**
     * Kaydedilmiş tema tercihini uygular
     */
    fun applySavedTheme(context: Context) {
        val isDarkMode = isDarkModeEnabled(context)
        applyTheme(context, isDarkMode)
    }
    
    /**
     * Kaydedilmiş tema tercihini döndürür
     */
    fun isDarkModeEnabled(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_DARK_MODE, false)
    }
    
    fun loadThemeFromFirebase(context: Context, callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Kullanıcı giriş yapmamışsa local tercih kullan
            val localTheme = isDarkModeEnabled(context)
            callback(localTheme)
            return
        }
        
        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Firebase'den tema tercihini al
                    val themePreference = document.getBoolean("themePreference") ?: false
                    
                    // Local SharedPreferences'ı da güncelle
                    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putBoolean(KEY_DARK_MODE, themePreference)
                        apply()
                    }
                    
                    callback(themePreference)
                } else {
                    // Kullanıcı dokümanı yoksa default light tema kullan
                    callback(false)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ThemeManager", "Firebase'den tema yüklenemedi: ${exception.message}")
                // Hata durumunda local tercih kullan
                val localTheme = isDarkModeEnabled(context)
                callback(localTheme)
            }
    }
    
    private fun saveThemeToFirebase(isDarkMode: Boolean) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("ThemeManager", "Kullanıcı giriş yapmamış, tema Firebase'e kaydedilemedi")
            return
        }
        
        firestore.collection("users")
            .document(currentUser.uid)
            .update("themePreference", isDarkMode)
            .addOnSuccessListener {
                Log.d("ThemeManager", "Tema tercihi Firebase'e kaydedildi: $isDarkMode")
            }
            .addOnFailureListener { exception ->
                Log.e("ThemeManager", "Tema Firebase'e kaydedilemedi: ${exception.message}")
                
                // Eğer kullanıcı dokümanı yoksa oluştur
                val userData = mapOf("themePreference" to isDarkMode)
                firestore.collection("users")
                    .document(currentUser.uid)
                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("ThemeManager", "Kullanıcı dokümanı oluşturuldu ve tema kaydedildi")
                    }
                    .addOnFailureListener { createException ->
                        Log.e("ThemeManager", "Kullanıcı dokümanı oluşturulamadı: ${createException.message}")
                    }
            }
    }
    
    // Yeni kullanıcılar için varsayılan tema tercihini ayarla
    fun setDefaultThemeForNewUser(userId: String) {
        firestore.collection("users")
            .document(userId)
            .update("themePreference", false) // Varsayılan olarak light theme
            .addOnFailureListener {
                // Eğer kullanıcı dokümanı yoksa oluştur
                val userData = mapOf("themePreference" to false)
                firestore.collection("users")
                    .document(userId)
                    .set(userData, com.google.firebase.firestore.SetOptions.merge())
            }
    }
} 