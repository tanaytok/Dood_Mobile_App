package com.example.dodoprojectv2.ui.home

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentHomeBinding
import com.example.dodoprojectv2.ui.profile.ProfileFragment
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.Navigation
import com.example.dodoprojectv2.ui.UserProfilePopup

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var userSearchAdapter: UserSearchAdapter
    private lateinit var postAdapter: PostAdapter
    private lateinit var commentAdapter: CommentAdapter
    
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
                // Kullanıcı profiline gitme
                navigateToUserProfile(user.userId)
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
            onLikeLongClicked = { post, view ->
                // Beğen butonuna uzun basıldığında
                showLikesPopup(post.postId, view)
                true // Olayı tükettiğimizi bildiriyoruz
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
    
    private fun showCommentsDialog(post: PostModel) {
        context?.let { ctx ->
            // Bottom Sheet olarak göstermek için Dialog oluştur
            val dialog = Dialog(ctx, R.style.BottomSheetDialogTheme)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_comments)
            dialog.window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                setGravity(android.view.Gravity.BOTTOM)
                attributes.windowAnimations = R.style.BottomSheetAnimation
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            
            // Dialog bileşenlerini bul
            val recyclerViewComments = dialog.findViewById<RecyclerView>(R.id.recycler_view_comments)
            val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar)
            val textEmptyComments = dialog.findViewById<TextView>(R.id.text_empty_comments)
            val editTextComment = dialog.findViewById<EditText>(R.id.edit_text_comment)
            val buttonSendComment = dialog.findViewById<ImageButton>(R.id.button_send_comment)
            val titleText = dialog.findViewById<TextView>(R.id.text_title)
            
            // Başlığı ayarla
            titleText.text = "${post.username}'in Yorumları"
            
            // Yorum adaptörünü ayarla
            commentAdapter = CommentAdapter(
                onUserClicked = { userId ->
                    // Dialog'u kapat ve kullanıcı profiline git
                    dialog.dismiss()
                    navigateToUserProfile(userId)
                }
            )
            
            recyclerViewComments.apply {
                layoutManager = LinearLayoutManager(ctx)
                adapter = commentAdapter
            }
            
            // Yorum gönderme butonunu ayarla
            buttonSendComment.setOnClickListener {
                val commentText = editTextComment.text.toString().trim()
                if (commentText.isNotEmpty()) {
                    homeViewModel.addComment(post.postId, commentText)
                    editTextComment.text.clear()
                }
            }
            
            // LiveData gözlemcileri oluştur
            val loadingObserver = androidx.lifecycle.Observer<Boolean> { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
            
            val emptyObserver = androidx.lifecycle.Observer<Boolean> { isEmpty ->
                textEmptyComments.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
            
            val commentsObserver = androidx.lifecycle.Observer<List<CommentModel>> { comments ->
                commentAdapter.updateComments(comments)
                if (comments.isNotEmpty()) {
                    recyclerViewComments.scrollToPosition(0)
                }
            }
            
            val errorObserver = androidx.lifecycle.Observer<String> { errorMsg ->
                if (!errorMsg.isNullOrEmpty()) {
                    Snackbar.make(dialog.findViewById(android.R.id.content), errorMsg, Snackbar.LENGTH_SHORT).show()
                }
            }
            
            // LiveData'lara gözlemcileri ekle
            homeViewModel.isLoadingComments.observe(viewLifecycleOwner, loadingObserver)
            homeViewModel.isEmptyComments.observe(viewLifecycleOwner, emptyObserver)
            homeViewModel.comments.observe(viewLifecycleOwner, commentsObserver)
            homeViewModel.errorMessage.observe(viewLifecycleOwner, errorObserver)
            
            // Dialog kapatıldığında gözlemcileri kaldır
            dialog.setOnDismissListener {
                homeViewModel.isLoadingComments.removeObserver(loadingObserver)
                homeViewModel.isEmptyComments.removeObserver(emptyObserver)
                homeViewModel.comments.removeObserver(commentsObserver)
                homeViewModel.errorMessage.removeObserver(errorObserver)
            }
            
            // Yorumları yükle
            homeViewModel.loadComments(post.postId)
            
            // Dialog'u göster
            dialog.show()
        }
    }

    /**
     * Beğenen kullanıcıları gösteren popup'ı gösterir
     */
    private fun showLikesPopup(postId: String, anchorView: View) {
        try {
            // Popup layoutu oluştur
            val inflater = LayoutInflater.from(requireContext())
            val popupView = inflater.inflate(R.layout.popup_likes, null)
            
            // Popup penceresi oluştur
            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            
            // Popup için görünümleri bul
            val recyclerView = popupView.findViewById<RecyclerView>(R.id.recycler_view_likes)
            val progressBar = popupView.findViewById<ProgressBar>(R.id.progress_bar)
            val textEmpty = popupView.findViewById<TextView>(R.id.text_empty)
            
            // RecyclerView'ı ayarla
            val likeAdapter = LikeUserAdapter(emptyList()) { userId ->
                // Kullanıcıya tıklandığında profil sayfasına git
                popupWindow.dismiss()
                navigateToUserProfile(userId)
            }
            
            recyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = likeAdapter
            }
            
            // Yükleniyor durumunu göster
            progressBar.visibility = View.VISIBLE
            textEmpty.visibility = View.GONE
            
            // Firestore'dan beğenenleri yükle
            homeViewModel.loadPostLikes(postId) { users, error ->
                progressBar.visibility = View.GONE
                
                if (error != null) {
                    textEmpty.text = "Yüklenirken hata: $error"
                    textEmpty.visibility = View.VISIBLE
                    return@loadPostLikes
                }
                
                if (users.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                } else {
                    textEmpty.visibility = View.GONE
                    likeAdapter.updateUsers(users)
                }
            }
            
            // Popup'ı göster
            popupWindow.elevation = 10f
            popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
        } catch (e: Exception) {
            Log.e(TAG, "Beğenenler popup gösterilirken hata: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 