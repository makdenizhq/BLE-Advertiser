package com.example.bleadvertiser

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bleadvertiser.databinding.FragmentResidentBinding
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class ResidentFragment : Fragment(), LocationListener {

    private var _binding: FragmentResidentBinding? = null
    private val binding get() = _binding!!

    private lateinit var locationManager: LocationManager
    private lateinit var geofencingClient: com.google.android.gms.location.GeofencingClient
    private lateinit var userDataStore: UserDataStore

    private var isGeofenceSet = false
    private val requiredAccuracyMeters = 2.5f
    private val geofenceId = "MyGeofence"
    private val geofenceRadiusInMeters = 50f
    private var qrBitmap: Bitmap? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                startGpsUpdates()
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_permissions_required), Toast.LENGTH_LONG).show()
            }
        }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResidentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        geofencingClient = LocationServices.getGeofencingClient(requireActivity())
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        userDataStore = UserDataStore(requireContext())

        setupSpinners()
        loadAndDisplayUserData()
        checkPermissionsAndStartGps()

        binding.btnSaveAndQr.setOnClickListener { saveDataAndGenerateQr() }
        binding.btnStart.setOnClickListener { startManualAdvertising() }
        binding.btnStop.setOnClickListener { stopAdvertising() }
        binding.btnResetGeofence.setOnClickListener { resetGeofence() }
        binding.btnShareQr.setOnClickListener { shareQrCode() }
    }

    private fun checkPermissionsAndStartGps() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest)
        } else {
            startGpsUpdates()
        }
    }

    private fun startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            binding.tvGpsStatus.text = getString(R.string.status_permissions_denied)
            return
        }
        if (isGeofenceSet) {
            binding.tvGpsStatus.text = getString(R.string.status_geofence_set_before)
        } else {
            binding.tvGpsStatus.text = getString(R.string.status_searching_location)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val accuracy = location.accuracy
        binding.tvGpsAccuracy.text = getString(R.string.accuracy_format, accuracy)

        if (!isGeofenceSet && accuracy <= requiredAccuracyMeters) {
            binding.tvGpsStatus.text = getString(R.string.status_location_saved)
            Toast.makeText(requireContext(), getString(R.string.resident_geofence_set_success), Toast.LENGTH_LONG).show()
            addGeofence(location.latitude, location.longitude)
            locationManager.removeUpdates(this)
        } else if (!isGeofenceSet) {
            binding.tvGpsStatus.text = getString(R.string.status_improving_accuracy)
        }
    }

    private fun addGeofence(latitude: Double, longitude: Double) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, geofenceRadiusInMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
            addOnSuccessListener {
                isGeofenceSet = true
                viewLifecycleOwner.lifecycleScope.launch { userDataStore.saveGeofenceStatus(true) }
            }
            addOnFailureListener { binding.tvGpsStatus.text = getString(R.string.status_geofence_failed) }
        }
    }

    private fun resetGeofence() {
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                isGeofenceSet = false
                viewLifecycleOwner.lifecycleScope.launch { userDataStore.saveGeofenceStatus(false) }
                binding.tvGpsAccuracy.text = ""
                Toast.makeText(requireContext(), getString(R.string.resident_geofence_reset_success), Toast.LENGTH_SHORT).show()
                startGpsUpdates()
            }
            addOnFailureListener { Toast.makeText(requireContext(), getString(R.string.resident_geofence_reset_fail), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun saveDataAndGenerateQr() = viewLifecycleOwner.lifecycleScope.launch {
        val signalId = binding.etSignalId.text.toString()
        if (signalId.length != 6) {
            Toast.makeText(requireContext(), getString(R.string.resident_error_signal_id_length), Toast.LENGTH_SHORT).show()
            return@launch
        }

        val bleKey = generateRandomAlphanumericKey()
        userDataStore.saveResidentData(signalId, bleKey)

        val user = userDataStore.userData.first()
        val qrData = "$bleKey|${user.apartment}|${user.name}|RESIDENT"
        try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(qrData, BarcodeFormat.QR_CODE, 400, 400)
            val width = bitMatrix.width
            val height = bitMatrix.height
            qrBitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    qrBitmap?.set(x, y, if (bitMatrix[x, y]) -0x1000000 else -0x1)
                }
            }
            binding.ivQrCode.setImageBitmap(qrBitmap)
            binding.ivQrCode.visibility = View.VISIBLE
            binding.btnShareQr.visibility = View.VISIBLE
            Toast.makeText(requireContext(), getString(R.string.resident_qr_success), Toast.LENGTH_LONG).show()
            binding.btnSaveAndQr.isEnabled = false 
        } catch (_: Exception) {
            Toast.makeText(requireContext(), getString(R.string.resident_qr_fail), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareQrCode() {
        val bitmap = qrBitmap ?: return
        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/image.jpeg")
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.close()

            val imageFile = File(cachePath, "image.jpeg")
            val contentUri: Uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imageFile)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, requireContext().contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }
            startActivity(Intent.createChooser(shareIntent, "QR Kodu Paylaş"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startManualAdvertising() = viewLifecycleOwner.lifecycleScope.launch {
        val user = userDataStore.userData.first()
        val bleKey = user.bleKey
        val signalId = user.signalId

        if (bleKey == null || signalId == null) {
            Toast.makeText(requireContext(), getString(R.string.resident_error_no_key), Toast.LENGTH_LONG).show()
            return@launch
        }

        val txPowerPos = binding.spinnerTxPower.selectedItemPosition
        val advModePos = binding.spinnerAdvMode.selectedItemPosition
        userDataStore.saveAdvSettings(txPowerPos, advModePos)

        val plainText = "${user.apartment}|$signalId"
        val serviceIntent = Intent(requireContext(), BleAdvertiserService::class.java).apply {
            putExtra("encryptedData", encryptData(plainText, bleKey))
            putExtra("txPower", getTxPowerFromPosition(txPowerPos))
            putExtra("advMode", getAdvModeFromPosition(advModePos))
        }
        requireContext().startService(serviceIntent)
        
        binding.tvCurrentTxPower.text = getString(R.string.resident_current_power, binding.spinnerTxPower.selectedItem.toString())
        binding.tvCurrentAdvMode.text = getString(R.string.resident_current_period, binding.spinnerAdvMode.selectedItem.toString())
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
        Toast.makeText(requireContext(), getString(R.string.resident_broadcast_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopAdvertising() {
        requireContext().stopService(Intent(requireContext(), BleAdvertiserService::class.java))
        binding.tvCurrentTxPower.text = ""
        binding.tvCurrentAdvMode.text = ""
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
        Toast.makeText(requireContext(), getString(R.string.resident_broadcast_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun setupSpinners() {
        ArrayAdapter.createFromResource(requireContext(), R.array.tx_power_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerTxPower.adapter = adapter
            }
        ArrayAdapter.createFromResource(requireContext(), R.array.adv_mode_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerAdvMode.adapter = adapter
            }
    }

    private fun loadAndDisplayUserData() = viewLifecycleOwner.lifecycleScope.launch {
        val user = userDataStore.userData.first()
        binding.tvInfo.text = getString(R.string.resident_info_format, user.name, user.apartment)
        
        val signalId = user.signalId ?: Random.nextInt(100000, 999999).toString()
        binding.etSignalId.setText(signalId)

        isGeofenceSet = user.isGeofenceSet
        
        binding.spinnerTxPower.setSelection(user.txPowerPos)
        binding.spinnerAdvMode.setSelection(user.advModePos)
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

    private fun generateRandomAlphanumericKey(): String {
        val length = 18
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { allowedChars[Random.nextInt(0, allowedChars.length)] }.joinToString("")
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStartGps()
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
