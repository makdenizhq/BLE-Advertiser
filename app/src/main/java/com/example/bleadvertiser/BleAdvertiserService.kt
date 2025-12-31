package com.example.bleadvertiser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.UUID

class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    // The UUID for the service
    private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    // The manufacturer ID for the advertising data
    private val MFG_ID = 0xFFFF
    // A prefix for the manufacturer data
    private val PREFIX = "DOORSYS|"
    // The channel ID for the notification
    private val CHANNEL_ID = "BleAdvertiserChannel"

    /**
     * Callback for the advertising events.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            updateNotification("Advertising: Active")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            updateNotification("Error: $errorCode")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null) {
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        }
    }

    /**
     * Called when the service is started.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Service starting...")
        startForeground(1, notification)

        if (intent != null) {
            val encryptedData = intent.getByteArrayExtra("encryptedData")
            val mode = intent.getIntExtra("mode", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            val txPower = intent.getIntExtra("txPower", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)

            if (encryptedData != null) {
                startAdvertising(encryptedData, mode, txPower)
            }
        }
        return START_STICKY
    }

    /**
     * Start BLE advertising.
     * @param encryptedData The data to advertise.
     * @param mode The advertising mode.
     * @param txPower The TX power level.
     */
    private fun startAdvertising(encryptedData: ByteArray, mode: Int, txPower: Int) {
        if (advertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(mode)
            .setTxPowerLevel(txPower)
            .setConnectable(false)
            .build()

        val payload = buildManufacturerPayload(encryptedData)
        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .addManufacturerData(MFG_ID, payload)
            .build()

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Build the manufacturer specific data payload.
     * @param encryptedData The encrypted data to include in the payload.
     * @return The manufacturer specific data payload.
     */
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

    override fun onDestroy() {
        if (advertiser != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
        }
        super.onDestroy()
    }

    /**
     * Create a notification channel for the service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "BLE Advertiser Service", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Create a notification for the service.
     * @param contentText The text to display in the notification.
     * @return The notification.
     */
    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("BLE Advertiser").setContentText(contentText).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(pendingIntent).build()
    }

    /**
     * Update the notification for the service.
     * @param text The text to display in the notification.
     */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }
}
