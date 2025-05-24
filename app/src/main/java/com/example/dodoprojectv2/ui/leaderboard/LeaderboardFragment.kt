package com.example.dodoprojectv2.ui.leaderboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentLeaderboardBinding
import com.example.dodoprojectv2.ui.UserProfilePopup

class LeaderboardFragment : Fragment() {

    private val TAG = "LeaderboardFragment"
    
    private lateinit var leaderboardViewModel: LeaderboardViewModel
    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        leaderboardViewModel = ViewModelProvider(this).get(LeaderboardViewModel::class.java)
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        
        return root
    }
    
    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter { userId ->
            // Kullanıcı profiline git - Popup kullan
            showUserProfilePopup(userId)
        }
        
        binding.recyclerViewLeaderboard.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = leaderboardAdapter
        }
    }
    
    // Kullanıcı profili popup'ını göster
    private fun showUserProfilePopup(userId: String) {
        // Null veya boş userId kontrolü
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "showUserProfilePopup: userId boş veya null")
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
    
    private fun setupObservers() {
        // Kullanıcıları izle
        leaderboardViewModel.leaderboardUsers.observe(viewLifecycleOwner) { users ->
            leaderboardAdapter.updateUsers(users)
        }
        
        // Boş durum için
        leaderboardViewModel.isEmpty.observe(viewLifecycleOwner) { isEmpty ->
            binding.textEmptyLeaderboard.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
        
        // Yükleniyor durumunu izle
        leaderboardViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        
        // Kalan süreyi izle
        leaderboardViewModel.timeRemaining.observe(viewLifecycleOwner) { timeRemaining ->
            binding.textTimeRemaining.text = timeRemaining
        }
        
        // Hata mesajlarını izle
        leaderboardViewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            leaderboardViewModel.loadLeaderboard()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 