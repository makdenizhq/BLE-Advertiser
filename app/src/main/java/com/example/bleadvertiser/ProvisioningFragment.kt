package com.example.bleadvertiser

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.bleadvertiser.databinding.FragmentProvisioningBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
class ProvisioningFragment : Fragment() {

    private var _binding: FragmentProvisioningBinding? = null
    private val binding get() = _binding!!

    private lateinit var userDataStore: UserDataStore
    private val tokenManager = TokenManager()
    private var qrData: String? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())

    private lateinit var deviceScanAdapter: DeviceScanAdapter
    private var selectedDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                startBleScan()
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_permissions_required), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            qrData = it.getString("qrData")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProvisioningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userDataStore = UserDataStore(requireContext())

        setupRecyclerView()
        parseQrData()

        binding.btnProvision.setOnClickListener {
            provisionSelectedDevice()
        }

        checkBlePermissionsAndStartScan()
    }

    private fun setupRecyclerView() {
        deviceScanAdapter = DeviceScanAdapter { scanResult ->
            selectedDevice = scanResult.device
            binding.btnProvision.isEnabled = true
        }
        binding.rvBleDevices.adapter = deviceScanAdapter
    }

    private fun parseQrData() {
        val data = qrData ?: return
        val parts = data.split('|')
        if (parts.size == 4) {
            val daire = parts[1]
            val ad = parts[2]
            val yakinlik = parts[3]
            binding.tvResidentInfo.text = getString(R.string.provisioning_resident_info_format, ad, daire, yakinlik)
        } else {
            binding.tvResidentInfo.text = getString(R.string.provisioning_invalid_qr)
        }
    }

    private fun checkBlePermissionsAndStartScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest)
        } else {
            startBleScan()
        }
    }

    private fun startBleScan() {
        if (isScanning) return
        binding.progressBar.visibility = View.VISIBLE
        deviceScanAdapter.clearDevices()

        val scanFilter = ScanFilter.Builder().build()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        isScanning = true
        scanHandler.postDelayed({ stopBleScan() }, 10000)
    }

    private fun stopBleScan() {
        if (isScanning) {
            bleScanner.stopScan(scanCallback)
            isScanning = false
            binding.progressBar.visibility = View.GONE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            activity?.runOnUiThread {
                deviceScanAdapter.updateDevice(result)
            }
        }
    }

    private fun provisionSelectedDevice() {
        val device = selectedDevice ?: return
        stopBleScan()
        binding.progressBar.visibility = View.VISIBLE
        val deviceName = if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) "Cihaza" else device.name
        Toast.makeText(requireContext(), getString(R.string.provisioning_connecting_to_device, deviceName), Toast.LENGTH_SHORT).show()
        device.connectGatt(requireContext(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                activity?.runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.provisioning_connection_lost), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(Constants.PROVISIONING_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(Constants.ADD_RESIDENT_CHAR_UUID)
            if (characteristic == null) {
                activity?.runOnUiThread { Toast.makeText(requireContext(), getString(R.string.provisioning_service_not_found), Toast.LENGTH_LONG).show() }
                gatt.disconnect()
                return
            }
            writeProvisioningData(gatt, characteristic)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            activity?.runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(requireContext(), getString(R.string.provisioning_write_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.provisioning_write_fail, status), Toast.LENGTH_LONG).show()
                }
                gatt.disconnect()
                findNavController().popBackStack()
            }
        }
    }

    private fun writeProvisioningData(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userDataStore.userData.first()
            val token = user.ownerToken
            if (token == null) {
                activity?.runOnUiThread { Toast.makeText(requireContext(), getString(R.string.provisioning_token_not_found), Toast.LENGTH_LONG).show() }
                gatt.disconnect()
                return@launch
            }

            val scope = tokenManager.parseTokenForScope(token)
            val qrParts = qrData?.split('|')
            val residentDaire = qrParts?.getOrNull(1)

            if (scope == null || residentDaire == null || !scope.authorizedApartments.contains(residentDaire)) {
                activity?.runOnUiThread { Toast.makeText(requireContext(), getString(R.string.provisioning_unauthorized_apartment), Toast.LENGTH_LONG).show() }
                gatt.disconnect()
                return@launch
            }

            val command = "ADD_RESIDENT|$token|$qrData"
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, command.toByteArray(Charsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = command.toByteArray(Charsets.UTF_8)
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBleScan()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        _binding = null
    }
}
