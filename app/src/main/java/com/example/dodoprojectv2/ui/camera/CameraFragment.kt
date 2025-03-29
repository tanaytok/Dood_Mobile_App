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
import com.example.dodoprojectv2.ui.profile.ProfileFragment

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
                // Bildirime tıklama işlemini kaldırdık
                // Burada hiçbir şey yapmıyoruz
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
            "like", "comment" -> {
                // Gönderiye git (eğer ilgili fonksiyon eklenirse)
                Toast.makeText(context, "Gönderi detayları henüz eklenmedi", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Diğer bildirim türleri
                Toast.makeText(context, notification.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun navigateToUserProfile(userId: String) {
        try {
            val fragment = ProfileFragment()
            val bundle = Bundle()
            bundle.putString("userId", userId)
            fragment.arguments = bundle
            
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, fragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı profiline giderken hata: ${e.message}", e)
            Toast.makeText(context, "Kullanıcı profiline gidilemedi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 