package com.example.bleadvertiser

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private val mfgId = 0xFFFF
    private val prefix = "DOORSYS|"
    private val channelId = "BleAdvertiserChannel"

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
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter != null) {
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Service starting...")
        startForeground(1, notification)

        if (intent != null) {
            val encryptedData = intent.getByteArrayExtra("encryptedData")
            val advMode = intent.getIntExtra("advMode", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            val txPower = intent.getIntExtra("txPower", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)

            if (encryptedData != null) {
                startAdvertising(encryptedData, advMode, txPower)
            }
        }
        return START_STICKY
    }

    private fun startAdvertising(encryptedData: ByteArray, mode: Int, txPower: Int) {
        if (advertiser == null) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(mode)
            .setTxPowerLevel(txPower)
            .setConnectable(false)
            .build()

        val payload = buildManufacturerPayload(encryptedData)
        val data = AdvertiseData.Builder()
            .addManufacturerData(mfgId, payload)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun buildManufacturerPayload(encryptedData: ByteArray): ByteArray {
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "BLE Advertiser Service", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, AdvertisingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId).setContentTitle("BLE Advertiser").setContentText(contentText).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(pendingIntent).build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        manager.notify(1, notification)
    }
}
