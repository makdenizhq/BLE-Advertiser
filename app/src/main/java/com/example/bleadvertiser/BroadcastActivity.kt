package com.example.bleadvertiser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class BroadcastActivity : AppCompatActivity() {

    private lateinit var etApartment: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnStartBroadcast: Button
    private lateinit var tvBroadcastStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broadcast)

        etApartment = findViewById(R.id.etApartment)
        etPassword = findViewById(R.id.etPassword)
        btnStartBroadcast = findViewById(R.id.btnStartBroadcast)
        tvBroadcastStatus = findViewById(R.id.tvBroadcastStatus)

        btnStartBroadcast.setOnClickListener {
            startBroadcast()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startBroadcast() {
        val apartment = etApartment.text.toString()
        val password = etPassword.text.toString()

        if (apartment.length != 2 || password.length != 6) {
            Toast.makeText(this, "Daire no 2, şifre 6 karakter olmalı.", Toast.LENGTH_SHORT).show()
            return
        }

        val plainText = "$apartment|$password"
        val encryptedData = encryptData(plainText)

        val serviceIntent = Intent(this, BleAdvertiserService::class.java).apply {
            putExtra("encryptedData", encryptedData)
        }
        startService(serviceIntent)

        tvBroadcastStatus.text = "Durum: Yayın yapılıyor..."
        btnStartBroadcast.isEnabled = false
        Toast.makeText(this, "Yayın başlatıldı.", Toast.LENGTH_SHORT).show()

        // 3 dakika sonra yayını durdur ve aktiviteyi kapat
        Handler(Looper.getMainLooper()).postDelayed({
            stopService(serviceIntent)
            tvBroadcastStatus.text = "Durum: Yayın durduruldu."
            Toast.makeText(this, "3 dakika doldu, yayın durduruldu.", Toast.LENGTH_SHORT).show()
            finish() // Sayfayı kapat
        }, 3 * 60 * 1000)
    }

    @SuppressLint("GetInstance")
    private fun encryptData(text: String): ByteArray {
        val key = "BuBenim32ByteGizliAnahtar1234567".toByteArray(Charsets.UTF_8).copyOf(32)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
}
