package com.example.bleadvertiser

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val notificationChannelId = "GeofenceChannel"
    private val geofenceEnterNotificationId = 5678
    private val geofenceExitNotificationId = 8765

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            return
        }

        if (geofencingEvent?.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val sharedPref = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
            val apartment = sharedPref.getString("APARTMENT", null)
            val signalId = sharedPref.getString("SIGNAL_ID", null)
            val bleKey = sharedPref.getString("BLE_KEY", null)
            val txPowerPos = sharedPref.getInt("TX_POWER_POS", 0)
            val advModePos = sharedPref.getInt("ADV_MODE_POS", 0)

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
                sendNotification(context, context.getString(R.string.geofence_enter_title), context.getString(R.string.geofence_enter_message), geofenceEnterNotificationId)

                Handler(Looper.getMainLooper()).postDelayed({
                    context.stopService(Intent(context, BleAdvertiserService::class.java))
                    sendNotification(context, context.getString(R.string.geofence_exit_title), context.getString(R.string.geofence_exit_message), geofenceExitNotificationId)
                }, 3 * 60 * 1000)
            }
        }
    }

    private fun sendNotification(context: Context, title: String, message: String, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // minSdk >= 26 olduğu için Build.VERSION.SDK_INT >= Build.VERSION_CODES.O kontrolü gereksiz
        val channel = NotificationChannel(
            notificationChannelId,
            "Coğrafi Alan Uyarıları",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, notification)
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

        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedData = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

        return iv + encryptedData
    }
}
