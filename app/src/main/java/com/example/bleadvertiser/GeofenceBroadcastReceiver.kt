package com.example.bleadvertiser

import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            return
        }

        if (geofencingEvent?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val sharedPref = context.getSharedPreferences("BleAdvertiserPrefs", Context.MODE_PRIVATE)
            val apartment = sharedPref.getString("APARTMENT", null)
            val signalId = sharedPref.getString("SIGNAL_ID", null)
            val bleKey = sharedPref.getString("BLE_KEY", null)
            val txPowerPos = sharedPref.getInt("TX_POWER_POS", 0) // Kaydedilen ayarı oku
            val advModePos = sharedPref.getInt("ADV_MODE_POS", 0) // Kaydedilen ayarı oku

            if (apartment != null && signalId != null && bleKey != null) {
                val plainText = "$apartment|$signalId"
                val txPower = getTxPowerFromPosition(txPowerPos)
                val advMode = getAdvModeFromPosition(advModePos)

                val serviceIntent = Intent(context, BleAdvertiserService::class.java).apply {
                    putExtra("encryptedData", encryptData(plainText, bleKey))
                    putExtra("txPower", txPower)
                    putExtra("advMode", advMode)
                }
                context.startService(serviceIntent)
                Toast.makeText(context, "Coğrafi alana girildi, yayın başlatılıyor...", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    context.stopService(Intent(context, BleAdvertiserService::class.java))
                    Toast.makeText(context, "3 dakika doldu, yayın durduruldu.", Toast.LENGTH_SHORT).show()
                }, 3 * 60 * 1000)
            }
        }
    }

    private fun getTxPowerFromPosition(position: Int): Int {
        return when (position) {
            0 -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            1 -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            2 -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            3 -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
            else -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }
    }

    private fun getAdvModeFromPosition(position: Int): Int {
        return when (position) {
            0 -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            1 -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
            2 -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
            else -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
        }
    }

    private fun encryptData(text: String, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8).copyOf(32)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }
}
