package com.example.dodoprojectv2.ui.profile

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.MainActivity
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import android.widget.ImageButton
import com.example.dodoprojectv2.ui.home.CommentAdapter
import com.example.dodoprojectv2.ui.home.CommentModel
import java.util.Calendar
import android.widget.PopupWindow
import com.example.dodoprojectv2.ui.home.LikeUserAdapter
import com.example.dodoprojectv2.ui.home.LikeUserModel
import androidx.navigation.Navigation
import com.example.dodoprojectv2.ui.UserProfilePopup
import com.example.dodoprojectv2.utils.ThemeManager
import com.google.firebase.firestore.FieldValue

class ProfileFragment : Fragment() {

    private lateinit var profileViewModel: ProfileViewModel
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var selectedImageUri: Uri? = null
    private lateinit var photoAdapter: PhotoAdapter
    
    private lateinit var progressBar: ProgressBar
    
    private val TAG = "ProfileFragment"
    
    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null && data.data != null) {
                selectedImageUri = data.data
                uploadImageToFirebase(selectedImageUri!!)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView başladı")
        
        try {
            profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
            
            _binding = FragmentProfileBinding.inflate(inflater, container, false)
            val root: View = binding.root
            
            // Önce loading göster
            progressBar = binding.root.findViewById(R.id.progress_bar)
            progressBar.visibility = View.VISIBLE
            
            // Initialize Firebase components
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
            
            // Önce varsayılan görünümü hazırla
            setDummyData()
            
            setupUIElements()
            setupRecyclerView()
            
            // Argüman olarak userId geldi mi kontrol et
            val userId = arguments?.getString("userId")
            Log.d(TAG, "Gelen userId: $userId, Mevcut kullanıcı: ${auth.currentUser?.uid}")
            
            // Geçerli userId yoksa veya kendi ID'mize eşitse kendi profilimizi göster
            if (userId.isNullOrEmpty() || userId == auth.currentUser?.uid) {
                Log.d(TAG, "Kendi profilimizi görüntülüyoruz")
                // Kendi profilimizi görüntülüyoruz
                loadUserProfile()
                loadUserPhotos()
                hideFollowButton()
            } else {
                Log.d(TAG, "Başka bir kullanıcının profilini görüntülüyoruz: $userId")
                // Başka bir kullanıcının profili görüntüleniyor
                loadUserProfile(userId)
                loadUserPhotos(userId)
                setupFollowButton(userId)
            }
            
            return root
        } catch (e: Exception) {
            Log.e(TAG, "Fragment oluşturulurken hata oluştu: ${e.message}", e)
            
            // Eğer binding oluşturulduysa ve kontekst mevcutsa hata mesajı göster
            if (_binding != null && context != null) {
                Toast.makeText(context, "Bir hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
            
            // Hata durumunda da bir View döndürmemiz gerekli
            if (_binding != null) {
                return binding.root
            } else {
                // Binding oluşturulamadıysa varsayılan layout oluştur
                val fallbackView = TextView(context)
                fallbackView.text = "Profil yüklenemedi"
                fallbackView.gravity = android.view.Gravity.CENTER
                return fallbackView
            }
        }
    }
    
    private fun setDummyData() {
        try {
            Log.d(TAG, "Dummy veri yükleniyor")
            // Dummy veriler ile arayüzü doldur (Firebase verisi yüklenene kadar gösterilir)
            binding.textUsername.text = "KullanıcıAdı"
            binding.textBio.text = "Bu bir örnek biyografidir. Burası kullanıcı biyografisini gösterir."
            binding.textStreakCount.text = "5"
            binding.textFollowersCount.text = "120"
            binding.textFollowingCount.text = "65"
        } catch (e: Exception) {
            Log.e(TAG, "Dummy veri yüklenirken hata: ${e.message}", e)
        }
    }
    
    private fun setupUIElements() {
        try {
            Log.d(TAG, "UI elemanları yapılandırılıyor")
            // Set up change photo button
            binding.buttonChangePhoto.setOnClickListener {
                Log.d(TAG, "Fotoğraf değiştirme butonuna tıklandı")
                openImageChooser()
            }
            
            // Setup settings button at top right
            binding.buttonEditProfileTop.setOnClickListener {
                Log.d(TAG, "Profil ayarları butonuna tıklandı (üst)")
                showSettingsDialog()
            }
            
            // Setup bio edit button
            binding.buttonEditBio.setOnClickListener {
                Log.d(TAG, "Biyografi düzenleme butonuna tıklandı")
                showEditProfileDialog()
            }
            
            // Setup click listeners for stats cards
            setupStatsClickListeners()
            
            // Glide ile varsayılan resmi yükle
            Glide.with(this)
                .load(R.drawable.default_profile)
                .into(binding.imageProfile)
        } catch (e: Exception) {
            Log.e(TAG, "UI elemanları yapılandırılırken hata: ${e.message}", e)
        }
    }
    
    private fun setupStatsClickListeners() {
        try {
            // Get currently viewed user ID
            val viewedUserId = arguments?.getString("userId") ?: auth.currentUser?.uid ?: ""
            
            // Followers card click listener
            binding.layoutStats.getChildAt(1).setOnClickListener {
                Log.d(TAG, "Takipçiler kartına tıklandı")
                showFollowersDialog(viewedUserId, "followers")
            }
            
            // Following card click listener
            binding.layoutStats.getChildAt(2).setOnClickListener {
                Log.d(TAG, "Takip edilenler kartına tıklandı")
                showFollowersDialog(viewedUserId, "following")
            }
        } catch (e: Exception) {
            Log.e(TAG, "İstatistik click listener'ları kurulurken hata: ${e.message}", e)
        }
    }

    private fun setupRecyclerView() {
        try {
            // RecyclerView düzenini ayarla
            val layoutManager = GridLayoutManager(context, 3)
            binding.recyclerViewPhotos.layoutManager = layoutManager
            
            // Adaptörü oluştur ve bağla
            photoAdapter = PhotoAdapter(emptyList()) { photo ->
                // Fotoğrafa tıklandığında detay diyaloğunu göster
                showPhotoDetailDialog(photo)
            }
            
            binding.recyclerViewPhotos.adapter = photoAdapter
        } catch (e: Exception) {
            Log.e(TAG, "RecyclerView kurulurken hata: ${e.message}", e)
        }
    }
    
    private fun loadUserProfile(userId: String? = null) {
        try {
            Log.d(TAG, "Kullanıcı profili yükleniyor")
            val currentUser = auth.currentUser
            
            if (currentUser == null) {
                Log.e(TAG, "Giriş yapmış kullanıcı bulunamadı")
                Toast.makeText(context, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Gösterilecek kullanıcı ID'si
            val profileUserId = userId ?: currentUser.uid
            
            if (profileUserId.isEmpty()) {
                Log.e(TAG, "Geçersiz kullanıcı ID: boş")
                Toast.makeText(context, "Geçersiz kullanıcı ID", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "Yüklenecek kullanıcı profili ID: $profileUserId")
            
            // Önce UI'ı hazırla ve loading göster
            progressBar.visibility = View.VISIBLE
            
            // Get user data from Firestore
            firestore.collection("users").document(profileUserId)
                .get()
                .addOnSuccessListener { document ->
                    progressBar.visibility = View.GONE
                    
                    try {
                        Log.d(TAG, "Firestore verisi başarıyla alındı")
                        if (document != null && document.exists()) {
                            Log.d(TAG, "Firestore dokümanı mevcut")
                            // Username
                            val username = document.getString("username") ?: ""
                            Log.d(TAG, "Kullanıcı adı: $username")
                            binding.textUsername.text = username
                            
                            // Bio
                            val bio = document.getString("bio") ?: "Biyografi eklenmemiş"
                            Log.d(TAG, "Biyografi: $bio")
                            binding.textBio.text = bio
                            
                            // Stats
                            val streak = document.getLong("streak") ?: 0
                            Log.d(TAG, "Seri: $streak")
                            binding.textStreakCount.text = streak.toString()
                            
                            // Gerçek takipçi ve takip sayılarını hesapla ve güncelle
                            calculateAndUpdateFollowCounts(profileUserId)
                            
                            // Profile photo
                            val profilePhotoUrl = document.getString("profilePhotoUrl") ?: ""
                            Log.d(TAG, "Profil foto URL: $profilePhotoUrl")
                            if (profilePhotoUrl.isNotEmpty()) {
                                context?.let { ctx ->
                                    try {
                                        Glide.with(ctx)
                                            .load(profilePhotoUrl)
                                            .placeholder(R.drawable.default_profile)
                                            .into(binding.imageProfile)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Profil fotoğrafı yüklenirken hata: ${e.message}", e)
                                        // Hata durumunda varsayılan resmi göster
                                        binding.imageProfile.setImageResource(R.drawable.default_profile)
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "Firestore dokümanı bulunamadı veya boş")
                            Toast.makeText(context, "Kullanıcı verileri bulunamadı", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Firestore veri işlemede hata: ${e.message}", e)
                        Toast.makeText(context, "Profil verisi işlenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Firestore verisi alınamadı: ${e.message}", e)
                    Toast.makeText(context, "Profil bilgileri yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Kullanıcı profili yüklenirken hata: ${e.message}", e)
            Toast.makeText(context, "Profil yüklenirken beklenmeyen hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadUserPhotos(userId: String? = null) {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "Giriş yapmış kullanıcı bulunamadı")
                Toast.makeText(context, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Gösterilecek kullanıcı ID'si
            val profileUserId = userId ?: currentUser.uid
            
            if (profileUserId.isEmpty()) {
                Log.e(TAG, "Geçersiz kullanıcı ID: boş")
                Toast.makeText(context, "Geçersiz kullanıcı ID", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "Yüklenecek kullanıcı fotoğrafları ID: $profileUserId")
            
            // Kendi profilimizi görüntülüyorsak veya takip ettiğimiz bir kullanıcıysa
            val isOwnProfile = profileUserId == currentUser.uid
            
            if (isOwnProfile) {
                // Kendi profilimiz - direkt fotoğrafları yükle
                loadPhotosFromFirestore(profileUserId)
            } else {
                // Başka birinin profili - takip durumunu kontrol et
                firestore.collection("follows")
                    .document("${currentUser.uid}_${profileUserId}")
                    .get()
                    .addOnSuccessListener { document ->
                        val isFollowing = document.exists()
                        
                        if (isFollowing) {
                            // Takip ediyorsak fotoğrafları yükle
                            loadPhotosFromFirestore(profileUserId)
                        } else {
                            // Takip etmiyorsak takip et mesajını göster
                            binding.recyclerViewPhotos.visibility = View.GONE
                            binding.textNoPhotos.visibility = View.GONE
                            binding.textFollowToSee.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip durumu kontrol edilirken hata: ${e.message}", e)
                        binding.progressPhotos.visibility = View.GONE
                        binding.recyclerViewPhotos.visibility = View.GONE
                        binding.textNoPhotos.visibility = View.GONE
                        binding.textFollowToSee.visibility = View.VISIBLE
                    }
            }
        } catch (e: Exception) {
            binding.progressPhotos.visibility = View.GONE
            binding.textNoPhotos.visibility = View.GONE
            binding.textFollowToSee.visibility = View.GONE
            Log.e(TAG, "Fotoğraflar yüklenirken hata: ${e.message}", e)
            Toast.makeText(context, "Fotoğraflar yüklenirken beklenmeyen hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Firestore'dan fotoğrafları yükleyen yardımcı metod
    private fun loadPhotosFromFirestore(profileUserId: String) {
        binding.progressPhotos.visibility = View.VISIBLE
        binding.textNoPhotos.visibility = View.GONE
        binding.textFollowToSee.visibility = View.GONE
        
        firestore.collection("user_photos")
            .whereEqualTo("userId", profileUserId)
            .whereEqualTo("isPublic", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20) // Son 20 fotoğrafı getir
            .get()
            .addOnSuccessListener { documents ->
                binding.progressPhotos.visibility = View.GONE
                
                if (documents.isEmpty) {
                    binding.recyclerViewPhotos.visibility = View.VISIBLE
                    binding.textNoPhotos.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }
                
                val photos = documents.mapNotNull { doc ->
                    try {
                        UserPhoto(
                            id = doc.id,
                            photoUrl = doc.getString("photoUrl") ?: "",
                            taskId = doc.getString("taskId") ?: "",
                            taskName = doc.getString("taskName") ?: "Görev",
                            timestamp = doc.getLong("timestamp") ?: 0,
                            userId = doc.getString("userId") ?: ""
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Fotoğraf verisi dönüştürülürken hata: ${e.message}", e)
                        null
                    }
                }
                
                if (photos.isEmpty()) {
                    binding.recyclerViewPhotos.visibility = View.VISIBLE
                    binding.textNoPhotos.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewPhotos.visibility = View.VISIBLE
                    binding.textNoPhotos.visibility = View.GONE
                    photoAdapter.updatePhotos(photos)
                }
            }
            .addOnFailureListener { e ->
                binding.progressPhotos.visibility = View.GONE
                binding.textNoPhotos.visibility = View.VISIBLE
                binding.textFollowToSee.visibility = View.GONE
                Log.e(TAG, "Fotoğraflar yüklenirken hata: ${e.message}", e)
                Toast.makeText(context, "Fotoğraflar yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showEditProfileDialog() {
        try {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_edit_profile)
            
            val bioEditText = dialog.findViewById<EditText>(R.id.edit_text_bio)
            val bioLengthTextView = dialog.findViewById<TextView>(R.id.text_bio_length)
            val saveButton = dialog.findViewById<Button>(R.id.button_save)
            val cancelButton = dialog.findViewById<Button>(R.id.button_cancel)
            
            // Mevcut biyografiyi göster
            bioEditText.setText(binding.textBio.text)
            
            // Karakter sayacını güncelle
            val maxLength = 80
            bioLengthTextView.text = "${bioEditText.text.length}/$maxLength"
            
            // Text değişikliğini dinle
            bioEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val length = s?.length ?: 0
                    bioLengthTextView.text = "$length/$maxLength"
                    
                    // Maksimum karakter sayısını aşarsa kaydet butonunu devre dışı bırak
                    saveButton.isEnabled = length <= maxLength
                }
            })
            
            // Kaydet butonunu ayarla
            saveButton.setOnClickListener {
                val newBio = bioEditText.text.toString().trim()
                updateUserBio(newBio)
                dialog.dismiss()
            }
            
            // İptal butonunu ayarla
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Profil düzenleme dialogu gösterilirken hata: ${e.message}", e)
        }
    }
    
    private fun updateUserBio(bio: String) {
        try {
            val currentUser = auth.currentUser ?: return
            
            firestore.collection("users").document(currentUser.uid)
                .update("bio", bio)
                .addOnSuccessListener {
                    binding.textBio.text = bio
                    Toast.makeText(context, "Biyografi güncellendi", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Biyografi güncellenirken hata: ${e.message}", e)
                    Toast.makeText(context, "Biyografi güncellenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Biyografi güncellenirken hata: ${e.message}", e)
        }
    }
    
    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getContent.launch(intent)
    }
    
    private fun uploadImageToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.buttonChangePhoto.isEnabled = false
        
        // Create a reference to the location where we'll store the image
        val storageRef = storage.reference.child("profile_photos/$userId.jpg")
        
        // Upload file to Firebase Storage
        val uploadTask = storageRef.putFile(imageUri)
        
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            
            // Continue with the task to get the download URL
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            binding.buttonChangePhoto.isEnabled = true
            
            if (task.isSuccessful) {
                val downloadUri = task.result
                
                // Update profile photo URL in Firestore
                firestore.collection("users").document(userId)
                    .update("profilePhotoUrl", downloadUri.toString())
                    .addOnSuccessListener {
                        // Load the uploaded image into the ImageView
                        Glide.with(this)
                            .load(downloadUri)
                            .placeholder(R.drawable.default_profile)
                            .into(binding.imageProfile)
                        
                        Toast.makeText(context, "Profil fotoğrafı güncellendi", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Profil verisi güncellenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(context, "Fotoğraf yüklenemedi: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsDialog() {
        try {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_settings)
            
            // Dialog genişliğini ayarla
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Dialog bileşenlerini bul
            val switchDarkMode = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_dark_mode)
            val layoutLogout = dialog.findViewById<View>(R.id.layout_logout)
            val buttonClose = dialog.findViewById<Button>(R.id.button_close)
            
            // Mevcut karanlık mod durumunu ayarla
            switchDarkMode.isChecked = ThemeManager.isDarkModeEnabled(requireContext())
            
            // Karanlık mod switch dinleyicisi
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                // Firebase entegrasyonlu tema değiştirme
                ThemeManager.applyTheme(requireContext(), isChecked)
                
                // Temayı hemen uygula
                activity?.recreate()
            }
            
            // Çıkış yap tıklama olayı
            layoutLogout.setOnClickListener {
                dialog.dismiss()
                
                // Çıkış yap onay dialogu
                val confirmBuilder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                confirmBuilder.setTitle("Çıkış Yap")
                confirmBuilder.setMessage("Hesabınızdan çıkmak istediğinizden emin misiniz?")
                confirmBuilder.setPositiveButton("Evet") { _, _ ->
                    try {
                        val mainActivity = activity as MainActivity
                        mainActivity.signOut()
                    } catch (e: Exception) {
                        Log.e(TAG, "Çıkış yapılırken hata: ${e.message}", e)
                        Toast.makeText(context, "Çıkış yapılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                confirmBuilder.setNegativeButton("İptal", null)
                confirmBuilder.show()
            }
            
            // Kapat butonu tıklama olayı
            buttonClose.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Ayarlar dialogu gösterilirken hata: ${e.message}", e)
        }
    }

    // Fotoğraf detay diyaloğunu göster
    private fun showPhotoDetailDialog(photo: UserPhoto) {
        try {
            context?.let { ctx ->
                // Dialog oluştur
                val dialog = Dialog(ctx)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.dialog_photo_detail)
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                
                // Dialog bileşenlerini bul
                val imagePhoto = dialog.findViewById<ImageView>(R.id.image_photo)
                val textTaskName = dialog.findViewById<TextView>(R.id.text_task_name)
                val textDate = dialog.findViewById<TextView>(R.id.text_date)
                val textLikeCount = dialog.findViewById<TextView>(R.id.text_like_count)
                val textCommentCount = dialog.findViewById<TextView>(R.id.text_comment_count)
                val buttonClose = dialog.findViewById<View>(R.id.button_close)
                val recyclerViewComments = dialog.findViewById<RecyclerView>(R.id.recycler_view_comments)
                val editTextComment = dialog.findViewById<EditText>(R.id.edit_text_comment)
                val buttonSendComment = dialog.findViewById<ImageButton>(R.id.button_send_comment)
                val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar)
                val textEmptyComments = dialog.findViewById<TextView>(R.id.text_empty_comments)
                val buttonTogglePanel = dialog.findViewById<ImageButton>(R.id.button_toggle_panel)
                val panelDetail = dialog.findViewById<View>(R.id.panel_detail)
                val guideline = dialog.findViewById<View>(R.id.guideline)
                
                // Toolbar başlığını ayarla
                dialog.findViewById<TextView>(R.id.text_title)?.text = "Görev Fotoğrafı"
                
                // Fotoğraf detaylarını göster
                Glide.with(ctx)
                    .load(photo.photoUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .into(imagePhoto)
                
                textTaskName.text = photo.taskName
                
                // Tarihi formatla
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = photo.timestamp
                val dateFormat = DateFormat.format("dd MMMM yyyy", calendar).toString()
                textDate.text = dateFormat
                
                // Beğeni ve yorum sayılarını yükle
                firestore.collection("user_photos").document(photo.id)
                    .get()
                    .addOnSuccessListener { document ->
                        val likesCount = document.getLong("likesCount")?.toInt() ?: 0
                        val commentsCount = document.getLong("commentsCount")?.toInt() ?: 0
                        
                        textLikeCount.text = "$likesCount beğeni"
                        textCommentCount.text = "$commentsCount yorum"
                        
                        // Beğeni istatistiğine uzun tıklama olayı ekle
                        try {
                            val layoutStats = dialog.findViewById<View>(R.id.layout_stats)
                            if (layoutStats is ViewGroup && layoutStats.childCount > 0) {
                                val likeLayout = layoutStats.getChildAt(0)
                                likeLayout?.setOnLongClickListener {
                                    showLikesPopup(photo.id, likeLayout)
                                    true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Beğeni layout'u alınırken hata: ${e.message}", e)
                        }
                        
                        // Beğeni sayısı göstergesine de uzun tıklama olayı ekle
                        textLikeCount.setOnLongClickListener {
                            showLikesPopup(photo.id, textLikeCount)
                            true
                        }
                    }
                
                // Panel aç/kapat tuşu için durum değişkeni
                var isPanelOpen = true
                
                // Panel aç/kapat tuşu için tıklama dinleyicisi
                buttonTogglePanel?.setOnClickListener {
                    isPanelOpen = !isPanelOpen
                    
                    if (isPanelOpen) {
                        // Paneli aç
                        panelDetail?.visibility = View.VISIBLE
                        buttonTogglePanel.setImageResource(R.drawable.ic_arrow_right)
                        
                        // Guideline'ı normal konumuna getir
                        try {
                            val params = guideline?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                            params?.guidePercent = 0.45f
                            guideline?.layoutParams = params
                        } catch (e: Exception) {
                            Log.e(TAG, "Guideline ayarlanırken hata: ${e.message}", e)
                        }
                    } else {
                        // Paneli kapat
                        panelDetail?.visibility = View.GONE
                        buttonTogglePanel.setImageResource(R.drawable.ic_arrow_left)
                        
                        // Guideline'ı sağa kaydır (tam ekran fotoğraf için)
                        try {
                            val params = guideline?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                            params?.guidePercent = 0.97f
                            guideline?.layoutParams = params
                        } catch (e: Exception) {
                            Log.e(TAG, "Guideline ayarlanırken hata: ${e.message}", e)
                        }
                    }
                }
                
                // Yorum adaptörünü oluştur
                val commentAdapter = CommentAdapter(emptyList()) { userId ->
                    dialog.dismiss()
                    navigateToUserProfile(userId)
                }
                
                recyclerViewComments?.apply {
                    layoutManager = LinearLayoutManager(ctx)
                    adapter = commentAdapter
                }
                
                // Yorumları yükle
                if (recyclerViewComments != null && progressBar != null && textEmptyComments != null) {
                    loadCommentsForPhoto(photo.id, commentAdapter, progressBar, textEmptyComments)
                }
                
                // Yorum gönderme butonunu ayarla
                buttonSendComment?.setOnClickListener {
                    val commentText = editTextComment?.text?.toString()?.trim() ?: ""
                    if (commentText.isNotEmpty() && editTextComment != null) {
                        addCommentToPhoto(photo.id, commentText, editTextComment, commentAdapter)
                    }
                }
                
                // Kapat butonunu ayarla
                buttonClose?.setOnClickListener {
                    dialog.dismiss()
                }
                
                // Dialog'u göster
                dialog.show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fotoğraf detay diyaloğu gösterilirken hata: ${e.message}", e)
            Toast.makeText(context, "Fotoğraf detayları gösterilirken bir hata oluştu", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadCommentsForPhoto(photoId: String, adapter: CommentAdapter, progressBar: ProgressBar, emptyText: TextView) {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        
        firestore.collection("post_comments")
            .whereEqualTo("postId", photoId)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                
                if (documents.isEmpty) {
                    emptyText.visibility = View.VISIBLE
                    adapter.updateComments(emptyList())
                    return@addOnSuccessListener
                }
                
                // Kullanıcı bilgilerini depolamak için map
                val userInfoMap = mutableMapOf<String, Pair<String, String>>() // userId -> (username, profilePhotoUrl)
                
                // Önce tüm kullanıcı bilgilerini almak için kullanıcı ID'lerini topla
                val userIds = documents.documents.mapNotNull { it.getString("userId") }.distinct()
                
                if (userIds.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    adapter.updateComments(emptyList())
                    return@addOnSuccessListener
                }
                
                // Kullanıcı bilgilerini yükle
                firestore.collection("users")
                    .whereIn("userId", userIds)
                    .get()
                    .addOnSuccessListener { userDocuments ->
                        // Kullanıcı bilgilerini map'e kaydet
                        for (userDoc in userDocuments) {
                            val userId = userDoc.getString("userId") ?: continue
                            val username = userDoc.getString("username") ?: ""
                            val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                            userInfoMap[userId] = Pair(username, profilePhotoUrl)
                        }
                        
                        // Yorumları oluştur
                        val commentsList = documents.mapNotNull { doc ->
                            try {
                                val userId = doc.getString("userId") ?: return@mapNotNull null
                                val userInfo = userInfoMap[userId] ?: Pair("", "")
                                
                                CommentModel(
                                    id = doc.id,
                                    postId = doc.getString("postId") ?: "",
                                    userId = userId,
                                    username = userInfo.first,
                                    userProfileUrl = userInfo.second,
                                    text = doc.getString("text") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: 0
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Yorum verisi dönüştürülürken hata: ${e.message}", e)
                                null
                            }
                        }
                        
                        // Yorumları zaman damgasına göre manuel olarak sırala (en yeniden en eskiye)
                        val sortedComments = commentsList.sortedByDescending { it.timestamp }
                        
                        if (sortedComments.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.visibility = View.GONE
                        }
                        
                        adapter.updateComments(sortedComments)
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Log.e(TAG, "Kullanıcı bilgileri yüklenirken hata: ${e.message}", e)
                        Toast.makeText(context, "Yorumlar yüklenemedi", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Yorumlar yüklenirken hata: ${e.message}", e)
                Toast.makeText(context, "Yorumlar yüklenemedi", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun addCommentToPhoto(photoId: String, commentText: String, editText: EditText, adapter: CommentAdapter) {
        val currentUser = auth.currentUser ?: return
        
        // Önce kullanıcı bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { userDocuments ->
                if (userDocuments.isEmpty) {
                    Toast.makeText(context, "Kullanıcı bilgileri alınamadı", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                
                val userDoc = userDocuments.documents.first()
                val username = userDoc.getString("username") ?: ""
                val profilePhotoUrl = userDoc.getString("profilePhotoUrl") ?: ""
                
                val commentData = hashMapOf(
                    "postId" to photoId,
                    "userId" to currentUser.uid,
                    "text" to commentText.trim(),
                    "timestamp" to System.currentTimeMillis()
                )
                
                firestore.collection("post_comments")
                    .add(commentData)
                    .addOnSuccessListener { documentReference ->
                        // Yorum sayısını güncelle
                        updatePostCommentCount(photoId)
                        
                        // UI'ı güncelle
                        editText.text.clear()
                        
                        // Yorumlar listesine yeni yorumu ekle
                        val newComment = CommentModel(
                            id = documentReference.id,
                            postId = photoId,
                            userId = currentUser.uid,
                            username = username,
                            userProfileUrl = profilePhotoUrl,
                            text = commentText.trim(),
                            timestamp = System.currentTimeMillis()
                        )
                        
                        // Mevcut yorumları al ve en başa yeni yorumu ekle
                        val currentComments = adapter.getComments()
                        val updatedComments = listOf(newComment) + currentComments
                        adapter.updateComments(updatedComments)
                        
                        // Fotoğraf sahibini bul ve bildirim gönder
                        firestore.collection("user_photos").document(photoId)
                            .get()
                            .addOnSuccessListener { photoDoc ->
                                val photoOwnerId = photoDoc.getString("userId")
                                if (photoOwnerId != null && photoOwnerId != currentUser.uid) {
                                    // Kendi gönderimize yorum yaparsak bildirim gönderme
                                    sendCommentNotification(photoOwnerId, currentUser.uid, photoId, commentText.trim())
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Yorum eklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    
    private fun updatePostCommentCount(postId: String) {
        firestore.collection("user_photos").document(postId)
            .get()
            .addOnSuccessListener { document ->
                val currentComments = document.getLong("commentsCount")?.toInt() ?: 0
                val newComments = currentComments + 1
                
                firestore.collection("user_photos").document(postId)
                    .update("commentsCount", newComments)
            }
    }
    
    private fun sendCommentNotification(receiverId: String, senderId: String, postId: String, commentText: String) {
        // Yorum yapan kullanıcının bilgilerini al
        firestore.collection("users")
            .whereEqualTo("userId", senderId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    return@addOnSuccessListener
                }
                
                val senderDoc = documents.documents.first()
                val senderUsername = senderDoc.getString("username") ?: "Bir kullanıcı"
                
                // Yorum metni çok uzunsa kısalt
                val shortenedComment = if (commentText.length > 30) 
                    "${commentText.substring(0, 30)}..." 
                else 
                    commentText
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "comment",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername fotoğrafınıza yorum yaptı: \"$shortenedComment\"",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "relatedItemId" to postId
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Yorum bildirimi eklenirken hata: ${e.message}", e)
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
            loadPostLikes(postId) { users, error ->
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
    
    /**
     * Bir gönderiye beğeni yapan kullanıcıları yükler
     */
    private fun loadPostLikes(postId: String, callback: (List<LikeUserModel>, String?) -> Unit) {
        firestore.collection("post_likes")
            .whereEqualTo("postId", postId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    callback(emptyList(), null)
                    return@addOnSuccessListener
                }
                
                // Kullanıcı ID'lerini topla
                val userIds = documents.documents.mapNotNull { it.getString("userId") }.distinct()
                
                if (userIds.isEmpty()) {
                    callback(emptyList(), null)
                    return@addOnSuccessListener
                }
                
                // Kullanıcı bilgilerini yükle
                firestore.collection("users")
                    .whereIn("userId", userIds)
                    .get()
                    .addOnSuccessListener { userDocuments ->
                        // Kullanıcı bilgilerini eşleştir
                        val usersMap = userDocuments.documents.associateBy(
                            { it.getString("userId") ?: "" },
                            { doc ->
                                LikeUserModel(
                                    userId = doc.getString("userId") ?: "",
                                    username = doc.getString("username") ?: "",
                                    profilePhotoUrl = doc.getString("profilePhotoUrl") ?: ""
                                )
                            }
                        )
                        
                        // Tüm beğenenleri kullanıcı bilgileriyle birleştir
                        val likeUsers = userIds.mapNotNull { userId -> usersMap[userId] }
                        
                        callback(likeUsers, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Kullanıcı bilgilerini yüklerken hata: ${e.message}", e)
                        callback(emptyList(), e.message)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Beğeni bilgilerini yüklerken hata: ${e.message}", e)
                callback(emptyList(), e.message)
            }
    }

    // Takip et butonunu ayarla
    private fun setupFollowButton(profileUserId: String) {
        try {
            // Takip et butonunu göster
            binding.buttonFollow.visibility = View.VISIBLE
            binding.buttonChangePhoto.visibility = View.GONE
            binding.buttonEditProfileTop.visibility = View.GONE
            binding.buttonEditBio.visibility = View.GONE
            
            // Takip et butonu için tıklama olayı
            binding.buttonFollow.setOnClickListener {
                val isFollowing = binding.buttonFollow.text.toString() == "Takibi Bırak"
                toggleFollow(profileUserId, isFollowing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Takip butonunu ayarlarken hata: ${e.message}", e)
        }
    }
    
    // Takip et butonunu gizle (kendi profilimiz için)
    private fun hideFollowButton() {
        binding.buttonFollow.visibility = View.GONE
        binding.buttonChangePhoto.visibility = View.VISIBLE
        binding.buttonEditProfileTop.visibility = View.VISIBLE
        binding.buttonEditBio.visibility = View.VISIBLE
    }
    
    // Takip durumunu kontrol et
    private fun checkFollowStatus(profileUserId: String) {
        val currentUser = auth.currentUser ?: return
        
        firestore.collection("follows")
            .document("${currentUser.uid}_${profileUserId}")
            .get()
            .addOnSuccessListener { document ->
                val isFollowing = document.exists()
                
                // UI'ı güncelle
                updateFollowButtonUI(isFollowing)
                
                // Takip edilmeyen profillerde fotoğrafları gizle
                if (!isFollowing) {
                    binding.recyclerViewPhotos.visibility = View.GONE
                    binding.textNoPhotos.visibility = View.GONE // Henüz fotoğraf paylaşmadı mesajını gizle
                    binding.textFollowToSee.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewPhotos.visibility = View.VISIBLE
                    binding.textFollowToSee.visibility = View.GONE
                    // "Henüz fotoğraf paylaşmadı" mesajının görünürlüğü loadUserPhotos içinde kontrol edilecek
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Takip durumu kontrol edilirken hata: ${e.message}", e)
                // Hata durumunda da varsayılan olarak takip edilmiyor olarak düşün
                updateFollowButtonUI(false)
                binding.recyclerViewPhotos.visibility = View.GONE
                binding.textNoPhotos.visibility = View.GONE
                binding.textFollowToSee.visibility = View.VISIBLE
            }
    }
    
    // Takip et butonunun görünümünü güncelle
    private fun updateFollowButtonUI(isFollowing: Boolean) {
        if (isFollowing) {
            binding.buttonFollow.text = "Takibi Bırak"
            binding.buttonFollow.setBackgroundResource(R.drawable.button_outline_background)
        } else {
            binding.buttonFollow.text = "Takip Et"
            binding.buttonFollow.setBackgroundResource(R.drawable.button_background)
        }
    }
    
    // Takip et/bırak işlemi
    private fun toggleFollow(profileUserId: String, isCurrentlyFollowing: Boolean) {
        val currentUser = auth.currentUser ?: return
        val followId = "${currentUser.uid}_${profileUserId}"
        
        if (!isCurrentlyFollowing) {
            // Takip et
            val followData = hashMapOf(
                "followerId" to currentUser.uid,
                "followingId" to profileUserId,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("follows")
                .document(followId)
                .set(followData)
                .addOnSuccessListener {
                    // UI'ı güncelle
                    updateFollowButtonUI(true)
                    updateFollowCounts(profileUserId, currentUser.uid, 1)
                    
                    // Fotoğrafları yükle
                    loadPhotosFromFirestore(profileUserId)
                    
                    // Bildirim gönder
                    sendFollowNotification(profileUserId, currentUser.uid)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Takip edilirken hata: ${e.message}", e)
                    Toast.makeText(context, "Takip edilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Takibi bırak
            firestore.collection("follows")
                .document(followId)
                .delete()
                .addOnSuccessListener {
                    // UI'ı güncelle
                    updateFollowButtonUI(false)
                    updateFollowCounts(profileUserId, currentUser.uid, -1)
                    
                    // Fotoğrafları gizle
                    binding.recyclerViewPhotos.visibility = View.GONE
                    binding.textNoPhotos.visibility = View.GONE
                    binding.textFollowToSee.visibility = View.VISIBLE
                    
                    // Bildirim gönder
                    sendUnfollowNotification(profileUserId, currentUser.uid)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Takip bırakılırken hata: ${e.message}", e)
                    Toast.makeText(context, "Takipten çıkılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    // Gerçek takipçi sayılarını hesapla ve güncelle
    private fun calculateAndUpdateFollowCounts(userId: String) {
        try {
            Log.d(TAG, "Gerçek takipçi sayıları hesaplanıyor: $userId")
            
            // Takipçi sayısını hesapla (bu kullanıcıyı takip eden kişiler)
            firestore.collection("follows")
                .whereEqualTo("followingId", userId)
                .get()
                .addOnSuccessListener { followerDocuments ->
                    val realFollowersCount = followerDocuments.size()
                    Log.d(TAG, "Gerçek takipçi sayısı: $realFollowersCount")
                    
                    // UI'ı güncelle
                    binding.textFollowersCount.text = realFollowersCount.toString()
                    
                    // Firebase'deki değeri de güncelle
                    firestore.collection("users").document(userId)
                        .update("followers", realFollowersCount)
                        .addOnSuccessListener {
                            Log.d(TAG, "Takipçi sayısı Firebase'de güncellendi: $realFollowersCount")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Takipçi sayısı güncellenirken hata: ${e.message}", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Takipçi sayısı hesaplanırken hata: ${e.message}", e)
                    // Hata durumunda Firebase'deki mevcut değeri göster
                    firestore.collection("users").document(userId)
                        .get()
                        .addOnSuccessListener { document ->
                            val fallbackFollowers = document.getLong("followers") ?: 0
                            binding.textFollowersCount.text = fallbackFollowers.toString()
                        }
                }
            
            // Takip ettiği kişi sayısını hesapla (bu kullanıcının takip ettiği kişiler)
            firestore.collection("follows")
                .whereEqualTo("followerId", userId)
                .get()
                .addOnSuccessListener { followingDocuments ->
                    val realFollowingCount = followingDocuments.size()
                    Log.d(TAG, "Gerçek takip sayısı: $realFollowingCount")
                    
                    // UI'ı güncelle
                    binding.textFollowingCount.text = realFollowingCount.toString()
                    
                    // Firebase'deki değeri de güncelle
                    firestore.collection("users").document(userId)
                        .update("following", realFollowingCount)
                        .addOnSuccessListener {
                            Log.d(TAG, "Takip sayısı Firebase'de güncellendi: $realFollowingCount")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Takip sayısı güncellenirken hata: ${e.message}", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Takip sayısı hesaplanırken hata: ${e.message}", e)
                    // Hata durumunda Firebase'deki mevcut değeri göster
                    firestore.collection("users").document(userId)
                        .get()
                        .addOnSuccessListener { document ->
                            val fallbackFollowing = document.getLong("following") ?: 0
                            binding.textFollowingCount.text = fallbackFollowing.toString()
                        }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Takipçi sayıları hesaplanırken beklenmeyen hata: ${e.message}", e)
        }
    }

    // Takipçi/takip sayısını güncelle
    private fun updateFollowCounts(followedUserId: String, followerUserId: String, increment: Int) {
        // Takip/takip bırakma işleminden sonra gerçek sayıları hesapla ve güncelle
        
        // Takip edilen kullanıcının takipçi sayısını yeniden hesapla
        firestore.collection("follows")
            .whereEqualTo("followingId", followedUserId)
            .get()
            .addOnSuccessListener { followerDocuments ->
                val realFollowersCount = followerDocuments.size()
                
                // Firebase'deki değeri güncelle
                firestore.collection("users").document(followedUserId)
                    .update("followers", realFollowersCount)
                    .addOnSuccessListener {
                        // Sadece UI görüntülenen kullanıcı için güncelle
                        val currentViewedUserId = arguments?.getString("userId") ?: auth.currentUser?.uid
                        if (currentViewedUserId == followedUserId) {
                            binding.textFollowersCount.text = realFollowersCount.toString()
                        }
                    }
            }
        
        // Takip eden kullanıcının takip ettiği sayısını yeniden hesapla  
        firestore.collection("follows")
            .whereEqualTo("followerId", followerUserId)
            .get()
            .addOnSuccessListener { followingDocuments ->
                val realFollowingCount = followingDocuments.size()
                
                // Firebase'deki değeri güncelle
                firestore.collection("users").document(followerUserId)
                    .update("following", realFollowingCount)
                    .addOnSuccessListener {
                        // Eğer takip eden kişi şu anda görüntülenen kullanıcıysa UI'ı güncelle
                        val currentViewedUserId = arguments?.getString("userId") ?: auth.currentUser?.uid
                        if (currentViewedUserId == followerUserId) {
                            binding.textFollowingCount.text = realFollowingCount.toString()
                        }
                    }
            }
    }
    
    // Takip bildirimi gönder
    private fun sendFollowNotification(receiverId: String, senderId: String) {
        // Takip eden kullanıcının bilgilerini al
        firestore.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val senderUsername = document.getString("username") ?: "Bir kullanıcı"
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "follow",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername sizi takip etmeye başladı!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
    }
    
    // Takipten çıkma bildirimi gönder
    private fun sendUnfollowNotification(receiverId: String, senderId: String) {
        // Takipten çıkan kullanıcının bilgilerini al
        firestore.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val senderUsername = document.getString("username") ?: "Bir kullanıcı"
                
                // Bildirim verisi oluştur
                val notificationData = hashMapOf(
                    "type" to "unfollow",
                    "receiverId" to receiverId,
                    "senderId" to senderId,
                    "senderUsername" to senderUsername,
                    "message" to "$senderUsername sizi takip etmeyi bıraktı!",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                
                // Bildirimi Firestore'a ekle
                firestore.collection("notifications")
                    .add(notificationData)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takipten çıkma bildirimi eklenirken hata: ${e.message}", e)
                    }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun showFollowersDialog(userId: String, type: String) {
        try {
            val currentUser = auth.currentUser ?: return
            
            // Check if user can view this information
            val isOwnProfile = userId == currentUser.uid
            
            if (!isOwnProfile) {
                // Check if current user follows the profile user
                firestore.collection("follows")
                    .document("${currentUser.uid}_${userId}")
                    .get()
                    .addOnSuccessListener { document ->
                        val isFollowing = document.exists()
                        
                        if (isFollowing) {
                            // User can view the list
                            showFollowListDialog(userId, type)
                        } else {
                            // Show permission denied dialog
                            showPermissionDeniedDialog(type)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Takip durumu kontrol edilirken hata: ${e.message}", e)
                        showPermissionDeniedDialog(type)
                    }
            } else {
                // Own profile, can view everything
                showFollowListDialog(userId, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Takipçiler dialog'u gösterilirken hata: ${e.message}", e)
        }
    }
    
    private fun showFollowListDialog(userId: String, type: String) {
        try {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_followers_following)
            
            val window = dialog.window
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Setup dialog views
            val textTitle = dialog.findViewById<TextView>(R.id.text_dialog_title)
            val buttonClose = dialog.findViewById<ImageButton>(R.id.button_close_dialog)
            val recyclerView = dialog.findViewById<RecyclerView>(R.id.recycler_view_users)
            val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar)
            val textEmpty = dialog.findViewById<TextView>(R.id.text_empty_state)
            
            // Set title
            textTitle.text = if (type == "followers") "Takipçiler" else "Takip Edilenler"
            
            // Setup close button
            buttonClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Setup RecyclerView
            val followAdapter = FollowUserAdapter(emptyList()) { clickedUserId ->
                dialog.dismiss()
                navigateToUserProfile(clickedUserId)
            }
            
            recyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = followAdapter
            }
            
            // Load data
            progressBar.visibility = View.VISIBLE
            textEmpty.visibility = View.GONE
            
            loadFollowData(userId, type, followAdapter, progressBar, textEmpty)
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Follow list dialog'u gösterilirken hata: ${e.message}", e)
        }
    }
    
    private fun showPermissionDeniedDialog(type: String) {
        try {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_followers_following)
            
            val window = dialog.window
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Setup dialog views
            val textTitle = dialog.findViewById<TextView>(R.id.text_dialog_title)
            val buttonClose = dialog.findViewById<ImageButton>(R.id.button_close_dialog)
            val recyclerView = dialog.findViewById<RecyclerView>(R.id.recycler_view_users)
            val progressBar = dialog.findViewById<ProgressBar>(R.id.progress_bar)
            val textEmpty = dialog.findViewById<TextView>(R.id.text_empty_state)
            val layoutPermissionDenied = dialog.findViewById<View>(R.id.layout_permission_denied)
            
            // Set title
            textTitle.text = if (type == "followers") "Takipçiler" else "Takip Edilenler"
            
            // Setup close button
            buttonClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Hide other views and show permission denied
            recyclerView.visibility = View.GONE
            progressBar.visibility = View.GONE
            textEmpty.visibility = View.GONE
            layoutPermissionDenied.visibility = View.VISIBLE
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Permission denied dialog'u gösterilirken hata: ${e.message}", e)
        }
    }
    
    private fun loadFollowData(userId: String, type: String, adapter: FollowUserAdapter, progressBar: ProgressBar, textEmpty: TextView) {
        try {
            val collection = if (type == "followers") "follows" else "follows"
            val fieldToQuery = if (type == "followers") "followingId" else "followerId"
            val fieldToGet = if (type == "followers") "followerId" else "followingId"
            
            firestore.collection(collection)
                .whereEqualTo(fieldToQuery, userId)
                .get()
                .addOnSuccessListener { documents ->
                    progressBar.visibility = View.GONE
                    
                    if (documents.isEmpty) {
                        textEmpty.visibility = View.VISIBLE
                        textEmpty.text = if (type == "followers") "Henüz kimse takip etmiyor" else "Henüz kimseyi takip etmiyor"
                        adapter.updateUsers(emptyList())
                    } else {
                        textEmpty.visibility = View.GONE
                        
                        // Get user IDs
                        val userIds = documents.mapNotNull { it.getString(fieldToGet) }
                        
                        // Load user details
                        loadUserDetails(userIds, adapter)
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    textEmpty.visibility = View.VISIBLE
                    textEmpty.text = "Veri yüklenirken hata oluştu"
                    Log.e(TAG, "Follow data yüklenirken hata: ${e.message}", e)
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            textEmpty.visibility = View.VISIBLE
            textEmpty.text = "Beklenmeyen hata oluştu"
            Log.e(TAG, "loadFollowData'da hata: ${e.message}", e)
        }
    }
    
    private fun loadUserDetails(userIds: List<String>, adapter: FollowUserAdapter) {
        try {
            if (userIds.isEmpty()) {
                adapter.updateUsers(emptyList())
                return
            }
            
            val users = mutableListOf<FollowUser>()
            var loadedCount = 0
            
            for (userId in userIds) {
                firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        loadedCount++
                        
                        if (document.exists()) {
                            val user = FollowUser(
                                userId = userId,
                                username = document.getString("username") ?: "Kullanıcı",
                                bio = document.getString("bio") ?: "",
                                profilePhotoUrl = document.getString("profilePhotoUrl") ?: ""
                            )
                            users.add(user)
                        }
                        
                        // All users loaded
                        if (loadedCount == userIds.size) {
                            adapter.updateUsers(users.sortedBy { it.username })
                        }
                    }
                    .addOnFailureListener { e ->
                        loadedCount++
                        Log.e(TAG, "User detail yüklenirken hata: ${e.message}", e)
                        
                        // All users processed (with some failures)
                        if (loadedCount == userIds.size) {
                            adapter.updateUsers(users.sortedBy { it.username })
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadUserDetails'de hata: ${e.message}", e)
            adapter.updateUsers(emptyList())
        }
    }
} 