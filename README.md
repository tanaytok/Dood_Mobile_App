# 🎯 DoDo Project v2

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/Min%20API-26-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**🤖 AI-Powered Daily Photography Challenges**

DoDo Project v2, Gemini AI ile desteklenen günlük fotoğraf görevleri uygulamasıdır. Kullanıcıları her gün farklı fotoğraf çekme macerasına çıkararak yaratıcılığı teşvik eder ve günlük rutinlerini eğlenceli hale getirir.

---

## ✨ Özellikler

### 🎨 **AI-Destekli Görev Üretimi**
- 🧠 **Gemini AI Integration**: Her gün benzersiz ve yaratıcı fotoğraf görevleri
- 🎲 **Akıllı Çeşitlilik**: İç mekan, dış mekan ve karma görevler
- 🔄 **Dinamik İçerik**: Kullanıcı davranışlarına göre adapte olan görevler

### 📸 **Gelişmiş Kamera Deneyimi**
- 📱 **CameraX Integration**: Modern ve performanslı kamera API'si
- 🎯 **ML Kit Object Detection**: Gerçek zamanlı nesne tanıma
- ✅ **Akıllı Doğrulama**: AI ile görev tamamlanma kontrolü
- 🎊 **Konfetti Animasyonları**: Başarı anında görsel ödüller

### 🏆 **Gamification & Sosyal**
- 💎 **Puan Sistemi**: Her tamamlanan görev için puanlar
- 🔥 **Streak Takibi**: Günlük görev tamamlama serileri
- 👥 **Sosyal Profil**: Kişisel istatistikler ve fotoğraf galerisi
- 🏅 **Haftalık Liderlik Tablosu**: Topluluk yarışması

### 🎨 **Modern UI/UX**
- 🌙 **Dark/Light Mode**: Tema değiştirme desteği
- 🎭 **Material Design 3**: Modern ve şık arayüz
- 🌀 **Smooth Animations**: Akıcı geçişler ve mikroetkileşimler
- 📱 **Responsive Design**: Tüm ekran boyutlarına uyumlu

---

## 🚀 Teknoloji Stack'i

### **Backend & AI**
- 🤖 **Google Gemini AI** - Görev üretimi
- 🔥 **Firebase Firestore** - Veri tabanı
- 🔐 **Firebase Authentication** - Kullanıcı yönetimi
- ☁️ **Firebase Storage** - Fotoğraf depolama

### **Android Development**
- 🏗️ **Kotlin** - Ana programlama dili
- 🏛️ **MVVM Architecture** - Temiz mimari
- 📦 **Android Jetpack Components**
  - Navigation Component
  - LiveData & ViewModel
  - WorkManager
- 📷 **CameraX** - Kamera işlemleri
- 🧠 **ML Kit** - Makine öğrenmesi

### **UI/UX Libraries**
- 🎨 **Material Design Components**
- 🌀 **Lottie Animations**
- 🎊 **Konfetti** - Başarı animasyonları
- 🖼️ **Glide** - Resim yükleme
- ♻️ **SwipeRefreshLayout** - Yenileme

### **Network & API**
- 🌐 **Retrofit** - HTTP client
- 📡 **OkHttp** - Network interceptor
- 📄 **Gson** - JSON serialization

---

## 📱 Ekran Görüntüleri

<div align="center">
  <img src="screenshots/home_screen.png" width="200" alt="Ana Ekran">
  <img src="screenshots/tasks_screen.png" width="200" alt="Görevler">
  <img src="screenshots/camera_screen.png" width="200" alt="Kamera">
  <img src="screenshots/profile_screen.png" width="200" alt="Profil">
</div>

---

## 🛠️ Kurulum

### Gereksinimler
- Android Studio Arctic Fox (2020.3.1) veya üzeri
- Android SDK API 26+
- Kotlin 1.8.0+
- Firebase projesi

### Adımlar

1. **Repository'yi klonlayın**
   ```bash
   git clone https://github.com/yourusername/dodoprojectv2.git
   cd dodoprojectv2
   ```

