# Rol Bazlı BLE Yönetim ve Yayın Uygulaması

Bu Android uygulaması, `OWNER` ve `RESIDENT` olmak üzere iki farklı rolde çalışabilen, gelişmiş bir BLE (Bluetooth Low Energy) yönetim ve yayın aracıdır. Uygulama, bir cihazın ya bir **yayıncı (Resident)** ya da bir **yönetim aracı (Owner)** olmasına olanak tanır ve bu rolü kalıcı hale getirir.

Uygulama, tek bir APK içinde iki farklı kullanıcı senaryosunu yönetir ve tamamen çevrimdışı (offline-first) çalışır.

---

## 🚀 Temel Özellikler

- **Kalıcı ve Akıllı Rol Sistemi:**
    - Uygulama açılışta, cihazda bir `OWNER_TOKEN` kayıtlı olup olmadığını kontrol eder. Varsa, cihazı bir **yönetim aracı** olarak kabul eder ve doğrudan `OwnerFragment`'ı açar.
    - Token yoksa, `RESIDENT` kimliği olup olmadığına bakar. Varsa, `ResidentFragment`'ı açar.
    - Hiçbir kimlik yoksa, kullanıcıyı sadece `RESIDENT` olarak kayıt olabileceği bir kurulum ekranına yönlendirir.
- **Rol Bazlı Arayüz:** Uygulama, cihazın rolüne göre tamamen farklı bir arayüz ve işlevsellik sunar.
- **Kişiye Özel Dinamik Anahtarlar:** Her `RESIDENT` için, sisteme ilk kayıt sırasında benzersiz, 18 karakterlik bir **BLE Yayın Anahtarı** üretilir. Tüm BLE yayınları bu kişisel anahtarla şifrelenir.
- **Güvenli Şifreleme:** Veriler, `AES/CBC/PKCS5Padding` gibi modern ve güvenli bir şifreleme algoritması kullanılarak korunur.
- **Token Bazlı Yetkilendirme (OWNER):**
    - `OWNER` rolü, `HMAC` ile imzalanmış bir `OWNER_TOKEN` kullanarak çalışır.
    - Android uygulaması token'ı **kriptografik olarak doğrulamaz**, sadece içindeki yetki listesini UI'da göstermek için ayrıştırır. **Gerçek doğrulama, komutu alan ESP32 cihazı tarafından yapılır.**
- **Esnek Token ve QR Yükleme:** `OWNER`'lar, kendilerine atanan `OWNER_TOKEN`'ı veya bir `RESIDENT`'a ait QR kodu, hem **kamerayla tarayarak** hem de **cihazdaki bir resim dosyasından (JPEG/PNG) seçerek** uygulamaya alabilir.
- **Çevrimdışı Cihaz Yönetimi (Provisioning):** `OWNER`, bir `RESIDENT`'ın QR kodunu aldıktan sonra, o kullanıcıyı yakındaki bir ESP32 cihazına BLE GATT üzerinden güvenli bir şekilde kaydedebilir.
- **Otomatik ve Manuel Yayın (RESIDENT):**
    - **Manuel:** `RESIDENT`, GPS'ten bağımsız olarak istediği zaman BLE yayınını başlatıp durdurabilir.
    - **Otomatik (Geofence):** Yüksek doğruluklu (< 2.5m) bir GPS konumu alındığında kurulan coğrafi alana girildiğinde, uygulama otomatik olarak 3 dakikalık bir yayın başlatır.
- **Uygulama Sıfırlama:** Kullanıcılar, ayarlar menüsünden tüm kimlik bilgilerini silerek cihazın rolünü sıfırlayabilir ve uygulamayı ilk kurulum durumuna döndürebilir.

---

## 🛠️ Teknik Akış ve Mimarisi

### 1. Kimlik ve Rol Kurulumu
- Uygulama açıldığında `SetupFragment` arka planda çalışır ve `DataStore`'u kontrol eder.
- Kayıtlı `OWNER_TOKEN` veya `RESIDENT` rolüne göre ilgili fragment'a yönlendirir.
- Hiçbir kayıt yoksa, kullanıcıya `RESIDENT` olarak kimlik bilgilerini (Ad, Daire) girmesi için kurulum arayüzü gösterilir.

### 2. RESIDENT Akışı
- **Sinyal ID:** `ResidentFragment` açıldığında, 6 haneli bir Sinyal ID otomatik olarak üretilir. Kullanıcı isterse değiştirebilir.
- **Anahtar Üretimi:** "Kimliği Kaydet ve QR Üret" butonuna basıldığında, kullanıcıya özel 18 karakterlik `BLE_KEY` üretilir ve `DataStore`'a kaydedilir.
- **QR Kod Formatı:** Üretilen QR kod, `KEY|DAIRE|AD|YAKINLIK` formatında veri içerir.
- **QR Paylaşımı:** Üretilen QR kodun `Bitmap`'i, bir JPEG dosyası olarak `FileProvider` aracılığıyla paylaşılır.
- **BLE Yayını:**
    - **Şifrelenecek Veri:** `DAİRE_NO|SİNYAL_ID`
    - **Şifreleme:** Yukarıdaki veri, kullanıcının `BLE_KEY`'i ile `AES/CBC/PKCS5Padding` kullanılarak şifrelenir. Şifreli verinin başına 16 byte'lık bir **Initialization Vector (IV)** eklenir.
    - **Reklam Paketi:** `[ "DOORSYS|" (8 byte) ] + [ IV (16 byte) ] + [ Şifrelenmiş Veri ]` formatında yayınlanır.

### 3. OWNER Akışı
- **Token Yönetimi:**
    - `OwnerFragment` açıldığında, token yoksa "kilitli" bir arayüz gösterilir.
    - Kullanıcı, kendisine verilen `OWNER_TOKEN`'ı (`DOORSYS_OWNER_TOKEN|v1|<...>`) kamerayla veya dosyadan okutarak alır.
    - Token kaydedildikten sonra yönetim paneli aktif hale gelir.
- **Cihaz Yönetimi (Provisioning):**
    - `QrScannerFragment` veya dosya seçici, `RESIDENT`'ın QR kodunu okur.
    - `ProvisioningFragment`, okunan QR verisiyle açılır.
    - BLE taraması yaparak yakındaki ESP32 cihazlarını listeler.
    - **GATT Write Komutu:** Yetki kontrolünden sonra, aşağıdaki komut ESP32'ye yazılır:
      `ADD_RESIDENT|OWNER_TOKEN|QR_DATA`

### 4. ESP32 Tarafından Beklenenler
- **BLE Advertiser Çözümleme:**
    - `"DOORSYS|"` ön ekine sahip paketleri dinlemeli.
    - Gelen verinin ilk 16 byte'ını **IV** olarak, geri kalanını şifreli veri olarak almalı.
    - Veritabanındaki her bir `BLE_KEY` ile bu veriyi `AES/CBC/PKCS5Padding` kullanarak çözmeyi denemeli.
- **GATT Sunucusu:**
    - `ADD_RESIDENT` komutunu alacak bir BLE GATT servisi ve karakteristiği sunmalı.
    - Gelen komuttaki `OWNER_TOKEN`'ı, kendi `master_key`'i ile **HMAC imzasını doğrulayarak** kontrol etmeli.
    - İmza geçerliyse, komuttaki `QR_DATA`'yı ayrıştırarak yeni kullanıcıyı veritabanına kaydetmeli.
    - İşlem sonucunu (ACK/NACK) bir bildirim (notification) ile Android uygulamasına geri göndermeli.
