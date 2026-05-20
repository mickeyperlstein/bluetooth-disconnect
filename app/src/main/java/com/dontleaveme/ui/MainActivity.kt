package com.dontleaveme.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.dontleaveme.databinding.ActivityMainBinding
import com.dontleaveme.service.BluetoothDeviceManager
import com.dontleaveme.service.WatchdogService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceManager: BluetoothDeviceManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) setupApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceManager = BluetoothDeviceManager(this)
        checkPermissionsAndSetup()
    }

    override fun onResume() {
        super.onResume()
        // Refresh device list in case pairings changed
        if (hasBtPermission()) refreshDeviceList()
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun hasBtPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else true

    private fun checkPermissionsAndSetup() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) setupApp() else permissionLauncher.launch(needed.toTypedArray())
    }

    // ── App setup ─────────────────────────────────────────────────────────────

    private fun setupApp() {
        startForegroundService(Intent(this, WatchdogService::class.java))
        refreshDeviceList()
        setupControls()
        loadSettings()
    }

    private fun refreshDeviceList() {
        val devices = deviceManager.getBondedDevices().sortedBy {
            try { it.name ?: it.address } catch (_: SecurityException) { it.address }
        }
        val watched = deviceManager.getWatchedAddresses()

        val adapter = DeviceAdapter(devices, watched) { address, isWatched ->
            deviceManager.setWatched(address, isWatched)
        }

        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            if (itemDecorationCount == 0) {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
            this.adapter = adapter
        }
    }

    private fun setupControls() {
        binding.buttonTest.setOnClickListener {
            startForegroundService(
                Intent(this, WatchdogService::class.java).apply {
                    action = WatchdogService.ACTION_TEST_ALARM
                }
            )
        }

        binding.buttonSilence.setOnClickListener {
            startForegroundService(
                Intent(this, WatchdogService::class.java).apply {
                    action = WatchdogService.ACTION_STOP_ALARM
                }
            )
        }

        binding.sliderGracePeriod.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                deviceManager.setGracePeriodMs(value.toLong() * 1000L)
                binding.textGracePeriod.text = "Grace period: ${value.toInt()}s"
            }
        }

        binding.switchAutoLearn.setOnCheckedChangeListener { _, checked ->
            deviceManager.setAutoLearn(checked)
        }
    }

    private fun loadSettings() {
        val seconds = (deviceManager.getGracePeriodMs() / 1000L).toFloat().coerceIn(10f, 120f)
        binding.sliderGracePeriod.value = seconds
        binding.textGracePeriod.text = "Grace period: ${seconds.toInt()}s"
        binding.switchAutoLearn.isChecked = deviceManager.isAutoLearnEnabled()
    }
}