2. **Firebase Kurulumu**
   - [Firebase Console](https://console.firebase.google.com)'da yeni proje oluşturun
   - Android uygulaması ekleyin
   - `google-services.json` dosyasını `app/` klasörüne yerleştirin

3. **API Keys Yapılandırması**
   ```kotlin
   // app/build.gradle.kts
   buildConfigField("String", "GEMINI_API_KEY", "\"YOUR_GEMINI_API_KEY\"")
   ```

4. **Firestore Kuralları**
   ```javascript
   // firestore.rules
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

5. **Uygulamayı Build Edin**
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🎮 Kullanım

### 🚀 Başlangıç
1. Uygulamayı açın ve hesap oluşturun
2. Günlük görevlerinizi görüntüleyin
3. Fotoğraf çekmeye başlayın!

### 📸 Görev Tamamlama
1. **Görev Seç**: Ana ekrandan bir görev seçin
2. **Fotoğraf Çek**: Kamera ile ilgili objeyi çekin
3. **AI Doğrulama**: Uygulama otomatik olarak doğrular
4. **Puan Kazan**: Başarılı görevler için puan kazanın

### 🏆 İlerleme Takibi
- **Günlük Streak**: Ardışık günlerde görev tamamlama
- **Haftalık Skor**: Diğer kullanıcılarla yarış
- **Profil Galerisi**: Çektiğiniz fotoğrafları görüntüleyin

---

## 🔧 Geliştirme

### Proje Yapısı
```
app/
├── src/main/java/com/example/dodoprojectv2/
│   ├── api/           # Gemini API entegrasyonu
│   ├── ui/            # Fragment'lar ve ViewModel'lar
│   │   ├── home/      # Ana ekran
│   │   ├── tasks/     # Görevler ekranı
│   │   ├── camera/    # Kamera aktivitesi
│   │   └── profile/   # Profil ekranı
│   ├── work/          # Background işlemler
│   └── utils/         # Yardımcı sınıflar
├── src/main/res/      # Kaynak dosyalar
└── build.gradle.kts   # Build konfigürasyonu
```

### Branch Stratejisi
- `main` - Stabil sürümler
- `develop` - Geliştirme branch'i
- `feature/*` - Yeni özellikler
- `hotfix/*` - Acil düzeltmeler

---

## 🤝 Katkıda Bulunma

Katkılarınızı memnuniyetle karşılıyoruz! 

1. Fork edin
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add amazing feature'`)
4. Branch'inizi push edin (`git push origin feature/amazing-feature`)
5. Pull Request oluşturun

### 📋 Geliştirme Kuralları
- Kotlin kod stiline uyun
- Unit testler yazın
- Commit mesajlarını açıklayıcı yapın
- Documentation güncelleyin

---

## 🐛 Bilinen Sorunlar

- [ ] Düşük ışıkta nesne tanıma performansı
- [ ] Bazı cihazlarda kamera açılış gecikmesi
- [ ] Offline mod sınırlı fonksiyonalite

---

## 📋 Gelecek Özellikler

- [ ] 🎥 Video görevleri
- [ ] 👥 Arkadaş sistemi
- [ ] 🏆 Başarım rozet sistemi
- [ ] 📊 Detaylı istatistikler
- [ ] 🌍 Konum bazlı görevler
- [ ] 🎨 Fotoğraf editör
- [ ] 📤 Sosyal medya paylaşımı

---

## 📄 Lisans

Bu proje MIT lisansı altında lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakın.

---

## 👨‍💻 Geliştirici

**[Adınız]**
- 📧 Email: [email@example.com]
- 🐦 Twitter: [@twitter_handle]
- 💼 LinkedIn: [linkedin.com/in/profile]

---

## 🙏 Teşekkürler

- 🤖 **Google Gemini AI** - Akıllı görev üretimi için
- 🔥 **Firebase** - Backend hizmetleri için
- 📱 **Android Team** - Harika development araçları için
- 🎨 **Material Design** - Güzel UI component'lar için

---

<div align="center">
  <h3>⭐ Beğendiyseniz yıldızlamayı unutmayın! ⭐</h3>
  
  **Made with ❤️ by Turkish Developers**
</div>
