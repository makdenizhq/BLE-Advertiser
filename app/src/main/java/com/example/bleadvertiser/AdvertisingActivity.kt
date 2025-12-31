package com.example.bleadvertiser

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.bleadvertiser.databinding.ActivityAdvertisingBinding
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AdvertisingActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityAdvertisingBinding
    private lateinit var locationManager: LocationManager
    private lateinit var geofencingClient: com.google.android.gms.location.GeofencingClient

    private var isGeofenceSet = false
    private val REQUIRED_ACCURACY_METERS = 2.5f
    private val GEOFENCE_ID = "MyGeofence"
    private val GEOFENCE_RADIUS_IN_METERS = 50f
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvertisingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geofencingClient = LocationServices.getGeofencingClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupSpinners()
        loadSavedData()
        checkPermissionsAndStartGps()
        displayBleMacAddress()

        binding.btnSave.setOnClickListener { saveDataAndGenerateQr() }
        binding.btnStart.setOnClickListener { startManualAdvertising() }
        binding.btnStop.setOnClickListener { stopAdvertising() }
    }

    private fun checkPermissionsAndStartGps() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startGpsUpdates()
        }
    }

    private fun startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (isGeofenceSet) {
            binding.tvGpsStatus.text = "Durum: Coğrafi alan daha önce kaydedilmiş."
            updateBroadcastButtons(true)
        } else {
            binding.tvGpsStatus.text = "Durum: Konum aranıyor..."
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val accuracy = location.accuracy
        binding.tvGpsAccuracy.text = "Doğruluk: ${"%.1f".format(accuracy)}m"

        if (!isGeofenceSet && accuracy <= REQUIRED_ACCURACY_METERS) {
            binding.tvGpsStatus.text = "Durum: Konum yüksek doğrulukla kaydedildi!"
            Toast.makeText(this, "Coğrafi alan otomatik olarak kuruldu.", Toast.LENGTH_LONG).show()
            addGeofence(location.latitude, location.longitude)
            locationManager.removeUpdates(this)
        } else if (!isGeofenceSet) {
            binding.tvGpsStatus.text = "Durum: Daha iyi doğruluk için bekleniyor..."
        }
    }

    private fun addGeofence(latitude: Double, longitude: Double) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, GEOFENCE_RADIUS_IN_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
            addOnSuccessListener {
                isGeofenceSet = true
                getSharedPreferences("BleAdvertiserPrefs", MODE_PRIVATE).edit()
                    .putBoolean("GEOFENCE_SET", true)
                    .apply()
                updateBroadcastButtons(true)
            }
            addOnFailureListener { binding.tvGpsStatus.text = "Durum: Coğrafi alan kurulamadı." }
        }
    }

    private fun saveDataAndGenerateQr() {
        val apartment = binding.etApartment.text.toString()
        val name = binding.etName.text.toString()
        val relationship = binding.spinnerRelationship.selectedItem.toString()
        val signalId = binding.etSignalId.text.toString()

        if (apartment.isEmpty() || name.isEmpty() || signalId.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
            return
        }

        val bleKey = generateRandomAlphanumericKey(18)

        val sharedPref = getSharedPreferences("BleAdvertiserPrefs", Context.MODE_PRIVATE).edit()
        sharedPref.putString("APARTMENT", apartment)
        sharedPref.putString("NAME", name)
        sharedPref.putString("RELATIONSHIP", relationship)
        sharedPref.putString("SIGNAL_ID", signalId)
        sharedPref.putString("BLE_KEY", bleKey)
        sharedPref.apply()

        val qrData = "$bleKey|${apartment.padEnd(2).substring(0, 2)}|${name.padEnd(20).substring(0, 20)}|${relationship.padEnd(10).substring(0, 10)}"
        try {
            val bitMatrix = MultiFormatWriter().encode(qrData, BarcodeFormat.QR_CODE, 400, 400)
            binding.ivQrCode.setImageBitmap(BarcodeEncoder().createBitmap(bitMatrix))
            binding.ivQrCode.visibility = View.VISIBLE
            Toast.makeText(this, "Bilgiler ve yeni yayın anahtarı kaydedildi. QR üretildi.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "QR Kodu oluşturulurken hata oluştu.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startManualAdvertising() {
        val sharedPref = getSharedPreferences("BleAdvertiserPrefs", Context.MODE_PRIVATE)
        val apartment = sharedPref.getString("APARTMENT", null)
        val signalId = sharedPref.getString("SIGNAL_ID", null)
        val bleKey = sharedPref.getString("BLE_KEY", null)

        if (apartment == null || signalId == null || bleKey == null) {
            Toast.makeText(this, "Lütfen önce bilgileri 'Kaydet ve QR Üret' butonu ile kaydedin.", Toast.LENGTH_LONG).show()
            return
        }

        val txPowerPos = binding.spinnerTxPower.selectedItemPosition
        val advModePos = binding.spinnerAdvMode.selectedItemPosition
        val txPower = getTxPowerFromPosition(txPowerPos)
        val advMode = getAdvModeFromPosition(advModePos)

        // Ayarları otomatik yayın için de kaydet
        sharedPref.edit()
            .putInt("TX_POWER_POS", txPowerPos)
            .putInt("ADV_MODE_POS", advModePos)
            .apply()

        val plainText = "$apartment|$signalId"
        val serviceIntent = Intent(this, BleAdvertiserService::class.java).apply {
            putExtra("encryptedData", encryptData(plainText, bleKey))
            putExtra("txPower", txPower)
            putExtra("advMode", advMode)
        }
        startService(serviceIntent)

        // Anlık durum bilgilerini güncelle
        binding.tvCurrentTxPower.text = "Geçerli Güç: ${binding.spinnerTxPower.selectedItem}"
        binding.tvCurrentAdvMode.text = "Geçerli Periyot: ${binding.spinnerAdvMode.selectedItem}"
        Toast.makeText(this, "Yayın başlatıldı.", Toast.LENGTH_SHORT).show()
    }

    private fun stopAdvertising() {
        stopService(Intent(this, BleAdvertiserService::class.java))
        binding.tvCurrentTxPower.text = ""
        binding.tvCurrentAdvMode.text = ""
        Toast.makeText(this, "Yayın durduruldu.", Toast.LENGTH_SHORT).show()
    }

    private fun updateBroadcastButtons(isEnabled: Boolean) {
        binding.btnStart.isEnabled = isEnabled
        binding.btnStop.isEnabled = isEnabled
    }

    private fun setupSpinners() {
        ArrayAdapter.createFromResource(this, R.array.tx_power_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerTxPower.adapter = adapter
            }
        ArrayAdapter.createFromResource(this, R.array.adv_mode_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerAdvMode.adapter = adapter
            }
        val relationships = resources.getStringArray(R.array.relationship_options_for_qr)
        val relAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, relationships)
        relAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRelationship.adapter = relAdapter
    }

    private fun loadSavedData() {
        val sharedPref = getSharedPreferences("BleAdvertiserPrefs", Context.MODE_PRIVATE)
        binding.etApartment.setText(sharedPref.getString("APARTMENT", ""))
        binding.etName.setText(sharedPref.getString("NAME", ""))
        binding.etSignalId.setText(sharedPref.getString("SIGNAL_ID", ""))
        val savedRelationship = sharedPref.getString("RELATIONSHIP", "Eş")
        val relationships = resources.getStringArray(R.array.relationship_options_for_qr)
        val position = relationships.indexOf(savedRelationship)
        if (position >= 0) binding.spinnerRelationship.setSelection(position)

        binding.spinnerTxPower.setSelection(sharedPref.getInt("TX_POWER_POS", 0))
        binding.spinnerAdvMode.setSelection(sharedPref.getInt("ADV_MODE_POS", 0))

        isGeofenceSet = sharedPref.getBoolean("GEOFENCE_SET", false)
    }

    private fun displayBleMacAddress() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val macAddress = bluetoothManager.adapter.address
                binding.tvBleMacAddress.text = "BLE Adresi: $macAddress"
            } catch (e: Exception) {
                binding.tvBleMacAddress.text = "BLE Adresi: Alınamadı"
            }
        } else {
            binding.tvBleMacAddress.text = "BLE Adresi: İzin gerekli"
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

    private fun generateRandomAlphanumericKey(length: Int): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { allowedChars[SecureRandom().nextInt(allowedChars.length)] }.joinToString("")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startGpsUpdates()
                displayBleMacAddress() // İzin verildikten sonra MAC adresini tekrar göstermeyi dene
            } else {
                Toast.makeText(this, "Tüm izinler uygulamanın doğru çalışması için gereklidir.", Toast.LENGTH_LONG).show()
                binding.tvGpsStatus.text = "Durum: İzinler reddedildi."
            }
        }
    }
}
