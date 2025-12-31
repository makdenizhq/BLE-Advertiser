package com.example.bleadvertiser

import android.Manifest
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var advertiser: BluetoothLeAdvertiser
    private var isAdvertising = false
    private val MAX_ENCRYPTED_BYTES = 17
    private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val MFG_ID = 0xFFFF
    private val PREFIX = "DOORSYS|"
    private val REQ_BLE_ADVERTISE = 1001

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            runOnUiThread {
                findViewById<TextView>(R.id.tvStatus).text = "Durum: Yayın yapılıyor"
                Toast.makeText(this@MainActivity, "Yayın başladı!", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            runOnUiThread {
                findViewById<TextView>(R.id.tvStatus).text = "Durum: Hata ($errorCode)"
                Toast.makeText(this@MainActivity, "Başlatılamadı: $errorCode", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth desteklenmiyor veya kapalı!", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val etName = findViewById<EditText>(R.id.etName)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val etFixedKey = findViewById<EditText>(R.id.etFixedKey)
        val switchDaily = findViewById<Switch>(R.id.switchDailyKey)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnShareQr = findViewById<Button>(R.id.btnShareQr)
        val ivQr = findViewById<ImageView>(R.id.ivQr)

        switchDaily.setOnCheckedChangeListener { _, isChecked ->
            etFixedKey.isEnabled = !isChecked
        }

        btnShareQr.setOnClickListener {
            val currentKey = getCurrentKey(etFixedKey.text.toString(), switchDaily.isChecked)
            generateQrCode(currentKey, ivQr)
        }

        btnStart.setOnClickListener {
            val name = etName.text.toString().trim()
            val message = etMessage.text.toString().trim()
            if (name.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Kimlik ve mesaj girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!ensureBleAdvertisePermission()) {
                return@setOnClickListener
            }

            val plainText = "$name|$message"
            val key = getCurrentKey(etFixedKey.text.toString(), switchDaily.isChecked)

            try {
                var shortened = plainText
                var encrypted = aesEncrypt(shortened, key)
                if (encrypted.size > MAX_ENCRYPTED_BYTES) {
                    while (encrypted.size > MAX_ENCRYPTED_BYTES && shortened.length > 1) {
                        shortened = shortened.dropLast(1)
                        encrypted = aesEncrypt(shortened, key)
                    }
                    Toast.makeText(this, "Veri uzun, kısaltıldı: $shortened", Toast.LENGTH_LONG).show()
                }
                startAdvertising(encrypted)
            } catch (e: Exception) {
                Toast.makeText(this, "Şifreleme hatası: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnStop.setOnClickListener { stopAdvertising() }
    }

    private fun getCurrentKey(fixedKey: String, useDaily: Boolean): String {
        return if (useDaily) {
            val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val base = fixedKey.ifEmpty { "BuBenim32ByteGizliAnahtar1234567" }
            sha256("$base$date").substring(0, 32) // 32 characters hex
        } else {
            val key = fixedKey.ifEmpty { "BuBenim32ByteGizliAnahtar1234567" }
            if (key.length != 32) "BuBenim32ByteGizliAnahtar1234567" else key
        }
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun aesEncrypt(text: String, keyStr: String): ByteArray {
        val keyBytes = keyStr.toByteArray(Charsets.UTF_8).copyOf(32)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun generateQrCode(text: String, imageView: ImageView) {
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, 400, 400)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            imageView.setImageBitmap(bitmap)
            imageView.visibility = android.view.View.VISIBLE
            Toast.makeText(this, "QR hazır! Uzun basıp paylaşabilirsin", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "QR oluşturulamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAdvertising(encryptedData: ByteArray) {
        if (isAdvertising) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val payload = buildManufacturerPayload(encryptedData)
        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .addManufacturerData(MFG_ID, payload)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth izni eksik!", Toast.LENGTH_SHORT).show()
            return
        }

        advertiser.startAdvertising(settings, data, advertiseCallback)
        isAdvertising = true
    }

    private fun stopAdvertising() {
        if (!isAdvertising) return
        advertiser.stopAdvertising(advertiseCallback)
        isAdvertising = false
        findViewById<TextView>(R.id.tvStatus).text = "Durum: Durduruldu"
        findViewById<ImageView>(R.id.ivQr).visibility = android.view.View.GONE
    }

    private fun buildManufacturerPayload(encryptedData: ByteArray): ByteArray {
        val prefixBytes = PREFIX.toByteArray(Charsets.UTF_8)
        val maxPayload = 20
        val maxEncrypted = maxPayload - prefixBytes.size
        val clipped = if (encryptedData.size > maxEncrypted) {
            encryptedData.copyOf(maxEncrypted)
        } else {
            encryptedData
        }
        return prefixBytes + clipped
    }

    private fun ensureBleAdvertisePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE),
            REQ_BLE_ADVERTISE
        )
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BLE_ADVERTISE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth izni verildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth izni reddedildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
    }
}
