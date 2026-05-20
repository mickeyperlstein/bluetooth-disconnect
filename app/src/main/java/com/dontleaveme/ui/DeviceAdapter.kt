package com.dontleaveme.ui

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dontleaveme.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val devices: List<BluetoothDevice>,
    watchedAddresses: Set<String>,
    private val onToggle: (address: String, watched: Boolean) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val watchedSet = watchedAddresses.toMutableSet()

    inner class ViewHolder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        val name = try { device.name?.takeIf { it.isNotBlank() } ?: device.address }
                   catch (_: SecurityException) { device.address }

        with(holder.binding) {
            textDeviceName.text = name
            textDeviceAddress.text = device.address
            // Clear listener before updating state to avoid phantom callbacks
            checkboxWatch.setOnCheckedChangeListener(null)
            checkboxWatch.isChecked = device.address in watchedSet
            checkboxWatch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) watchedSet.add(device.address)
                else watchedSet.remove(device.address)
                onToggle(device.address, isChecked)
            }
        }
    }
}
