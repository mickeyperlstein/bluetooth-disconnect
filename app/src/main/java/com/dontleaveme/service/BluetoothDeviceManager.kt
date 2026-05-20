package com.dontleaveme.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences

class BluetoothDeviceManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "dontleaveme_prefs"
        private const val KEY_WATCHED = "watched_devices"
        private const val KEY_GRACE_MS = "grace_period_ms"
        private const val KEY_AUTO_LEARN = "auto_learn"
        private const val DEFAULT_GRACE_MS = 30_000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val bluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    fun getBondedDevices(): Set<BluetoothDevice> = try {
        bluetoothAdapter?.bondedDevices ?: emptySet()
    } catch (_: SecurityException) {
        emptySet()
    }

    fun getWatchedAddresses(): Set<String> =
        prefs.getStringSet(KEY_WATCHED, emptySet()) ?: emptySet()

    fun isWatched(device: BluetoothDevice): Boolean =
        device.address in getWatchedAddresses()

    fun setWatched(address: String, watched: Boolean) {
        val current = getWatchedAddresses().toMutableSet()
        if (watched) current.add(address) else current.remove(address)
        prefs.edit().putStringSet(KEY_WATCHED, current).apply()
    }

    fun addWatchedDevice(device: BluetoothDevice) = setWatched(device.address, true)

    fun getGracePeriodMs(): Long = prefs.getLong(KEY_GRACE_MS, DEFAULT_GRACE_MS)

    fun setGracePeriodMs(ms: Long) {
        prefs.edit().putLong(KEY_GRACE_MS, ms).apply()
    }

    fun isAutoLearnEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_LEARN, false)

    fun setAutoLearn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LEARN, enabled).apply()
    }
}
