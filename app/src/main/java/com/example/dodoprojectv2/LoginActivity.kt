package com.example.dodoprojectv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Kaydedilmiş tema tercihini uygula
        ThemeManager.applySavedTheme(this)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance()
        
        // Initialize UI elements
        emailEditText = findViewById(R.id.edit_text_email)
        passwordEditText = findViewById(R.id.edit_text_password)
        loginButton = findViewById(R.id.button_login)
        registerLink = findViewById(R.id.text_view_register)
        progressBar = findViewById(R.id.progress_bar)
        
        // Set up login button click listener
        loginButton.setOnClickListener {
            loginUser()
        }
        
        // Set up register link click listener
        registerLink.setOnClickListener {
            // Navigate to RegisterActivity
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loginUser() {
        val identifier = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        // Validate inputs
        if (identifier.isEmpty()) {
            emailEditText.error = "E-posta, kullanıcı adı veya telefon numarası gerekli"
            emailEditText.requestFocus()
            return
        }
        
        if (password.isEmpty()) {
            passwordEditText.error = "Şifre gerekli"
            passwordEditText.requestFocus()
            return
        }
        
        // Show progress bar
        progressBar.visibility = View.VISIBLE
        
        // Log giriş denemesi
        Log.d("LoginActivity", "Giriş denemesi: $identifier")
        
        // First try to login with email
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            Log.d("LoginActivity", "Email ile giriş yapılıyor")
            loginWithEmail(identifier, password)
        } else {
            Log.d("LoginActivity", "Kullanıcı adı/telefon kontrolü yapılıyor")
            // If not email, check if it's a username or phone number
            checkUserIdentifier(identifier, password)
        }
    }
    
    private fun loginWithEmail(email: String, password: String) {
        Log.d("LoginActivity", "Email ile giriş: $email")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                
                if (task.isSuccessful) {
                    // Sign in success
                    Log.d("LoginActivity", "Giriş başarılı")
                    
                    // Firebase'den kullanıcının tema tercihini yükle
                    ThemeManager.loadThemeFromFirebase(this@LoginActivity) { isDarkMode ->
                        // Tema tercihini uygula
                        ThemeManager.applyTheme(this@LoginActivity, isDarkMode)
                        
                        // Ana sayfaya yönlendir
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    // If sign in fails, display a message to the user
                    Log.e("LoginActivity", "Giriş başarısız", task.exception)
                    Toast.makeText(baseContext, "Giriş başarısız: " + task.exception?.message,
                        Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun checkUserIdentifier(identifier: String, password: String) {
        Log.d("LoginActivity", "Kullanıcı adı kontrolü: $identifier")
        // Query Firestore to find user by username or phone
        db.collection("users")
            .whereEqualTo("username", identifier)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Found user by username
                    val userDoc = documents.documents[0]
                    val email = userDoc.getString("email")
                    Log.d("LoginActivity", "Kullanıcı adıyla bulundu, email: $email")
                    
                    if (email != null) {
                        loginWithEmail(email, password)
                    } else {
                        progressBar.visibility = View.GONE
                        Log.e("LoginActivity", "Email bilgisi bulunamadı")
                        Toast.makeText(this, "Kullanıcı e-posta bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("LoginActivity", "Kullanıcı adıyla bulunamadı, telefon kontrolü yapılıyor")
                    // Try phone number
                    checkPhoneNumber(identifier, password)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("LoginActivity", "Kullanıcı adı kontrolünde hata", e)
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun checkPhoneNumber(phone: String, password: String) {
        Log.d("LoginActivity", "Telefon kontrolü: $phone")
        // Query Firestore to find user by phone
        db.collection("users")
            .whereEqualTo("phone", phone)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                
                if (!documents.isEmpty) {
                    // Found user by phone
                    val userDoc = documents.documents[0]
                    val email = userDoc.getString("email")
                    Log.d("LoginActivity", "Telefon numarasıyla bulundu, email: $email")
                    
                    if (email != null) {
                        loginWithEmail(email, password)
                    } else {
                        Log.e("LoginActivity", "Email bilgisi bulunamadı")
                        Toast.makeText(this, "Kullanıcı e-posta bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // User not found
                    Log.e("LoginActivity", "Kullanıcı bulunamadı")
                    Toast.makeText(this, "Kullanıcı bulunamadı. Lütfen e-posta, kullanıcı adı veya telefon numaranızı kontrol edin.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("LoginActivity", "Telefon kontrolünde hata", e)
                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    // Check if user is already signed in when activity starts
    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
} 