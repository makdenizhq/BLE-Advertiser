package com.example.bleadvertiser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.bleadvertiser.databinding.FragmentSetupBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var userDataStore: UserDataStore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        binding.root.isVisible = false // Hide UI until check is complete
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userDataStore = UserDataStore(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val user = userDataStore.userData.first()
            when {
                user.ownerToken?.isNotBlank() == true -> {
                    findNavController().navigate(R.id.action_setupFragment_to_ownerFragment)
                }
                user.role == Role.RESIDENT -> {
                    findNavController().navigate(R.id.action_setupFragment_to_residentFragment)
                }
                else -> {
                    binding.root.isVisible = true
                    setupUI()
                }
            }
        }
    }

    private fun setupUI() {
        setupSpinner()
        binding.btnSaveIdentity.setOnClickListener {
            saveAndNavigate()
        }
    }

    private fun setupSpinner() {
        val roles = Role.entries.filter { it != Role.NONE }.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRole.adapter = adapter
    }

    private fun saveAndNavigate() {
        val name = binding.etName.text.toString().trim()
        val apartment = binding.etApartment.text.toString().trim()
        val selectedRole = Role.valueOf(binding.spinnerRole.selectedItem.toString())

        if (name.isEmpty() || apartment.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.setup_error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userDataStore.saveIdentity(name, apartment, selectedRole)
            when (selectedRole) {
                Role.RESIDENT -> findNavController().navigate(R.id.action_setupFragment_to_residentFragment)
                Role.OWNER -> findNavController().navigate(R.id.action_setupFragment_to_ownerFragment)
                Role.NONE -> { /* Should not happen */ }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
