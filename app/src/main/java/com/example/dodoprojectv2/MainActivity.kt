package com.example.dodoprojectv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.dodoprojectv2.databinding.ActivityMainBinding
import com.example.dodoprojectv2.databinding.NotificationBadgeBinding
import com.example.dodoprojectv2.ui.camera.CameraViewModel
import com.example.dodoprojectv2.utils.ThemeManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var cameraViewModel: CameraViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        
        // Initialize ViewModel
        cameraViewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        
        // Kullanıcı giriş yapmışsa Firebase'den tema tercihini yükle
        loadUserThemePreference()
        
        // Setup bottom navigation with fragments
        val navView: BottomNavigationView = binding.navView
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        
        // Configure the top level destinations - no back button will be shown for these
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_leaderboard,
                R.id.navigation_tasks,
                R.id.navigation_camera,
                R.id.navigation_profile
            )
        )
        
        navView.setupWithNavController(navController)
        
        // Bildirim sayacını takip et
        setupNotificationBadge()
        
        // Check if user is logged in
        checkCurrentUser()
        
        // TaskCompletionActivity'den gelen intent'i kontrol et
        handleTaskCompletionIntent()
    }
    
    private fun setupNotificationBadge() {
        cameraViewModel.unreadCount.observe(this) { count ->
            Log.d("MainActivity", "unreadCount değişti: $count")
            
            try {
                val badgeDrawable = binding.navView.getOrCreateBadge(R.id.navigation_camera)
                
                if (count > 0) {
                    badgeDrawable.isVisible = true
                    badgeDrawable.number = count
                    // Badge'in arkaplan rengini kırmızı olarak ayarla
                    badgeDrawable.backgroundColor = resources.getColor(android.R.color.holo_red_light, theme)
                    // Badge'in gösterilen metin rengini beyaz olarak ayarla
                    badgeDrawable.badgeTextColor = resources.getColor(android.R.color.white, theme)
                    
                    Log.d("MainActivity", "Badge güncellendi: $count")
                } else {
                    badgeDrawable.isVisible = false
                    Log.d("MainActivity", "Badge gizlendi: $count")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Badge güncellenirken hata: ${e.message}", e)
                
                // Alternatif yaklaşım - menü öğesine özel bir ikon oluştur
                try {
                    val menu = binding.navView.menu
                    val menuItem = menu.findItem(R.id.navigation_camera)
                    
                    if (count > 0) {
                        // Bildirim olan ikon
                        menuItem.setIcon(R.drawable.ic_notification_alert)
                        Log.d("MainActivity", "Alternatif: Bildirim ikonu ayarlandı")
                    } else {
                        // Normal ikon
                        menuItem.setIcon(R.drawable.ic_camera)
                        Log.d("MainActivity", "Alternatif: Normal ikon ayarlandı")
                    }
                } catch (e2: Exception) {
                    Log.e("MainActivity", "Alternatif badge güncellemesi de başarısız: ${e2.message}", e2)
                }
            }
        }
        
        // Bildirimler sayfasına gidildiğinde bildirimleri yükle ve otomatik olarak okunmuş olarak işaretle
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_camera) {
                // Bildirimler sekmesine geçildiğinde tüm bildirimleri okundu olarak işaretle
                cameraViewModel.markAllAsRead()
                Log.d("MainActivity", "Kamera sekmesine geçildi. Bildirimler okundu olarak işaretlendi.")
            }
        }
    }
    
    fun signOut() {
        // Sign out from Firebase
        auth.signOut()
        
        // Navigate back to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        // Clear the back stack so user can't go back to MainActivity after logout
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun checkCurrentUser() {
        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If not signed in, go to login screen
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // User is signed in, can display user info if needed
            val displayName = currentUser.displayName
            val userName = if (!displayName.isNullOrEmpty()) {
                displayName
            } else {
                // DisplayName yoksa email'den @ öncesini al
                currentUser.email?.substringBefore("@") ?: "Kullanıcı"
            }
            Toast.makeText(this, "Hoş geldiniz, $userName", Toast.LENGTH_SHORT).show()
            
            // Okunmamış bildirimleri kontrol et
            cameraViewModel.checkUnreadNotifications()
        }
    }
    
    private fun handleTaskCompletionIntent() {
        // TaskCompletionActivity'den "Görevlere Dön" ile gelindi mi kontrol et
        if (intent.getBooleanExtra("SHOW_TASKS_FRAGMENT", false)) {
            // Tasks fragment'ını göster
            navController.navigate(R.id.navigation_tasks)
            Log.d("MainActivity", "TaskCompletionActivity'den yönlendirme: Tasks fragment'ı gösteriliyor")
        }
    }
    
    private fun loadUserThemePreference() {
        ThemeManager.loadThemeFromFirebase(this) { isDarkMode ->
            // Firebase'den gelen tema tercihini uygula
            if (isDarkMode != ThemeManager.isDarkModeEnabled(this)) {
                // Tema değişmişse aktiviteyi yeniden başlat
                runOnUiThread {
                    ThemeManager.applyTheme(this, isDarkMode)
                    recreate()
                }
            } else {
                // Tema aynıysa sadece mevcut temayı uygula
                ThemeManager.applyTheme(this, isDarkMode)
            }
        }
    }
}