# Uygulama Hedef Akışı (Nihai Versiyon)

Bu belge, uygulamanın son kullanıcı için tasarlanan nihai ve en mantıklı kullanım akışını açıklamaktadır.

---

## 1. Uygulama Başlangıç Akışı

Uygulama her açıldığında, arka planda kullanıcının kimlik durumunu kontrol eder:

- **Eğer bir `OWNER_TOKEN` Kayıtlıysa:**
  - Uygulama, bu cihazın bir **yönetim aracı** olduğunu anlar.
  - Kurulum ekranı **atlanır**.
  - Kullanıcı, **doğrudan** "Owner Kontrol Paneli" ekranına yönlendirilir.

- **Eğer `RESIDENT` Rolü Kayıtlıysa:**
  - Uygulama, bu cihazın bir **yayıncı** olduğunu anlar.
  - Kurulum ekranı **atlanır**.
  - Kullanıcı, **doğrudan** "Resident Kontrol Paneli" ekranına yönlendirilir.

- **Eğer Hiçbir Şey Kayıtlı Değilse (İlk Kurulum):**
  - Kullanıcıyı, sadece `RESIDENT` olarak kayıt olabileceği bir "Kimlik Kurulumu" ekranı karşılar.
  - Kullanıcı Adını ve Daire Numarasını girip kaydeder ve "Resident Kontrol Paneli"ne geçer.

---

## 2. RESIDENT Rolü Akışı

Bu rol, sisteme kimliğini yayınlayacak olan standart kullanıcıdır. Akış değişmemiştir.

1.  **Ana Ekran:** "Resident Kontrol Paneli" ekranını görür.
2.  **Sinyal ID:** "Sinyal ID" alanı, otomatik olarak üretilmiş 6 haneli bir numara ile dolu gelir. Kullanıcı isterse değiştirebilir.
3.  **Anahtar ve QR Üretimi:** **"Kimliği Kaydet ve QR Üret"** butonuna basarak, kendisine özel **BLE Yayın Anahtarı**'nı içeren QR kodunu üretir.
4.  **QR Paylaşımı:** "QR Paylaş" butonu ile üretilen QR kodun resmini (JPEG) bir `OWNER`'a (yöneticiye) gönderir.
5.  **Yayınlama:**
    *   **Manuel:** "Manuel Yayınla" butonu her zaman aktiftir.
    *   **Otomatik (Geofence):** GPS doğruluğu yeterli seviyeye ulaştığında coğrafi alan kurulur ve bu alana girildiğinde otomatik yayın başlar.

---

## 3. OWNER Rolü Akışı

Bu rol, sisteme yeni `RESIDENT`'ları ekleme yetkisine sahip olan yönetici kullanıcıdır. Bu cihazın kendisi bir `RESIDENT` değildir.

1.  **Ana Ekran (Kilitli Durum):**
    *   Uygulama açıldığında, "Owner Kontrol Paneli" ekranı kilitli bir modda açılır.
    *   Ekranda sadece "Token QR Kodu Tara" ve "Token Dosyadan Yükle" seçenekleri bulunur. Resident ekleme işlevleri kapalıdır.
2.  **Token Alma:** OWNER, sistem yöneticisinden aldığı `OWNER_TOKEN`'ı kamerayla veya dosyadan okutarak yükler.
3.  **Ana Ekran (Aktif Durum):**
    *   Token yüklendikten sonra, kilitli kart kaybolur ve yönetim paneli aktif hale gelir.
    *   Ekranda, token'dan okunan "Yetkili Daireler" listesi gösterilir.
    *   "Resident QR Kodu Tara" ve "Resident QR Dosyadan Yükle" butonları aktifleşir.
4.  **Resident Ekleme:**
    *   OWNER, bu butonları kullanarak bir `RESIDENT`'ın QR kodunu alır.
    *   Uygulama, "Cihaz Yönetimi" ekranına geçer.
    *   OWNER, listeden doğru ESP32 cihazını seçer ve kaydı cihaza aktarır.

---

## 4. Rol Değişimi ve Sıfırlama

*   Bir cihazın rolü (`OWNER` veya `RESIDENT`) bir kez ayarlandıktan sonra **kalıcıdır**.
*   Kullanıcı, cihazın rolünü değiştirmek veya uygulamayı başa döndürmek isterse, uygulamanın ayarlar menüsündeki **"Uygulamayı Sıfırla"** seçeneğini kullanmalıdır.
*   Bu işlem, tüm yerel verileri siler ve kullanıcıyı tekrar ilk kurulum ekranına yönlendirir.
