package com.dontleaveme.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

class BluetoothDeviceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("dontleaveme", Context.MODE_PRIVATE)

    private val bluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    fun getBondedDevices(): Set<BluetoothDevice> = try {
        bluetoothAdapter?.bondedDevices ?: emptySet()
    } catch (_: SecurityException) {
        emptySet()
    }

    fun isWatched(device: BluetoothDevice): Boolean = device.address in getWatchedAddresses()

    fun getWatchedAddresses(): Set<String> = prefs.getStringSet("watched", emptySet()) ?: emptySet()

    fun setWatched(address: String, watched: Boolean) {
        val current = getWatchedAddresses().toMutableSet()
        if (watched) current.add(address) else current.remove(address)
        prefs.edit().putStringSet("watched", current).apply()
    }

    // v2: used by WatchdogService for auto-learn on disconnect
    // fun addWatchedDevice(device: BluetoothDevice) = setWatched(device.address, true)

    // v2: grace period slider (10–120 s) stored here, read by WatchdogService.startGracePeriod()
    // fun getGracePeriodMs(): Long = prefs.getLong("grace_ms", 30_000L)
    // fun setGracePeriodMs(ms: Long) { prefs.edit().putLong("grace_ms", ms).apply() }

    // v2: auto-learn toggle — automatically watch any device that disconnects
    // fun isAutoLearnEnabled(): Boolean = prefs.getBoolean("auto_learn", false)
    // fun setAutoLearn(enabled: Boolean) { prefs.edit().putBoolean("auto_learn", enabled).apply() }
}
