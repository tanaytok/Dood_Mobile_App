package com.example.dodoprojectv2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dodoprojectv2.utils.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var editTextUsername: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPhone: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var buttonRegister: Button
    private lateinit var textViewLogin: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Kaydedilmiş tema tercihini uygula
        ThemeManager.applySavedTheme(this)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        // Initialize views
        editTextUsername = findViewById(R.id.edit_text_username)
        editTextEmail = findViewById(R.id.edit_text_email)
        editTextPhone = findViewById(R.id.edit_text_phone)
        editTextPassword = findViewById(R.id.edit_text_password)
        editTextConfirmPassword = findViewById(R.id.edit_text_confirm_password)
        buttonRegister = findViewById(R.id.button_register)
        textViewLogin = findViewById(R.id.text_view_login)
        progressBar = findViewById(R.id.progress_bar)
        
        // Set up register button
        buttonRegister.setOnClickListener {
            registerUser()
        }
        
        // Navigate to login screen
        textViewLogin.setOnClickListener {
            finish() // Go back to login activity
        }
    }
    
    private fun registerUser() {
        val username = editTextUsername.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val phone = editTextPhone.text.toString().trim()
        val password = editTextPassword.text.toString().trim()
        val confirmPassword = editTextConfirmPassword.text.toString().trim()
        
        android.util.Log.d("RegisterActivity", "Kayıt denemesi: $username / $email")
        
        // Validate input
        if (username.isEmpty()) {
            editTextUsername.error = "Kullanıcı adı gerekli"
            editTextUsername.requestFocus()
            return
        }
        
        if (email.isEmpty()) {
            editTextEmail.error = "E-posta gerekli"
            editTextEmail.requestFocus()
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Geçerli bir e-posta giriniz"
            editTextEmail.requestFocus()
            return
        }
        
        if (phone.isEmpty()) {
            editTextPhone.error = "Telefon numarası gerekli"
            editTextPhone.requestFocus()
            return
        }
        
        if (password.isEmpty()) {
            editTextPassword.error = "Şifre gerekli"
            editTextPassword.requestFocus()
            return
        }
        
        if (password.length < 6) {
            editTextPassword.error = "Şifre en az 6 karakter olmalıdır"
            editTextPassword.requestFocus()
            return
        }
        
        if (confirmPassword.isEmpty() || confirmPassword != password) {
            editTextConfirmPassword.error = "Şifreler eşleşmiyor"
            editTextConfirmPassword.requestFocus()
            return
        }
        
        // Check if username already exists
        checkUsernameAvailability(username, email, phone, password)
    }
    
    private fun checkUsernameAvailability(username: String, email: String, phone: String, password: String) {
        progressBar.visibility = View.VISIBLE
        android.util.Log.d("RegisterActivity", "Kullanıcı adı kontrolü: $username")
        
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Username available, check if phone is available
                    android.util.Log.d("RegisterActivity", "Kullanıcı adı müsait")
                    checkPhoneAvailability(username, email, phone, password)
                } else {
                    progressBar.visibility = View.GONE
                    android.util.Log.e("RegisterActivity", "Kullanıcı adı zaten kullanılıyor")
                    editTextUsername.error = "Bu kullanıcı adı zaten kullanılıyor"
                    editTextUsername.requestFocus()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                android.util.Log.e("RegisterActivity", "Kullanıcı adı kontrolünde hata", e)
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun checkPhoneAvailability(username: String, email: String, phone: String, password: String) {
        android.util.Log.d("RegisterActivity", "Telefon kontrolü: $phone")
        db.collection("users")
            .whereEqualTo("phone", phone)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Phone available, proceed with registration
                    android.util.Log.d("RegisterActivity", "Telefon müsait")
                    createUserWithEmail(username, email, phone, password)
                } else {
                    progressBar.visibility = View.GONE
                    android.util.Log.e("RegisterActivity", "Telefon zaten kullanılıyor")
                    editTextPhone.error = "Bu telefon numarası zaten kullanılıyor"
                    editTextPhone.requestFocus()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                android.util.Log.e("RegisterActivity", "Telefon kontrolünde hata", e)
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createUserWithEmail(username: String, email: String, phone: String, password: String) {
        android.util.Log.d("RegisterActivity", "Kullanıcı oluşturuluyor: $email")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Registration successful
                    android.util.Log.d("RegisterActivity", "Firebase Auth kaydı başarılı")
                    val user = auth.currentUser
                    
                    // Store additional user information in Firestore
                    user?.let {
                        val userId = it.uid
                        android.util.Log.d("RegisterActivity", "Firestore'a kullanıcı bilgileri ekleniyor, userId: $userId")
                        val userMap = hashMapOf(
                            "userId" to userId,
                            "username" to username,
                            "email" to email,
                            "phone" to phone,
                            "bio" to "", // Empty bio initially
                            "profilePhotoUrl" to "", // No profile photo initially
                            "streak" to 0, // Initial streak count
                            "points" to 0, // Initial points
                            "followers" to 0, // Initial followers count
                            "following" to 0, // Initial following count
                            "themePreference" to false, // Varsayılan light theme
                            "createdAt" to System.currentTimeMillis()
                        )
                        
                        // Add user to Firestore
                        db.collection("users").document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                progressBar.visibility = View.GONE
                                android.util.Log.d("RegisterActivity", "Firestore kullanıcı kaydı başarılı")
                                Toast.makeText(baseContext, "Kayıt başarılı!", Toast.LENGTH_SHORT).show()
                                
                                // Yeni kullanıcı için varsayılan tema ayarla ve MainActivity'ye git
                                ThemeManager.loadThemeFromFirebase(this@RegisterActivity) { isDarkMode ->
                                    // Tema tercihini uygula
                                    ThemeManager.applyTheme(this@RegisterActivity, isDarkMode)
                                    
                                    // Navigate to MainActivity
                                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                android.util.Log.e("RegisterActivity", "Firestore kullanıcı kaydında hata", e)
                                Toast.makeText(baseContext, "Kullanıcı profili oluşturulamadı: ${e.message}", 
                                    Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    // Registration failed
                    progressBar.visibility = View.GONE
                    android.util.Log.e("RegisterActivity", "Firebase Auth kaydı başarısız", task.exception)
                    Toast.makeText(baseContext, "Kayıt başarısız: ${task.exception?.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
    }
} 