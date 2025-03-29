package com.example.dodoprojectv2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class PhoneLoginActivity : AppCompatActivity() {

    private lateinit var phoneEditText: EditText
    private lateinit var codeEditText: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var verifyCodeButton: Button
    private lateinit var backToLoginTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var storedVerificationId: String = ""
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private val TAG = "PhoneLoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_login)

        // Initialize Firebase services
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        phoneEditText = findViewById(R.id.editTextPhone)
        codeEditText = findViewById(R.id.editTextCode)
        sendCodeButton = findViewById(R.id.buttonSendCode)
        verifyCodeButton = findViewById(R.id.buttonVerifyCode)
        backToLoginTextView = findViewById(R.id.textViewBackToLogin)

        // Initially hide the verification code input
        codeEditText.visibility = View.GONE
        verifyCodeButton.visibility = View.GONE

        // Set up send code button click listener
        sendCodeButton.setOnClickListener {
            val phoneNumber = phoneEditText.text.toString().trim()
            if (validatePhoneNumber(phoneNumber)) {
                startPhoneNumberVerification(phoneNumber)
            }
        }

        // Set up verify code button click listener
        verifyCodeButton.setOnClickListener {
            val code = codeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                verifyPhoneNumberWithCode(storedVerificationId, code)
            } else {
                codeEditText.error = "Doğrulama kodu zorunludur"
            }
        }

        // Navigate back to login screen
        backToLoginTextView.setOnClickListener {
            finish() // Go back to login activity
        }
    }

    private fun validatePhoneNumber(phoneNumber: String): Boolean {
        if (phoneNumber.isEmpty()) {
            phoneEditText.error = "Telefon numarası zorunludur"
            return false
        }
        
        // Basic validation - you can enhance this
        if (!phoneNumber.startsWith("+")) {
            phoneEditText.error = "Telefon numarası + ile başlamalıdır (örn: +90...)"
            return false
        }
        
        return true
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        // Show progress indicator
        // progressBar.visibility = View.VISIBLE

        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            // Hide progress indicator
            // progressBar.visibility = View.GONE
            
            Log.e(TAG, "Phone verification error: ${e.message}", e)
            
            if (e.message?.contains("BILLING_NOT_ENABLED") == true) {
                // Show a dialog explaining the issue
                showBillingNotEnabledDialog(phoneNumber)
            } else {
                Toast.makeText(
                    baseContext,
                    "Telefon doğrulama başlatılamadı: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showBillingNotEnabledDialog(phoneNumber: String) {
        AlertDialog.Builder(this)
            .setTitle("Telefon Doğrulama Kullanılamıyor")
            .setMessage("Firebase Telefon Doğrulama hizmeti için ödeme yöntemi ayarlanmamış. " +
                    "Bu sürüm için telefon doğrulama yerine, telefon numaranız ve oluşturacağınız " +
                    "bir şifre ile kayıt olabilirsiniz.")
            .setPositiveButton("Kayıt Ol") { _, _ ->
                showAlternativePhoneLoginForm(phoneNumber)
            }
            .setNegativeButton("Geri Dön") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showAlternativePhoneLoginForm(phoneNumber: String) {
        // Hide previous UI
        findViewById<TextInputLayout>(R.id.textInputLayoutPhone).visibility = View.GONE
        findViewById<TextInputLayout>(R.id.textInputLayoutCode).visibility = View.GONE
        sendCodeButton.visibility = View.GONE
        verifyCodeButton.visibility = View.GONE
        
        // Show alternative flow UI
        findViewById<View>(R.id.alternativeLoginLayout).visibility = View.VISIBLE
        
        val altPhoneEditText = findViewById<EditText>(R.id.editTextPhoneAlt)
        val altPasswordEditText = findViewById<EditText>(R.id.editTextPasswordAlt)
        val loginAltButton = findViewById<Button>(R.id.buttonLoginAlt)
        
        // Pre-fill phone number
        altPhoneEditText.setText(phoneNumber)
        
        loginAltButton.setOnClickListener {
            val phone = altPhoneEditText.text.toString().trim()
            val password = altPasswordEditText.text.toString().trim()
            
            if (validateAlternativeInput(phone, password)) {
                loginWithPhoneAndPassword(phone, password)
            }
        }
    }

    private fun validateAlternativeInput(phone: String, password: String): Boolean {
        val altPhoneEditText = findViewById<EditText>(R.id.editTextPhoneAlt)
        val altPasswordEditText = findViewById<EditText>(R.id.editTextPasswordAlt)
        
        if (phone.isEmpty()) {
            altPhoneEditText.error = "Telefon numarası zorunludur"
            return false
        }
        if (!phone.startsWith("+")) {
            altPhoneEditText.error = "Telefon numarası + ile başlamalıdır (örn: +90...)"
            return false
        }
        if (password.isEmpty()) {
            altPasswordEditText.error = "Şifre zorunludur"
            return false
        }
        if (password.length < 6) {
            altPasswordEditText.error = "Şifre en az 6 karakter olmalıdır"
            return false
        }
        return true
    }

    private fun loginWithPhoneAndPassword(phone: String, password: String) {
        // Show progress
        // progressBar.visibility = View.VISIBLE
        
        // Query users collection by phone number
        db.collection("users")
            .whereEqualTo("phone", phone)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val userDoc = documents.documents[0]
                    val userId = userDoc.id
                    val email = userDoc.getString("email")
                    
                    if (email != null) {
                        // Log in with email and password
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                // Hide progress
                                // progressBar.visibility = View.GONE
                                
                                if (task.isSuccessful) {
                                    Toast.makeText(baseContext, "Giriş başarılı", Toast.LENGTH_SHORT).show()
                                    navigateToMainActivity()
                                } else {
                                    Toast.makeText(baseContext, "Telefon numarası veya şifre hatalı", 
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        // Hide progress
                        // progressBar.visibility = View.GONE
                        Toast.makeText(baseContext, "Bu telefon numarası ile kullanıcı bulunamadı", 
                            Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Hide progress
                    // progressBar.visibility = View.GONE
                    Toast.makeText(baseContext, "Bu telefon numarası ile kullanıcı bulunamadı", 
                        Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // Hide progress
                // progressBar.visibility = View.GONE
                Toast.makeText(baseContext, "Veritabanı hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // This callback will be invoked in two situations:
            // 1 - Instant verification. In some cases the phone number can be instantly
            //     verified without needing to send or enter a verification code.
            // 2 - Auto-retrieval. On some devices Google Play services can automatically
            //     detect the incoming verification SMS and perform verification without
            //     user action.
            
            // Hide progress indicator
            // progressBar.visibility = View.GONE
            
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Hide progress indicator
            // progressBar.visibility = View.GONE
            
            Log.e(TAG, "Verification failed: ${e.message}", e)
            
            if (e.message?.contains("BILLING_NOT_ENABLED") == true) {
                showBillingNotEnabledDialog(phoneEditText.text.toString().trim())
            } else {
                Toast.makeText(baseContext, "Doğrulama başarısız: ${e.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // The SMS verification code has been sent to the provided phone number,
            // we now need to ask the user to enter the code and then construct a credential
            // by combining the code with a verification ID.
            
            // Hide progress indicator
            // progressBar.visibility = View.GONE
            
            // Save verification ID and resending token so we can use them later
            storedVerificationId = verificationId
            resendToken = token
            
            // Show verification code input
            codeEditText.visibility = View.VISIBLE
            verifyCodeButton.visibility = View.VISIBLE
            
            Toast.makeText(baseContext, "Doğrulama kodu gönderildi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        // Show progress indicator
        // progressBar.visibility = View.VISIBLE
        
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                // Hide progress indicator
                // progressBar.visibility = View.GONE
                
                if (task.isSuccessful) {
                    // Sign in success
                    val user = task.result?.user
                    
                    // Check if this is a new user and store data in Firestore if needed
                    val userId = user?.uid
                    if (userId != null) {
                        db.collection("users").document(userId).get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    // This is a new user, create a document for them
                                    val userMap = hashMapOf(
                                        "userId" to userId,
                                        "phoneNumber" to user.phoneNumber,
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                    
                                    db.collection("users").document(userId)
                                        .set(userMap)
                                        .addOnSuccessListener {
                                            Toast.makeText(baseContext, "Giriş başarılı", 
                                                Toast.LENGTH_SHORT).show()
                                            navigateToMainActivity()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(baseContext, "Kullanıcı veri kaydı başarısız: ${e.message}", 
                                                Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    // Existing user
                                    Toast.makeText(baseContext, "Giriş başarılı", 
                                        Toast.LENGTH_SHORT).show()
                                    navigateToMainActivity()
                                }
                            }
                    }
                } else {
                    // Sign in failed
                    Toast.makeText(baseContext, "Doğrulama başarısız: ${task.exception?.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 