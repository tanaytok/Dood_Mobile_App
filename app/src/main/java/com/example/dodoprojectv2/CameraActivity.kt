package com.example.dodoprojectv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dodoprojectv2.databinding.ActivityCameraBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var taskId: String? = null
    private var taskTitle: String? = null
    private var taskTotalCount: Int = 1
    private var taskCurrentCount: Int = 0
    
    private val TAG = "CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Firebase bileşenlerini başlat
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // Intent ekstralarını al
        taskId = intent.getStringExtra("TASK_ID")
        taskTitle = intent.getStringExtra("TASK_TITLE")
        taskTotalCount = intent.getIntExtra("TASK_TOTAL_COUNT", 1)
        taskCurrentCount = intent.getIntExtra("TASK_CURRENT_COUNT", 0)
        
        // Başlığı ve açıklamayı ayarla
        binding.textTaskTitle.text = taskTitle
        binding.textTaskProgress.text = "$taskCurrentCount/$taskTotalCount"
        
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        binding.buttonCapturePhoto.setOnClickListener { takePhoto() }
        
        // İptal et butonunu ayarla
        binding.buttonCancel.setOnClickListener {
            finish()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture
        
        if (imageCapture == null) {
            Log.e(TAG, "imageCapture null, kamera başlatılamamış olabilir")
            Toast.makeText(baseContext, "Kamera hazır değil, lütfen tekrar deneyin", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Fotoğraf çekme işlemi başlatılıyor...")
        Toast.makeText(baseContext, "Fotoğraf çekiliyor...", Toast.LENGTH_SHORT).show()

        try {
            // Create time-stamped output file to hold the image
            val photoFile = File(
                outputDirectory,
                SimpleDateFormat(
                    FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg")
            
            Log.d(TAG, "Fotoğraf dosyası oluşturuldu: ${photoFile.absolutePath}")

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // Set up image capture listener, which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        Toast.makeText(baseContext, "Fotoğraf çekilemedi: ${exc.message}", 
                            Toast.LENGTH_SHORT).show()
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = Uri.fromFile(photoFile)
                        val msg = "Fotoğraf kaydedildi: $savedUri"
                        Log.d(TAG, msg)
                        Toast.makeText(baseContext, "Fotoğraf kaydedildi", Toast.LENGTH_SHORT).show()
                        
                        // Çekilen fotoğrafı göster
                        binding.imagePreview.setImageURI(savedUri)
                        binding.imagePreview.visibility = android.view.View.VISIBLE
                        
                        // Kamera kontrolleri yerine yükleme kontrollerini göster
                        binding.layoutCameraControls.visibility = android.view.View.GONE
                        binding.layoutUploadControls.visibility = android.view.View.VISIBLE
                        
                        // Yükle butonunu ayarla
                        binding.buttonUpload.setOnClickListener {
                            uploadPhotoToFirebase(savedUri)
                        }
                        
                        // Tekrar çek butonunu ayarla
                        binding.buttonRetake.setOnClickListener {
                            // Kamera kontrollerini tekrar göster
                            binding.layoutCameraControls.visibility = android.view.View.VISIBLE
                            binding.layoutUploadControls.visibility = android.view.View.GONE
                            binding.imagePreview.visibility = android.view.View.GONE
                        }
                    }
                })
            Log.d(TAG, "takePicture çağrısı yapıldı")
        } catch (e: Exception) {
            Log.e(TAG, "Fotoğraf çekilirken hata oluştu: ${e.message}", e)
            Toast.makeText(baseContext, "Fotoğraf çekilirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadPhotoToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Kullanıcı oturum açmamış", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (taskId == null) {
            Toast.makeText(this, "Görev bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Yükleme durumunu göster
        binding.buttonUpload.isEnabled = false
        binding.buttonRetake.isEnabled = false
        binding.progressUpload.visibility = android.view.View.VISIBLE
        
        // Benzersiz dosya adı oluştur
        val fileName = "${UUID.randomUUID()}.jpg"
        
        // Storage referansı oluştur
        val storageRef = storage.reference.child("task_photos/$fileName")
        
        // Firebase Storage'a yükle
        storageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                // Firestore'a fotoğraf bilgilerini kaydet
                val photoData = hashMapOf(
                    "userId" to userId,
                    "taskId" to taskId,
                    "taskName" to taskTitle,
                    "photoUrl" to downloadUri.toString(),
                    "timestamp" to System.currentTimeMillis(),
                    "isPublic" to true
                )
                
                firestore.collection("user_photos")
                    .add(photoData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "Fotoğraf Firestore'a kaydedildi: ${documentReference.id}")
                        
                        // Görev ilerleme durumunu güncelle
                        updateTaskProgress()
                    }
                    .addOnFailureListener { e ->
                        binding.buttonUpload.isEnabled = true
                        binding.buttonRetake.isEnabled = true
                        binding.progressUpload.visibility = android.view.View.GONE
                        
                        Log.e(TAG, "Fotoğraf Firestore'a kaydedilemedi: ${e.message}", e)
                        Toast.makeText(this, "Fotoğraf kaydedilemedi: ${e.message}", 
                            Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.buttonUpload.isEnabled = true
                binding.buttonRetake.isEnabled = true
                binding.progressUpload.visibility = android.view.View.GONE
                
                Log.e(TAG, "Fotoğraf yüklenemedi: ${e.message}", e)
                Toast.makeText(this, "Fotoğraf yüklenemedi: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
    }
    
    private fun updateTaskProgress() {
        taskId?.let { id ->
            val newCompletedCount = taskCurrentCount + 1
            
            // İlerleme metnini güncelle
            taskCurrentCount = newCompletedCount
            binding.textTaskProgress.text = "$taskCurrentCount/$taskTotalCount"
            
            // Firestore'da görev durumunu güncelleme intentini oluştur
            val resultIntent = Intent().apply {
                putExtra("TASK_ID", id)
                putExtra("NEW_COUNT", newCompletedCount)
                putExtra("IS_COMPLETED", newCompletedCount >= taskTotalCount)
            }
            
            setResult(RESULT_OK, resultIntent)
            
            // Görev tamamlandıysa mesaj göster ve aktiviteyi kapat
            if (newCompletedCount >= taskTotalCount) {
                Toast.makeText(this, "Görev tamamlandı! +100 puan kazandınız.", 
                    Toast.LENGTH_LONG).show()
                finish()
            } else {
                // Başarılı mesajı göster
                Toast.makeText(this, "Fotoğraf yüklendi!", Toast.LENGTH_SHORT).show()
                
                // Kamera kontrollerini tekrar göster
                binding.layoutCameraControls.visibility = android.view.View.VISIBLE
                binding.layoutUploadControls.visibility = android.view.View.GONE
                binding.imagePreview.visibility = android.view.View.GONE
                binding.progressUpload.visibility = android.view.View.GONE
            }
        }
    }

    private fun startCamera() {
        try {
            Log.d(TAG, "Kamera başlatılıyor...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                try {
                    // Used to bind the lifecycle of cameras to the lifecycle owner
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    Log.d(TAG, "CameraProvider alındı")

                    // Preview
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                        }
                    Log.d(TAG, "Preview oluşturuldu")

                    // ImageCapture
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    Log.d(TAG, "ImageCapture oluşturuldu")

                    // Select back camera as a default
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll()
                        Log.d(TAG, "Kamera use cases unbind edildi")

                        // Bind use cases to camera
                        cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture)
                        Log.d(TAG, "Kamera lifecycle'a bağlandı")

                    } catch(exc: Exception) {
                        Log.e(TAG, "Use case binding failed", exc)
                        Toast.makeText(this, "Kamera başlatılamadı: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kamera provider alınırken hata: ${e.message}", e)
                    Toast.makeText(this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e(TAG, "Kamera başlatılırken hata: ${e.message}", e)
            Toast.makeText(this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val outputDirectory: File by lazy {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Kamera kullanımı için gerekli izinler verilmedi.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA)
    }
} 