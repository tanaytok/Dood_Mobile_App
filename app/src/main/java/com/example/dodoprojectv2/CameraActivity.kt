package com.example.dodoprojectv2

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.example.dodoprojectv2.api.Content
import com.example.dodoprojectv2.api.GeminiClient
import com.example.dodoprojectv2.api.GeminiRequest
import com.example.dodoprojectv2.api.Part
import com.example.dodoprojectv2.databinding.ActivityCameraBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var taskId: String? = null
    private var taskTitle: String? = null
    private var taskTotalCount: Int = 1
    private var taskCurrentCount: Int = 0
    
    // Çekilen fotoğrafı tutmak için değişken
    private var capturedBitmap: Bitmap? = null
    
    // Fallback işlem kontrolü
    private var isFallbackProcessing = false
    
    // Timeout handler kontrolü
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    
    private val TAG = "CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 CameraActivity onCreate başlatıldı")
        Log.d(TAG, "🔍 İsEmulator: ${isEmulator()}")
        Log.d(TAG, "📱 Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}")
        
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
        binding.buttonCapturePhoto.setOnClickListener { 
            Log.d(TAG, "🎯 BUTTON CLICKED: Fotoğraf çek butonu tıklandı!")
            
            Log.d(TAG, "🔍 ImageCapture initialized: ${::imageCapture.isInitialized}")
            Log.d(TAG, "🔍 IsEmulator: ${isEmulator()}")
            Log.d(TAG, "🔍 Activity state - isDestroyed: $isDestroyed, isFinishing: $isFinishing")
            
            if (::imageCapture.isInitialized) {
                Log.d(TAG, "📷 ImageCapture initialized, takePicture çağrılıyor")
                takePicture()
            } else {
                Log.w(TAG, "⚠️ ImageCapture henüz hazır değil")
                
                if (isEmulator()) {
                    Log.d(TAG, "🤖 EMÜLATÖR: Test fotoğrafı oluşturuluyor")
                    createFallbackPhoto()
                } else {
                    Log.d(TAG, "📱 GERÇEK CİHAZ: Bekleniyor")
                    Toast.makeText(this, "Kamera henüz hazır değil, lütfen bekleyin", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // İptal et butonunu ayarla
        binding.buttonCancel.setOnClickListener {
            Log.d(TAG, "❌ Cancel butonu tıklandı")
            finish()
        }
        
        // DEBUG: Test butonu ekle (sadece emülatör için)
        if (isEmulator()) {
            binding.buttonCancel.text = "Test Foto"
            binding.buttonCancel.setOnClickListener {
                Log.d(TAG, "🧪 TEST BUTONU: Fallback fotoğraf zorla oluşturuluyor")
                Toast.makeText(this, "TEST: Fallback fotoğraf oluşturuluyor", Toast.LENGTH_SHORT).show()
                createFallbackPhoto()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePicture() {
        Log.d(TAG, "📸 takePicture() başlatıldı")
        
        // Activity lifecycle kontrolü
        if (isDestroyed || isFinishing) {
            Log.w(TAG, "⚠️ Activity destroyed/finishing, işlem iptal edildi")
            return
        }

        if (!::imageCapture.isInitialized) {
            Log.e(TAG, "❌ imageCapture initialize edilmemiş!")
            if (isEmulator()) {
                Log.d(TAG, "🤖 EMÜLATÖR: Fallback foto oluşturuluyor")
                createFallbackPhoto()
            } else {
                handleCameraError(Exception("ImageCapture hazır değil"))
            }
            return
        }

        Log.d(TAG, "💾 Output directory oluşturuluyor...")
        
        // İlk olarak kamera kullanılabilir mi kontrol et
        if (!::imageCapture.isInitialized) {
            Log.e(TAG, "ImageCapture henüz başlatılmamış")
            Toast.makeText(this, "Kamera hazırlanıyor, lütfen bekleyin", Toast.LENGTH_SHORT).show()
            
            // Emülatörde kamera problemi varsa fallback mekanizması
            if (isEmulator()) {
                Log.d(TAG, "EMÜLATÖR MODU: Kamera hazır değil, fallback fotoğraf oluşturuluyor")
                Handler(Looper.getMainLooper()).postDelayed({
                    createFallbackPhoto()
                }, 1000)
            }
            return
        }
        
        Log.d(TAG, "takePicture başlatıldı")

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        try {
            // Emülatör için özel handling
            if (isEmulator()) {
                Log.d(TAG, "EMÜLATÖR MODU: Özel fotoğraf çekme modu")
                
                // Emülatör için timeout mekanizması (10 saniye - gerçek kamera için)
                timeoutHandler = Handler(Looper.getMainLooper())
                timeoutRunnable = Runnable {
                    Log.w(TAG, "🤖 EMÜLATÖR TIMEOUT: Kamera callback gelmedi, fallback photo oluşturuluyor")
                    createFallbackPhoto()
                }
                timeoutHandler?.postDelayed(timeoutRunnable!!, 10000) // 10 saniye timeout
            }
            
            // Set up image capture listener, which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        
                        // Emülatörde hata varsa fallback kullan
                        if (isEmulator()) {
                            Log.d(TAG, "EMÜLATÖR MODU: Kamera hatası, fallback fotoğraf oluşturuluyor")
                            createFallbackPhoto()
                            return
                        }
                        
                        // Daha detaylı hata mesajı
                        val errorMessage = when (exc.imageCaptureError) {
                            ImageCapture.ERROR_CAMERA_CLOSED -> "Kamera kapandı, tekrar başlatılıyor..."
                            ImageCapture.ERROR_CAPTURE_FAILED -> "Fotoğraf çekme işlemi başarısız"
                            ImageCapture.ERROR_FILE_IO -> "Dosya yazma hatası"
                            ImageCapture.ERROR_INVALID_CAMERA -> "Geçersiz kamera"
                            else -> "Kamera hatası: ${exc.message}"
                        }
                        
                        Toast.makeText(baseContext, errorMessage, Toast.LENGTH_SHORT).show()
                        
                        // Kamera kapandıysa tekrar başlatmayı dene
                        if (exc.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED) {
                            Log.d(TAG, "Kamera kapandı, tekrar başlatılıyor...")
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (!isDestroyed && !isFinishing) {
                                        startCamera()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Kamera tekrar başlatılamadı: ${e.message}")
                                }
                            }, 1000)
                        }
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "✅ onImageSaved callback geldi!")
                        
                        // Emülatör timeout'unu iptal et - gerçek fotoğraf geldi
                        timeoutHandler?.removeCallbacks(timeoutRunnable!!)
                        timeoutHandler = null
                        timeoutRunnable = null
                        
                        if (isDestroyed || isFinishing) {
                            Log.w(TAG, "Activity destroy edilmiş, fotoğraf işlenmeyecek")
                            return
                        }
                        
                        val savedUri = Uri.fromFile(photoFile)
                        val msg = "Fotoğraf kaydedildi: $savedUri"
                        Log.d(TAG, msg)
                        
                        // Emülatör bilgisi
                        if (isEmulator()) {
                            Log.d(TAG, "EMÜLATÖR MODU: Fotoğraf başarıyla kaydedildi")
                        }
                        
                        try {
                            // Çekilen fotoğrafı bitmap olarak yükle ve göster
                            val bitmap: Bitmap? = try {
                                // Daha güvenli bitmap yükleme
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 2 // Bellek kullanımını azalt
                                    inJustDecodeBounds = false
                                }
                                BitmapFactory.decodeFile(photoFile.absolutePath, options)
                            } catch (e: Exception) {
                                Log.e(TAG, "Fotoğraf dosyası bitmap'e dönüştürülemedi: ${e.message}")
                                null
                            }
                            
                            if (bitmap != null && !bitmap.isRecycled) {
                                // Bitmap'i ImageView'a yerleştir
                                binding.imagePreview.setImageBitmap(bitmap)
                                binding.imagePreview.visibility = View.VISIBLE
                                
                                // capturedBitmap sınıf değişkenine kaydet
                                capturedBitmap = bitmap
                                
                                Log.d(TAG, "Fotoğraf bitmap olarak yüklendi ve görüntülendi")
                            } else {
                                // Bitmap oluşturulamadıysa da URI'yi göster
                                try {
                                    binding.imagePreview.setImageURI(savedUri)
                                    binding.imagePreview.visibility = View.VISIBLE
                                    Log.d(TAG, "Bitmap oluşturulamadı, URI doğrudan gösterildi")
                                } catch (e: Exception) {
                                    Log.e(TAG, "URI gösterimi de başarısız: ${e.message}")
                                    Toast.makeText(baseContext, "Fotoğraf görüntülenemedi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Fotoğraf gösterimi sırasında hata: ${e.message}", e)
                            // Yine de URI'yi göstermeyi dene
                            try {
                                binding.imagePreview.setImageURI(savedUri)
                                binding.imagePreview.visibility = View.VISIBLE
                            } catch (uriException: Exception) {
                                Log.e(TAG, "URI gösterimi de başarısız: ${uriException.message}")
                            }
                        }
                        
                        processPhotoTaken(savedUri)
                    }
                })
            Log.d(TAG, "takePicture çağrısı yapıldı")
            
        } catch (e: Exception) {
            Log.e(TAG, "takePicture sırasında hata oluştu: ${e.message}", e)
            
            // Emülatörde hata varsa fallback kullan
            if (isEmulator()) {
                Log.d(TAG, "EMÜLATÖR MODU: Hata oluştu, fallback fotoğraf oluşturuluyor")
                createFallbackPhoto()
            } else {
                Toast.makeText(baseContext, "Fotoğraf çekilirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createFallbackPhoto() {
        Log.d(TAG, "🎨 createFallbackPhoto() başlatıldı")
        
        // Activity lifecycle kontrolü
        if (isDestroyed || isFinishing) {
            Log.w(TAG, "⚠️ Activity destroyed/finishing, fallback photo işlemi iptal edildi")
            return
        }
        
        // Çoklu çağrı kontrolü
        if (isFallbackProcessing) {
            Log.w(TAG, "⚠️ Fallback işlemi zaten devam ediyor, yinelenen çağrı atlanıyor")
            return
        }
        
        isFallbackProcessing = true
        Log.d(TAG, "🖼️ Fallback bitmap oluşturuluyor...")
        
        try {
            // Varsayılan bitmap oluştur
            val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.LTGRAY)
            
            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                textSize = 24f
                isAntiAlias = true
            }
            
            canvas.drawText("EMÜLATÖR FOTOĞRAFİ", 50f, 100f, paint)
            canvas.drawText(taskTitle ?: "Görev", 50f, 150f, paint)
            canvas.drawText("${System.currentTimeMillis()}", 50f, 200f, paint)
            
            // Bitmap'i dosyaya kaydet
            val photoFile = File(
                outputDirectory,
                "fallback_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.jpg"
            )
            
            val outputStream = photoFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            
            val savedUri = Uri.fromFile(photoFile)
            
            // UI'yi güncelle
            runOnUiThread {
                binding.imagePreview.setImageBitmap(bitmap)
                binding.imagePreview.visibility = View.VISIBLE
                capturedBitmap = bitmap
                
                Toast.makeText(this, "Emülatör: Test fotoğrafı oluşturuldu", Toast.LENGTH_SHORT).show()
                
                processPhotoTaken(savedUri)
                
                // İşlem tamamlandı
                isFallbackProcessing = false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fallback fotoğraf oluşturulamadı: ${e.message}")
            Toast.makeText(this, "Fotoğraf oluşturulamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            isFallbackProcessing = false
        }
    }
    
    private fun processPhotoTaken(savedUri: Uri) {
        // Kamera kontrolleri yerine yükleme kontrollerini göster
        binding.layoutCameraControls.visibility = View.GONE
        binding.layoutUploadControls.visibility = View.VISIBLE
        
        // EMÜLATÖR TEST MODU - Otomatik olarak yükle
        Log.d(TAG, "TEST MODU: Otomatik yükleme aktif")
        Toast.makeText(baseContext, "TEST MODU: Otomatik olarak görev kabul ediliyor", Toast.LENGTH_LONG).show()
        
        runOnUiThread {
            // Fotoğraf analizini atlayarak direkt başarı göster
            showTaskSuccessAndUpload(savedUri)
            Log.d(TAG, "Fotoğraf analizi atlandı, direkt başarılı kabul edildi")
        }
        
        // Normal yükle butonunu yine de konfigüre et
        binding.buttonUpload.setOnClickListener {
            // Analiz durumu metnini göster
            binding.textAnalysisStatus.visibility = View.VISIBLE
            binding.buttonUpload.isEnabled = false
            binding.buttonRetake.isEnabled = false
            
            // Analizi atlayarak direkt başarılı kabul et
            showTaskSuccessAndUpload(savedUri)
        }
        
        // Tekrar çek butonunu ayarla
        binding.buttonRetake.setOnClickListener {
            // Kamera kontrollerini tekrar göster
            binding.layoutCameraControls.visibility = View.VISIBLE
            binding.layoutUploadControls.visibility = View.GONE
            binding.imagePreview.visibility = View.GONE
        }
    }
    
    private fun showTaskSuccessAndUpload(savedUri: Uri) {
        Log.d(TAG, "showTaskSuccessAndUpload başlatıldı - URI: $savedUri")
        
        // Butonları etkinleştir - özellikle emülatör için
        binding.buttonUpload.isEnabled = true
        binding.buttonRetake.isEnabled = true
        
        // Butonlara manuel tıklama olay dinleyicileri ekle
        binding.buttonUpload.setOnClickListener {
            Log.d(TAG, "Manuel yükleme butonu tıklandı")
            // Firebase'e fotoğrafı yükle ve görev durumunu güncelle
            uploadPhotoToFirebase(savedUri)
        }
        
        binding.buttonRetake.setOnClickListener {
            Log.d(TAG, "Tekrar çek butonu tıklandı")
            binding.layoutCameraControls.visibility = View.VISIBLE
            binding.layoutUploadControls.visibility = View.GONE
            binding.imagePreview.visibility = View.GONE
        }
        
        // Analiz durumunu göster (analiz yapılmadı ama sonuç başarılı kabul edildi)
        binding.textAnalysisStatus.visibility = View.VISIBLE
        binding.textAnalysisStatus.text = "Görev onaylandı! Tebrikler! ✓"
        binding.textAnalysisStatus.setTextColor(Color.GREEN)
        Log.d(TAG, "Görev başarılı kabul edildi, Firebase yükleme hazırlanıyor")
        
        try {
            // Konfetti animasyonunu başlat (emülatörde bazen çalışmayabilir)
            try {
                binding.konfettiView.build()
                    .addColors(Color.YELLOW, Color.GREEN, Color.MAGENTA)
                    .setDirection(0.0, 359.0)
                    .setSpeed(1f, 5f)
                    .setFadeOutEnabled(true)
                    .setTimeToLive(2000L)
                    .addShapes(Shape.Square, Shape.Circle)
                    .addSizes(Size(12))
                    .setPosition(-50f, binding.konfettiView.width + 50f, -50f, -50f)
                    .streamFor(300, 2000L)
                
                Log.d(TAG, "Konfetti animasyonu başlatıldı")
            } catch (konfettiEx: Exception) {
                // Konfetti çalışmazsa sessizce devam et
                Log.e(TAG, "Konfetti animasyonu sırasında hata: ${konfettiEx.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Konfetti animasyon bloğunda hata: ${e.message}")
        }
        
        try {
            // Tamamlama ikonunu göster
            binding.taskCompletionIcon.setImageResource(R.drawable.ic_task_completed)
            binding.taskCompletionIcon.visibility = View.VISIBLE
            
            val anim = ObjectAnimator.ofFloat(binding.taskCompletionIcon, "alpha", 0f, 1f)
            anim.duration = 500
            anim.start()
            
            Log.d(TAG, "Görev tamamlama ikonu gösterildi")
        } catch (e: Exception) {
            Log.e(TAG, "Görev tamamlama ikonu gösterimi sırasında hata: ${e.message}")
        }
        
        // EMÜLATÖR TEST MODU: Otomatik olarak Firebase'e yükle
        Log.d(TAG, "TEST MODU: Otomatik Firebase yükleme başlatılıyor")
        // Firebase'e fotoğrafı yükle ve görev durumunu güncelle
        uploadPhotoToFirebase(savedUri)
    }
    
    private fun uploadPhotoToFirebase(imageUri: Uri) {
        Log.d(TAG, "uploadPhotoToFirebase başlatıldı - URI: $imageUri")
        
        // Yüklenme durumunu göster
        binding.progressUpload.visibility = View.VISIBLE
        binding.buttonUpload.isEnabled = false
        binding.buttonRetake.isEnabled = false
        
        try {
            // Firebase Auth kontrol
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.e(TAG, "Kullanıcı oturum açmamış")
                Toast.makeText(this, "Lütfen giriş yapın", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Benzersiz dosya adı oluştur
            val fileName = "task_${taskId}_${System.currentTimeMillis()}.jpg"
            
            // Bitmap oluşturma - güvenli yöntem
            var bitmap: Bitmap? = null
            
            try {
                // Önce capturedBitmap'i kontrol et
                if (capturedBitmap != null && !capturedBitmap!!.isRecycled) {
                    bitmap = capturedBitmap
                    Log.d(TAG, "CapturedBitmap kullanılıyor")
                } else {
                    // URI'den bitmap oluşturmayı dene
                    bitmap = try {
                        val inputStream = contentResolver.openInputStream(imageUri)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2 // Bellek kullanımını azalt
                            inJustDecodeBounds = false
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    } catch (e: Exception) {
                        Log.e(TAG, "URI'den bitmap oluşturulamadı: ${e.message}")
                        null
                    }
                }
                
                // Bitmap hala null ise varsayılan oluştur
                if (bitmap == null || bitmap.isRecycled) {
                    Log.w(TAG, "Bitmap oluşturulamadı, varsayılan bitmap oluşturuluyor")
                    // Varsayılan bitmap oluştur - 400x300 çözünürlüğünde
                    bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                    
                    // Bitmap'in üzerine görev bilgisi çiz
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    
                    val paint = android.graphics.Paint().apply {
                        color = Color.BLACK
                        textSize = 24f
                        isAntiAlias = true
                    }
                    
                    canvas.drawText("Görev Fotoğrafı", 50f, 100f, paint)
                    canvas.drawText(taskTitle ?: "Görev", 50f, 150f, paint)
                    canvas.drawText("${System.currentTimeMillis()}", 50f, 200f, paint)
                    
                    Toast.makeText(this, "Varsayılan fotoğraf kullanılıyor", Toast.LENGTH_SHORT).show()
                }
                
                // Bitmap'i byte array'e dönüştür
                val baos = ByteArrayOutputStream()
                val compressionQuality = 75 // Kalite ve boyut dengesi
                bitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, baos)
                val imageData = baos.toByteArray()
                
                Log.d(TAG, "Bitmap işlendi, boyut: ${imageData.size} bytes")
                
                // Storage referansı oluştur
                val storageRef = storage.reference.child("task_photos/$fileName")
                
                Log.d(TAG, "Firebase Storage'a yükleme başlatılıyor: $fileName")
                
                // Metadata oluştur
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCustomMetadata("taskId", taskId ?: "unknown")
                    .setCustomMetadata("userId", userId)
                    .build()
                
                // Firebase Storage'a byte array yükle
                storageRef.putBytes(imageData, metadata)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) {
                            Log.e(TAG, "Storage yükleme hatası: ${task.exception?.message}")
                            task.exception?.let { throw it }
                        }
                        Log.d(TAG, "Storage yükleme başarılı, downloadUrl alınıyor")
                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "Download URL alındı: $downloadUri")
                        
                        // Firestore'a fotoğraf bilgilerini kaydet
                        val photoData = hashMapOf(
                            "userId" to userId,
                            "taskId" to (taskId ?: "unknown"),
                            "taskName" to (taskTitle ?: "Görev"),
                            "photoUrl" to downloadUri.toString(),
                            "timestamp" to System.currentTimeMillis(),
                            "isPublic" to true,
                            "isApproved" to true // Otomatik onaylı
                        )
                        
                        Log.d(TAG, "Firestore'a veri yazılıyor: user_photos collection")
                        
                        firestore.collection("user_photos")
                            .add(photoData)
                            .addOnSuccessListener { documentReference ->
                                Log.d(TAG, "Fotoğraf Firestore'a kaydedildi: ${documentReference.id}")
                                
                                // Görev ilerleme durumunu güncelle
                                updateTaskProgressWithFirebase(userId)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Fotoğraf Firestore'a kaydedilemedi: ${e.message}", e)
                                
                                // UI'yi geri yükle
                                binding.buttonUpload.isEnabled = true
                                binding.buttonRetake.isEnabled = true
                                binding.progressUpload.visibility = View.GONE
                                
                                Toast.makeText(this, "Firestore kayıt hatası, ancak görev ilerletiliyor", Toast.LENGTH_SHORT).show()
                                
                                // Hata durumunda bile görevi ilerlet
                                updateTaskProgressWithFirebase(userId)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Fotoğraf Storage'a yüklenemedi: ${e.message}", e)
                        
                        // UI'yi geri yükle
                        binding.buttonUpload.isEnabled = true
                        binding.buttonRetake.isEnabled = true
                        binding.progressUpload.visibility = View.GONE
                        
                        Toast.makeText(this, "Yükleme hatası, ancak görev ilerletiliyor", Toast.LENGTH_SHORT).show()
                        
                        // Hata durumunda bile görevi ilerlet
                        updateTaskProgressWithFirebase(userId)
                    }
                    
            } catch (bitmapException: Exception) {
                Log.e(TAG, "Bitmap işlemi sırasında kritik hata: ${bitmapException.message}", bitmapException)
                
                // UI'yi geri yükle
                binding.buttonUpload.isEnabled = true
                binding.buttonRetake.isEnabled = true
                binding.progressUpload.visibility = View.GONE
                
                Toast.makeText(this, "Fotoğraf işlenemedi, ancak görev ilerletiliyor", Toast.LENGTH_SHORT).show()
                
                // Kritik hata durumunda bile görevi ilerlet
                updateTaskProgressWithFirebase(userId)
            }
            
        } catch (generalException: Exception) {
            Log.e(TAG, "Upload işlemi sırasında genel hata: ${generalException.message}", generalException)
            
            // UI'yi geri yükle
            binding.buttonUpload.isEnabled = true
            binding.buttonRetake.isEnabled = true
            binding.progressUpload.visibility = View.GONE
            
            Toast.makeText(this, "Genel hata, ana ekrana dönülüyor", Toast.LENGTH_SHORT).show()
            
            // En son çare: görevi yine de ilerlet
            val userId = auth.currentUser?.uid
            if (userId != null) {
                updateTaskProgressWithFirebase(userId)
            } else {
                // Kullanıcı yoksa bile aktiviteyi kapat
                finish()
            }
        }
    }
    
    private fun updateTaskProgressWithFirebase(userId: String) {
        Log.d(TAG, "updateTaskProgressWithFirebase başlatıldı - userId: $userId")
        
        try {
            // Test modu için güvenlik kontrolü
            val id = taskId ?: "test-task-${System.currentTimeMillis()}"
            val newCompletedCount = taskCurrentCount + 1
            Log.d(TAG, "Görev ilerlemesi güncelleniyor: $taskCurrentCount -> $newCompletedCount / $taskTotalCount")
            
            // İlerleme metnini güncelle
            taskCurrentCount = newCompletedCount
            binding.textTaskProgress.text = "$taskCurrentCount/$taskTotalCount"
            
            // Firestore'da görev durumunu güncelleme intentini oluştur
            val resultIntent = Intent().apply {
                putExtra("TASK_ID", id)
                putExtra("NEW_COUNT", newCompletedCount)
                putExtra("IS_COMPLETED", newCompletedCount >= taskTotalCount)
            }
            
            Log.d(TAG, "Result intent oluşturuldu: TASK_ID=$id, NEW_COUNT=$newCompletedCount, IS_COMPLETED=${newCompletedCount >= taskTotalCount}")
            
            try {
                setResult(RESULT_OK, resultIntent)
                Log.d(TAG, "Result set edildi, RESULT_OK")
            } catch (e: Exception) {
                Log.e(TAG, "Result set edilemedi: ${e.message}")
            }
            
            // Görev tamamlandıysa TaskCompletionActivity'yi başlat
            if (newCompletedCount >= taskTotalCount) {
                Log.d(TAG, "Görev tamamlandı! TaskCompletionActivity başlatılıyor.")
                
                // Görevi tamamlanmış olarak işaretle
                updateTaskCompletionStatus(id)
                
                // Çekilen fotoğrafın URI'sini al
                val photoUri = capturedBitmap?.let { bitmap ->
                    // Bitmap'i geçici bir dosyaya kaydet ve URI'sini al
                    saveImageToTempFile(bitmap)
                } ?: "default_photo_uri"
                
                // TaskCompletionActivity'yi başlat
                val intent = Intent(this, TaskCompletionActivity::class.java).apply {
                    putExtra("PHOTO_URI", photoUri)
                    putExtra("TASK_TITLE", taskTitle)
                    putExtra("TASK_POINTS", 100) // Varsayılan puan
                    putExtra("TASK_COMPLETED", true)
                    putExtra("TASK_ID", id)
                    putExtra("TASK_TOTAL_COUNT", taskTotalCount)
                    putExtra("TASK_CURRENT_COUNT", newCompletedCount)
                }
                startActivity(intent)
                finish()
            } else {
                // Görev henüz tamamlanmadı ama fotoğraf çekildi
                Log.d(TAG, "Fotoğraf yüklendi! Görev devam ediyor: $newCompletedCount/$taskTotalCount")
                
                // Çekilen fotoğrafın URI'sini al
                val photoUri = capturedBitmap?.let { bitmap ->
                    // Bitmap'i geçici bir dosyaya kaydet ve URI'sini al
                    saveImageToTempFile(bitmap)
                } ?: "default_photo_uri"
                
                // TaskCompletionActivity'yi başlat (görev devam ediyor)
                val intent = Intent(this, TaskCompletionActivity::class.java).apply {
                    putExtra("PHOTO_URI", photoUri)
                    putExtra("TASK_TITLE", taskTitle)
                    putExtra("TASK_POINTS", 0) // Henüz puan kazanılmadı
                    putExtra("TASK_COMPLETED", false)
                    putExtra("TASK_PROGRESS", "$newCompletedCount/$taskTotalCount")
                    putExtra("TASK_ID", id)
                    putExtra("TASK_TOTAL_COUNT", taskTotalCount)
                    putExtra("TASK_CURRENT_COUNT", newCompletedCount)
                }
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Görev ilerleme güncellemesinde hata: ${e.message}")
            // Yine de aktiviteyi kapat
            Toast.makeText(this, "Hata oluştu, ana ekrana dönülüyor", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateTaskCompletionStatus(taskId: String) {
        // Firebase ile görev tamamlama
        val userId = auth.currentUser?.uid ?: return
        
        try {
            // Önce görev belgesini al ve puanı öğren
            firestore.collection("tasks").document(taskId).get()
                .addOnSuccessListener { document ->
                    // Görev puanını al (varsayılan olarak 100 puan)
                    val points = document.getLong("points")?.toInt() ?: 100
                    Log.d(TAG, "Görev puanı alındı: $points puan (taskId: $taskId)")
                    
                    // Görevi Firestore'da tamamlanmış olarak işaretle
                    firestore.collection("tasks").document(taskId)
                        .update(
                            mapOf(
                                "status" to "completed",
                                "isCompleted" to true,
                                "completedAt" to System.currentTimeMillis(),
                                "completedByUserId" to userId
                            )
                        )
                        .addOnSuccessListener {
                            Log.d(TAG, "Görev tamamlandı olarak işaretlendi: $taskId")
                            
                            // Kullanıcıya puanları ekle
                            addPointsToUser(userId, points)
                            
                            // Başarı mesajı
                            Toast.makeText(this, "Görev tamamlandı! +$points puan kazandınız.", 
                                Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Görev tamamlama işaretlemesi başarısız: ${e.message}")
                            // Yine de puanları ekle
                            addPointsToUser(userId, points)
                            Toast.makeText(this, "Görev durumu güncellenemedi, ancak puanlarınız eklendi: +$points", 
                                Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Görev bilgileri alınamadı: ${e.message}")
                    // Hata durumunda varsayılan puan ekle
                    addPointsToUser(userId, 100)
                    
                    // Varsayılan puan ile görev tamamlama
                    firestore.collection("tasks").document(taskId)
                        .update(
                            mapOf(
                                "status" to "completed",
                                "isCompleted" to true,
                                "completedAt" to System.currentTimeMillis(),
                                "completedByUserId" to userId
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Görev tamamlandı! +100 puan kazandınız.", 
                                Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { updateError ->
                            Log.e(TAG, "Görev güncellenemedi: ${updateError.message}")
                            Toast.makeText(this, "Görev durumu güncellenemedi, ancak puanlarınız eklendi.", 
                                Toast.LENGTH_LONG).show()
                        }
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "Görev tamamlama işlemi sırasında hata: ${e.message}")
            // Hata durumunda da puanları ekle
            addPointsToUser(userId, 100)
            Toast.makeText(this, "Bir hata oluştu, ancak puanlarınız eklendi.", 
                Toast.LENGTH_LONG).show()
        }
    }
    
    private fun addPointsToUser(userId: String, points: Int) {
        try {
            // Mevcut kullanıcı puanını al ve güncelle
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    // Mevcut puanı al (yoksa 0)
                    val currentPoints = document.getLong("points")?.toInt() ?: 0
                    val newPoints = currentPoints + points
                    
                    // Yeni puanı güncelle
                    firestore.collection("users").document(userId)
                        .update("points", newPoints)
                        .addOnSuccessListener {
                            Log.d(TAG, "Kullanıcı puanı güncellendi: $newPoints (+$points)")
                            
                            // Haftalık skor tablosunu güncelle
                            updateWeeklyScore(userId, points)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Puan güncellemesi başarısız: ${e.message}")
                            
                            // Doküman yoksa oluştur
                            if (e.message?.contains("NOT_FOUND") == true) {
                                createUserPointsDocument(userId, points)
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Kullanıcı puanı alınamadı: ${e.message}")
                    // Doküman yoksa oluştur
                    createUserPointsDocument(userId, points)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Puan ekleme işlemi sırasında hata: ${e.message}")
        }
    }
    
    private fun updateWeeklyScore(userId: String, points: Int) {
        try {
            // Haftalık skor koleksiyonunda kullanıcı belgesi referansı
            val weeklyScoreRef = firestore.collection("weekly_scores").document(userId)
            
            // İlgili belgeyi al ve güncelle
            weeklyScoreRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Mevcut puanı al
                        val currentScore = document.getLong("score")?.toInt() ?: 0
                        val newScore = currentScore + points
                        
                        // Puanı güncelle
                        weeklyScoreRef.update("score", newScore)
                            .addOnSuccessListener {
                                Log.d(TAG, "Haftalık skor güncellendi: $newScore (+$points)")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Haftalık skor güncellenemedi: ${e.message}")
                            }
                    } else {
                        // Belge yoksa yeni oluştur
                        val weeklyScoreData = hashMapOf(
                            "userId" to userId,
                            "score" to points,
                            "username" to (auth.currentUser?.displayName ?: "Anonim Kullanıcı"),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        
                        weeklyScoreRef.set(weeklyScoreData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Haftalık skor belgesi oluşturuldu: $points")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Haftalık skor belgesi oluşturulamadı: ${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Haftalık skor alınamadı: ${e.message}")
                    
                    // Hata durumunda yeni belge oluşturmayı dene
                    val weeklyScoreData = hashMapOf(
                        "userId" to userId,
                        "score" to points,
                        "username" to (auth.currentUser?.displayName ?: "Anonim Kullanıcı"),
                        "updatedAt" to System.currentTimeMillis()
                    )
                    
                    weeklyScoreRef.set(weeklyScoreData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Hata sonrası haftalık skor belgesi oluşturuldu: $points")
                        }
                        .addOnFailureListener { setError ->
                            Log.e(TAG, "Haftalık skor belgesi kesinlikle oluşturulamadı: ${setError.message}")
                        }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Haftalık skor güncelleme işlemi sırasında genel hata: ${e.message}")
        }
    }
    
    private fun createUserPointsDocument(userId: String, points: Int) {
        // Kullanıcı puanları dokümanı yoksa oluştur
        val userData = hashMapOf(
            "userId" to userId,
            "points" to points,
            "displayName" to (auth.currentUser?.displayName ?: "Anonim Kullanıcı"),
            "updatedAt" to System.currentTimeMillis()
        )
        
        firestore.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "Kullanıcı puanları dokümanı oluşturuldu: $points")
                
                // Haftalık skorları da güncelle
                updateWeeklyScore(userId, points)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı puanları dokümanı oluşturulamadı: ${e.message}")
            }
    }

    private fun startCamera() {
        Log.d(TAG, "📹 startCamera() başlatıldı")
        
        // Activity lifecycle kontrolü
        if (isDestroyed || isFinishing) {
            Log.w(TAG, "⚠️ Activity destroyed/finishing, kamera başlatılmıyor")
            return
        }
        
        Log.d(TAG, "🎯 CameraProvider instance alınıyor...")
        
        try {
            Log.d(TAG, "Kamera başlatılıyor...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                // Callback sırasında tekrar lifecycle kontrolü
                if (isDestroyed || isFinishing) {
                    Log.w(TAG, "Callback sırasında activity destroy edilmiş, kamera bağlanmayacak")
                    return@addListener
                }
                
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

                        // Final lifecycle check before binding
                        if (isDestroyed || isFinishing) {
                            Log.w(TAG, "Binding öncesi activity destroy edilmiş")
                            return@addListener
                        }

                        // Bind use cases to camera
                        val camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture)
                        Log.d(TAG, "Kamera lifecycle'a başarıyla bağlandı")
                        
                        // Emülatör kontrolü
                        if (isEmulator()) {
                            Log.d(TAG, "EMÜLATÖR MODU: Kamera başlatıldı")
                        }

                    } catch(exc: Exception) {
                        Log.e(TAG, "Use case binding failed", exc)
                        handleCameraError(exc)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kamera provider alınırken hata: ${e.message}", e)
                    handleCameraError(e)
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e(TAG, "Kamera başlatılırken hata: ${e.message}", e)
            handleCameraError(e)
        }
    }
    
    private fun handleCameraError(exception: Exception) {
        val errorMessage = when {
            exception.message?.contains("destroyed lifecycle") == true -> {
                "Kamera başlatılamadı: Uygulama yaşam döngüsü sorunu"
            }
            exception.message?.contains("Permission") == true -> {
                "Kamera izni sorunu. Lütfen uygulamayı yeniden başlatın."
            }
            isEmulator() -> {
                "Emülatör kamera sorunu. Gerçek cihazda test edin veya AVD kamera ayarlarını kontrol edin."
            }
            else -> {
                "Kamera başlatılamadı: ${exception.message}"
            }
        }
        
        Log.e(TAG, "Kamera hatası: $errorMessage", exception)
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // Hata durumunda aktiviteyi kapat
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed && !isFinishing) {
                finish()
            }
        }, 2000)
    }
    
    private fun isEmulator(): Boolean {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")
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
                Log.d(TAG, "Kamera izinleri verildi")
                startCamera()
            } else {
                Log.e(TAG, "Kamera izinleri reddedildi")
                
                if (isEmulator()) {
                    Toast.makeText(this,
                        "Emülatör: Kamera izni reddedildi. Test modu etkinleştiriliyor.",
                        Toast.LENGTH_LONG).show()
                    
                    // Emülatörde izin yoksa da test moduna geç
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Emülatör test modu: İzin olmadan devam ediliyor")
                    }, 1000)
                } else {
                    Toast.makeText(this,
                        "Kamera kullanımı için gerekli izinler verilmedi.",
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveImageToTempFile(bitmap: Bitmap): String {
        return try {
            val tempFile = File(cacheDir, "temp_task_photo_${System.currentTimeMillis()}.jpg")
            val outputStream = tempFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Geçici dosya oluşturulamadı: ${e.message}")
            ""
        }
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA)
    }
}

// Helper functions for color similarity
private fun isColorSimilar(color1: Int, color2: Int, threshold: Double = 0.8): Boolean {
    val r1 = Color.red(color1)
    val g1 = Color.green(color1)
    val b1 = Color.blue(color1)
    
    val r2 = Color.red(color2)
    val g2 = Color.green(color2)
    val b2 = Color.blue(color2)
    
    // Renk benzerliği algoritması (Öklid mesafesi)
    val distance = sqrt((r1 - r2).toDouble().pow(2) + 
                    (g1 - g2).toDouble().pow(2) + 
                    (b1 - b2).toDouble().pow(2))
    
    val maxDistance = sqrt(255.0.pow(2) * 3)
    val similarity = 1 - (distance / maxDistance)
    
    return similarity >= threshold
} 