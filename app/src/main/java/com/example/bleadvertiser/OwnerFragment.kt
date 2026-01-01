package com.example.bleadvertiser

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.bleadvertiser.databinding.FragmentOwnerBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OwnerFragment : Fragment() {

    private var _binding: FragmentOwnerBinding? = null
    private val binding get() = _binding!!

    private lateinit var userDataStore: UserDataStore
    private val tokenManager = TokenManager()
    private val qrUtils by lazy { QrUtils(requireContext()) }
    private var currentScanRequest: String? = null

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val qrText = qrUtils.decodeQrFromUri(it)
            if (qrText != null) {
                handleQrResult(qrText)
            } else {
                Toast.makeText(requireContext(), "Dosyadan QR kodu okunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("qr_scan_result") { _, bundle ->
            val qrText = bundle.getString("qr_text")
            if (qrText != null) {
                handleQrResult(qrText)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOwnerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userDataStore = UserDataStore(requireContext())

        loadAndDisplayData()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnScanTokenLocked.setOnClickListener {
            currentScanRequest = "TOKEN_SCAN"
            findNavController().navigate(R.id.action_ownerFragment_to_qrScannerFragment)
        }
        binding.btnLoadTokenFromFileLocked.setOnClickListener {
            currentScanRequest = "TOKEN_SCAN"
            openDocumentLauncher.launch(arrayOf("image/jpeg", "image/png"))
        }

        binding.btnScanResidentQr.setOnClickListener {
            currentScanRequest = "RESIDENT_SCAN"
            findNavController().navigate(R.id.action_ownerFragment_to_qrScannerFragment)
        }
        binding.btnLoadResidentFromFile.setOnClickListener {
            currentScanRequest = "RESIDENT_SCAN"
            openDocumentLauncher.launch(arrayOf("image/jpeg", "image/png"))
        }
        
        binding.btnDeleteToken.setOnClickListener {
            deleteToken()
        }
        
        binding.btnSaveToken.setOnClickListener {
            val token = binding.etOwnerToken.text.toString().trim()
            if (token.isNotBlank()) {
                saveAndParseToken(token)
            }
        }
    }

    private fun handleQrResult(qrText: String) {
        when (currentScanRequest) {
            "TOKEN_SCAN" -> {
                if (qrText.startsWith("DOORSYS_OWNER_TOKEN|v1|")) {
                    val token = qrText.substringAfter("DOORSYS_OWNER_TOKEN|v1|")
                    binding.etOwnerToken.setText(token)
                    Toast.makeText(requireContext(), "Token alana yerleştirildi. Kaydetmek için butona basın.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Geçersiz Owner Token formatı.", Toast.LENGTH_SHORT).show()
                }
            }
            "RESIDENT_SCAN" -> {
                val bundle = bundleOf("qrData" to qrText)
                findNavController().navigate(R.id.action_ownerFragment_to_provisioningFragment, bundle)
            }
        }
    }

    private fun loadAndDisplayData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val user = userDataStore.userData.first()
            binding.tvOwnerInfo.text = getString(R.string.owner_info_format, user.name, user.apartment)

            val hasToken = user.ownerToken?.isNotBlank() == true
            if (hasToken) {
                binding.etOwnerToken.setText(user.ownerToken)
                parseAndDisplayScope(user.ownerToken!!)
            }
            updateUiBasedOnToken(hasToken)
        }
    }

    private fun saveAndParseToken(token: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            userDataStore.saveOwnerToken(token)
            parseAndDisplayScope(token)
            updateUiBasedOnToken(true)
            Toast.makeText(requireContext(), getString(R.string.owner_token_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteToken() {
        viewLifecycleOwner.lifecycleScope.launch {
            userDataStore.saveOwnerToken("") // Token'ı temizle
            binding.etOwnerToken.setText("")
            binding.tvScopeList.text = ""
            updateUiBasedOnToken(false)
            Toast.makeText(requireContext(), "Token silindi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseAndDisplayScope(token: String) {
        val scope = tokenManager.parseTokenForScope(token)
        if (scope != null) {
            binding.tvScopeList.text = scope.authorizedApartments.joinToString(", ")
        } else {
            binding.tvScopeList.text = getString(R.string.owner_token_invalid)
            Toast.makeText(requireContext(), getString(R.string.owner_token_fail), Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateUiBasedOnToken(hasToken: Boolean) {
        binding.cardLocked.isVisible = !hasToken
        binding.groupProvisioning.visibility = if (hasToken) View.VISIBLE else View.GONE // Düzeltilen satır
        binding.btnDeleteToken.isVisible = hasToken
        binding.btnSaveToken.isVisible = !hasToken
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
