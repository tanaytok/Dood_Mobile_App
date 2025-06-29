package com.example.dodoprojectv2.ui.camera

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentCameraBinding
import com.example.dodoprojectv2.ui.UserProfilePopup

class CameraFragment : Fragment() {

    private lateinit var cameraViewModel: CameraViewModel
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var notificationAdapter: NotificationAdapter
    
    private val TAG = "CameraFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        cameraViewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        setupClearButton()
        
        binding.textCamera.text = "Bildirimler"
        
        return root
    }
    
    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            onNotificationClicked = { notification ->
                handleNotificationClick(notification)
            }
        )
        
        binding.recyclerViewNotifications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notificationAdapter
        }
    }
    
    private fun setupObservers() {
        // Bildirimleri izle
        cameraViewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            notificationAdapter.updateNotifications(notifications)
        }
        
        // Boş durum için
        cameraViewModel.isEmpty.observe(viewLifecycleOwner) { isEmpty ->
            binding.textEmptyNotifications.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
        
        // Yükleniyor durumunu izle
        cameraViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        
        // Hata mesajlarını izle
        cameraViewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            cameraViewModel.loadNotifications()
        }
    }
    
    private fun setupClearButton() {
        binding.buttonClearNotifications.setOnClickListener {
            showClearConfirmationDialog()
        }
    }
    
    private fun showClearConfirmationDialog() {
        context?.let {
            AlertDialog.Builder(it)
                .setTitle("Bildirimleri Temizle")
                .setMessage("Tüm bildirimleriniz silinecek. Emin misiniz?")
                .setPositiveButton("Evet") { _, _ ->
                    cameraViewModel.clearAllNotifications()
                }
                .setNegativeButton("İptal", null)
                .show()
        }
    }
    
    private fun handleNotificationClick(notification: NotificationModel) {
        Log.d(TAG, "Bildirime tıklandı: ${notification.type}")
        
        when (notification.type) {
            "follow", "unfollow" -> {
                // Takip eden/çıkan kullanıcının profiline git
                navigateToUserProfile(notification.senderId)
            }
            "like" -> {
                // Beğenilen gönderiye git
                if (notification.relatedItemId.isNotEmpty()) {
                    // TODO: İleride gönderi detay sayfası eklenirse bu kısım güncellenecek
                    Toast.makeText(context, "Beğenilen gönderi detayları henüz eklenmedi", Toast.LENGTH_SHORT).show()
                    
                    // Alternatif olarak beğenen kullanıcının profiline git
                    navigateToUserProfile(notification.senderId)
                } else {
                    Toast.makeText(context, notification.message, Toast.LENGTH_SHORT).show()
                }
            }
            "comment" -> {
                // Yorum yapılan gönderiye git
                if (notification.relatedItemId.isNotEmpty()) {
                    // TODO: İleride gönderi detay sayfası eklenirse bu kısım güncellenecek
                    Toast.makeText(context, "Yorum yapılan gönderi detayları henüz eklenmedi", Toast.LENGTH_SHORT).show()
                    
                    // Alternatif olarak yorum yapan kullanıcının profiline git
                    navigateToUserProfile(notification.senderId)
                } else {
                    Toast.makeText(context, notification.message, Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Diğer bildirim türleri
                Toast.makeText(context, notification.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun navigateToUserProfile(userId: String) {
        // Null veya boş userId kontrolü
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "navigateToUserProfile: userId boş veya null")
            return
        }
        
        try {
            context?.let { ctx ->
                val userProfilePopup = UserProfilePopup(ctx)
                userProfilePopup.showUserProfile(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı profili popup gösterilirken hata: ${e.message}", e)
            Toast.makeText(context, "Kullanıcı profili açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 