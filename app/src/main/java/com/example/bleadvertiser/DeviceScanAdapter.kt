package com.example.bleadvertiser

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bleadvertiser.databinding.ListItemDeviceBinding

@SuppressLint("MissingPermission")
class DeviceScanAdapter(
    private val onDeviceClicked: (ScanResult) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<ScanResult>()
    private var selectedPosition = -1

    @SuppressLint("NotifyDataSetChanged")
    fun updateDevice(scanResult: ScanResult) {
        val existingDeviceIndex = devices.indexOfFirst { it.device.address == scanResult.device.address }
        if (existingDeviceIndex != -1) {
            devices[existingDeviceIndex] = scanResult
            notifyItemChanged(existingDeviceIndex)
        } else {
            devices.add(scanResult)
            devices.sortByDescending { it.rssi }
            notifyDataSetChanged() // Sıralama değiştiği için tüm listeyi güncellemek daha kolay
        }
    }

    fun clearDevices() {
        val size = devices.size
        devices.clear()
        selectedPosition = -1
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ListItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val result = devices[position]
        holder.bind(result, position == selectedPosition)
        holder.itemView.setOnClickListener {
            val previousSelectedPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (previousSelectedPosition != -1) {
                notifyItemChanged(previousSelectedPosition)
            }
            notifyItemChanged(selectedPosition)
            onDeviceClicked(result)
        }
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(private val binding: ListItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(result: ScanResult, isSelected: Boolean) {
            binding.tvDeviceName.text = result.device.name ?: "Bilinmeyen Cihaz"
            binding.tvDeviceAddress.text = result.device.address
            binding.tvDeviceRssi.text = "${result.rssi} dBm"
            binding.radioButton.isChecked = isSelected
        }
    }
}
