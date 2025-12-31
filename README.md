# Otomatik BLE Yayınlayıcı Uygulaması

Bu Android uygulaması, coğrafi alana duyarlı (geofence) ve manuel olarak kontrol edilebilen, kişiye özel şifrelenmiş BLE (Bluetooth Low Energy) sinyalleri yayınlamak için tasarlanmıştır.

Uygulama, bir kullanıcının belirli bir coğrafi alana (örneğin, evinin veya ofisinin önüne) girdiğinde otomatik olarak 3 dakikalık bir BLE yayını başlatmasını sağlar. Ayrıca, kullanıcıya istediği zaman yayını manuel olarak başlatma ve durdurma imkanı sunar.

Her kullanıcı için benzersiz bir şifreleme anahtarı üretilir ve bu anahtar, kullanıcının sisteme tanıtılması için bir QR kod aracılığıyla paylaşılır.

## 🚀 Temel Özellikler

- **Kişiye Özel Şifreleme:** Her kullanıcı için benzersiz bir 18 karakterlik BLE yayın anahtarı üretilir. Yayınlanan veriler (Daire No, Sinyal ID) bu anahtarla AES algoritması kullanılarak şifrelenir.
- **Dinamik Anahtar Paylaşımı:** Üretilen benzersiz anahtar, diğer kullanıcı bilgileriyle birlikte bir QR kod içinde sunulur. Bu, kullanıcıların sisteme kolayca ve güvenli bir şekilde kaydedilmesini sağlar.
- **Otomatik Coğrafi Alan Kurulumu:** Uygulama, yüksek doğruluklu (varsayılan < 2.5m) bir GPS sinyali aldığında, coğrafi alanı (geofence) otomatik olarak kurar.
- **Otomatik Yayın (Geofence):** Kurulan coğrafi alana girildiğinde, uygulama otomatik olarak 3 dakikalık bir BLE yayını başlatır.
- **Manuel Yayın Kontrolü:** Coğrafi alan kurulduktan sonra aktif hale gelen "Yayınla" ve "Durdur" butonları ile kullanıcı, yayını istediği zaman manuel olarak kontrol edebilir.
- **Canlı GPS Durum Takibi:** Ana ekrandaki bir kart, anlık GPS doğruluğunu ve durumunu kullanıcıya bildirir.

## 🛠️ Teknik Akış

1.  **İlk Açılış ve İzinler:** Uygulama ilk açıldığında, `ACCESS_FINE_LOCATION` ve `ACCESS_BACKGROUND_LOCATION` izinlerini ister.
2.  **Bilgi Girişi ve Anahtar Üretimi:**
    - Kullanıcı Daire No, Adı Soyadı, Yakınlık ve Sinyal ID gibi bilgileri girer.
    - **"Kaydet ve QR Üret"** butonuna basıldığında:
        - 18 karakterlik benzersiz bir **BLE Yayın Anahtarı** üretilir.
        - Bu anahtar, girilen diğer tüm bilgilerle birlikte telefonun hafızasına (`SharedPreferences`) kaydedilir.
        - Anahtarı ve diğer temel bilgileri içeren bir QR kod üretilip ekranda gösterilir.
3.  **Coğrafi Alanın Kurulması:**
    - Uygulama, GPS sinyalini dinlemeye başlar.
    - GPS doğruluğu **2.5 metrenin** altına düştüğünde, o anki konum merkez alınarak 50 metrelik bir coğrafi alan (geofence) otomatik olarak kurulur.
    - Bu işlem tamamlandığında, manuel "Yayınla" ve "Durdur" butonları aktif hale gelir.
4.  **Yayın Süreci:**
    - **Otomatik Yayın:** Cihaz, kurulan coğrafi alana girdiğinde `GeofenceBroadcastReceiver` tetiklenir. Telefon hafızasından okunan **kişiye özel BLE anahtarı** ile şifrelenmiş veri, 3 dakikalığına yayınlanır.
    - **Manuel Yayın:** Kullanıcı "Yayınla" butonuna bastığında, yine kişiye özel anahtar ile şifrelenmiş veri, kullanıcı "Durdur" butonuna basana kadar süresiz olarak yayınlanır.

## 📦 BLE Reklam Paketi Yapısı

Yayınlanan "Üreticiye Özel Veri" (Manufacturer Specific Data) paketi şu yapıdadır:

- **Üretici Kimliği (MFG ID):** `0xFFFF` (Test için)
- **Veri (Payload):**
    - **Ön Ek (Prefix):** `"DOORSYS|"` (8 byte) - Sinyalin bu uygulamaya ait olduğunu belirtir.
    - **Şifrelenmiş Veri:** (16 byte) - Kullanıcının Daire No ve Sinyal ID'sinin, **kişiye özel 18 karakterlik anahtar** ile AES şifrelenmiş halidir.

## 🔧 Projeyi Derleme

1.  Bu repoyu klonlayın.
2.  Projeyi Android Studio'da açın.
3.  Gerekli SDK ve araçların yüklü olduğundan emin olun.
4.  Projeyi derleyin (`Build > Make Project`).
5.  Uygulamayı bir cihaza yüklemek için `Run 'app'` komutunu çalıştırın.
