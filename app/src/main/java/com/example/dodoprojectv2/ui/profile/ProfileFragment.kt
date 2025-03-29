package com.example.dodoprojectv2.ui.profile

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.dodoprojectv2.MainActivity
import com.example.dodoprojectv2.R
import com.example.dodoprojectv2.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage

class ProfileFragment : Fragment() {

    private lateinit var profileViewModel: ProfileViewModel
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var selectedImageUri: Uri? = null
    private lateinit var photoAdapter: PhotoAdapter
    
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
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
        
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root
        
        try {
            // Initialize Firebase components
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
            
            // Önce varsayılan görünümü hazırla
            setDummyData()
            
            setupUIElements()
            setupRecyclerView()
            loadUserProfile()
            loadUserPhotos()
        } catch (e: Exception) {
            Log.e(TAG, "Fragment oluşturulurken hata oluştu: ${e.message}", e)
            Toast.makeText(context, "Bir hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        return root
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
            
            // Glide ile varsayılan resmi yükle
            Glide.with(this)
                .load(R.drawable.default_profile)
                .into(binding.imageProfile)
        } catch (e: Exception) {
            Log.e(TAG, "UI elemanları yapılandırılırken hata: ${e.message}", e)
        }
    }
    
    private fun setupRecyclerView() {
        try {
            // RecyclerView düzenini ayarla
            val layoutManager = GridLayoutManager(context, 3)
            binding.recyclerViewPhotos.layoutManager = layoutManager
            
            // Adaptörü oluştur ve bağla
            photoAdapter = PhotoAdapter(emptyList()) { photo ->
                // Fotoğrafa tıklandığında yapılacak işlemler
                Toast.makeText(context, "Fotoğraf: ${photo.taskName}", Toast.LENGTH_SHORT).show()
                // Burada fotoğrafı tam ekran görüntüleme veya başka bir işlem yapılabilir
            }
            
            binding.recyclerViewPhotos.adapter = photoAdapter
        } catch (e: Exception) {
            Log.e(TAG, "RecyclerView kurulurken hata: ${e.message}", e)
        }
    }
    
    private fun loadUserProfile() {
        try {
            Log.d(TAG, "Kullanıcı profili yükleniyor")
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                Log.d(TAG, "Giriş yapmış kullanıcı bulundu: ${currentUser.email}")
                val userId = currentUser.uid
                
                // Get user data from Firestore
                firestore.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        try {
                            Log.d(TAG, "Firestore verisi başarıyla alındı")
                            if (document != null && document.exists()) {
                                Log.d(TAG, "Firestore dokümanı mevcut")
                                // Username
                                val username = document.getString("username")
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
                                
                                val followers = document.getLong("followers") ?: 0
                                Log.d(TAG, "Takipçi: $followers")
                                binding.textFollowersCount.text = followers.toString()
                                
                                val following = document.getLong("following") ?: 0
                                Log.d(TAG, "Takip: $following")
                                binding.textFollowingCount.text = following.toString()
                                
                                // Profile photo
                                val profilePhotoUrl = document.getString("profilePhotoUrl")
                                Log.d(TAG, "Profil foto URL: $profilePhotoUrl")
                                if (profilePhotoUrl != null && profilePhotoUrl.isNotEmpty()) {
                                    Glide.with(this)
                                        .load(profilePhotoUrl)
                                        .placeholder(R.drawable.default_profile)
                                        .into(binding.imageProfile)
                                }
                            } else {
                                Log.d(TAG, "Firestore dokümanı bulunamadı veya boş")
                                Toast.makeText(context, "Kullanıcı verileri bulunamadı", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Firestore veri işlemede hata: ${e.message}", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore verisi alınamadı: ${e.message}", e)
                        Toast.makeText(context, "Profil bilgileri yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Log.d(TAG, "Giriş yapmış kullanıcı bulunamadı")
                Toast.makeText(context, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı profili yüklenirken hata: ${e.message}", e)
        }
    }
    
    private fun loadUserPhotos() {
        try {
            val currentUser = auth.currentUser ?: return
            
            binding.progressPhotos.visibility = View.VISIBLE
            binding.textNoPhotos.visibility = View.GONE
            
            firestore.collection("user_photos")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("isPublic", true)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20) // Son 20 fotoğrafı getir
                .get()
                .addOnSuccessListener { documents ->
                    binding.progressPhotos.visibility = View.GONE
                    
                    if (documents.isEmpty) {
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
                    
                    photoAdapter.updatePhotos(photos)
                }
                .addOnFailureListener { e ->
                    binding.progressPhotos.visibility = View.GONE
                    binding.textNoPhotos.visibility = View.VISIBLE
                    Log.e(TAG, "Fotoğraflar yüklenirken hata: ${e.message}", e)
                }
        } catch (e: Exception) {
            binding.progressPhotos.visibility = View.GONE
            binding.textNoPhotos.visibility = View.VISIBLE
            Log.e(TAG, "Fotoğraflar yüklenirken hata: ${e.message}", e)
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
            val options = arrayOf("Profil Düzenle", "Çıkış Yap")
            
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("Profil Ayarları")
            builder.setItems(options) { dialog, which ->
                when (which) {
                    0 -> showEditProfileDialog() // Profil Düzenle
                    1 -> {
                        // Çıkış Yap
                        try {
                            val mainActivity = activity as MainActivity
                            mainActivity.signOut()
                        } catch (e: Exception) {
                            Log.e(TAG, "Çıkış yapılırken hata: ${e.message}", e)
                            Toast.makeText(context, "Çıkış yapılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            builder.show()
        } catch (e: Exception) {
            Log.e(TAG, "Ayarlar dialogu gösterilirken hata: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 