package com.example.dodoprojectv2.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentHomeBinding
import com.example.dodoprojectv2.ui.profile.ProfileFragment
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userSearchAdapter: UserSearchAdapter
    private lateinit var postAdapter: PostAdapter
    
    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        setupSearchView()
        setupRecyclerViews()
        setupObservers()
        setupSwipeRefresh()
        
        return root
    }
    
    private fun setupSearchView() {
        // Arama çubuğu
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Arama terimi değiştiğinde sonuçları sıfırla
                if (s.isNullOrBlank()) {
                    homeViewModel.clearSearch()
                }
            }
            
            override fun afterTextChanged(s: Editable?) {
                // Kullanıcı yazmayı bıraktığında arama yap
                if (!s.isNullOrBlank() && s.length >= 3) {
                    homeViewModel.searchUsers(s.toString())
                }
            }
        })
        
        binding.editTextSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.editTextSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    homeViewModel.searchUsers(query)
                }
                return@setOnEditorActionListener true
            }
            false
        }
        
        binding.buttonSearch.setOnClickListener {
            val query = binding.editTextSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                homeViewModel.searchUsers(query)
            } else {
                homeViewModel.clearSearch()
            }
        }
    }
    
    private fun setupRecyclerViews() {
        // Arama sonuçları için RecyclerView
        userSearchAdapter = UserSearchAdapter(
            onUserClicked = { user ->
                // Kullanıcı profiline gitme işlemini kaldırdık
                // Burada hiçbir şey yapmıyoruz
            },
            onFollowClicked = { user, isFollowing ->
                // Takip et/bırak
                homeViewModel.followUser(user.userId, isFollowing)
            }
        )
        
        binding.recyclerViewSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userSearchAdapter
        }
        
        // Akış için RecyclerView
        postAdapter = PostAdapter(
            onPostClicked = { post ->
                // Gönderiye tıklandığında yapılacak işlem
                // Örneğin detay görüntüleme
            },
            onLikeClicked = { post ->
                // Beğen butonuna tıklandığında
                homeViewModel.likePost(post.postId)
            },
            onUserClicked = { userId ->
                // Kullanıcı profiline git
                navigateToUserProfile(userId)
            },
            onCommentsClicked = { post ->
                // Yorumları görüntüle
                showCommentsDialog(post)
            }
        )
        
        binding.recyclerViewFeed.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
    }
    
    private fun setupObservers() {
        // Arama sonuçlarını izle
        homeViewModel.searchResults.observe(viewLifecycleOwner) { users ->
            userSearchAdapter.updateUsers(users)
            userSearchAdapter.checkFollowStatus()
        }
        
        // Arama modu durumunu izle
        homeViewModel.isSearchMode.observe(viewLifecycleOwner) { isSearchMode ->
            if (isSearchMode) {
                binding.cardViewSearchResults.visibility = View.VISIBLE
                binding.swipeRefreshLayout.visibility = View.GONE
            } else {
                binding.cardViewSearchResults.visibility = View.GONE
                binding.swipeRefreshLayout.visibility = View.VISIBLE
            }
        }
        
        // Arama sonuçları boş ise
        homeViewModel.isEmptySearchResults.observe(viewLifecycleOwner) { isEmpty ->
            binding.textNoSearchResults.visibility = if (isEmpty && homeViewModel.isSearchMode.value == true) View.VISIBLE else View.GONE
            
            // RecyclerView'in görünürlüğünü de buna göre ayarla
            if (isEmpty && homeViewModel.isSearchMode.value == true) {
                binding.recyclerViewSearchResults.visibility = View.GONE
            } else if (homeViewModel.isSearchMode.value == true) {
                binding.recyclerViewSearchResults.visibility = View.VISIBLE
            }
        }
        
        // Akış gönderilerini izle
        homeViewModel.feedPosts.observe(viewLifecycleOwner) { posts ->
            postAdapter.updatePosts(posts)
        }
        
        // Akış boş ise
        homeViewModel.isEmptyFeed.observe(viewLifecycleOwner) { isEmpty ->
            binding.textEmptyFeed.visibility = if (isEmpty && homeViewModel.isSearchMode.value != true) View.VISIBLE else View.GONE
        }
        
        // Yükleniyor durumunu izle
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        
        // Hata mesajlarını izle
        homeViewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            homeViewModel.clearSearch()
            homeViewModel.loadFeedPosts()
        }
    }
    
    private fun navigateToUserProfile(userId: String) {
        // TODO: Kullanıcı profiline yönlendirme mantığını ekle
        // Bu geçici bir çözümdür - gerçek uygulamada farklı kullanıcı profillerini görüntüleme mantığı eklenmelidir
        val fragment = ProfileFragment()
        val bundle = Bundle()
        bundle.putString("userId", userId)
        fragment.arguments = bundle
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment_activity_main, fragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun showCommentsDialog(post: PostModel) {
        // TODO: Yorumları göstermek için bir dialog göster
        Snackbar.make(binding.root, "Yorumlar özelliği henüz eklenmedi", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 