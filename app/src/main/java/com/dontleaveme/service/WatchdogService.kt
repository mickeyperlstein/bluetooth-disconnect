package com.dontleaveme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.dontleaveme.R
import com.dontleaveme.audio.EscalationStage
import com.dontleaveme.audio.PhonePersonality
import com.dontleaveme.ui.MainActivity
import kotlin.math.abs
import kotlin.math.sqrt

class WatchdogService : Service(), SensorEventListener {

    companion object {
        const val ACTION_STOP_ALARM = "com.dontleaveme.STOP_ALARM"
        const val ACTION_TEST_ALARM = "com.dontleaveme.TEST_ALARM"

        private const val CHANNEL_PERSISTENT = "dontleaveme_persistent"
        private const val CHANNEL_ALERT = "dontleaveme_alert"
        private const val NOTIF_PERSISTENT = 1
        private const val NOTIF_ALERT = 2

        // m/s² delta from gravity — below this the phone is considered stationary
        private const val MOTION_THRESHOLD = 1.5f

        private const val ESCALATION_INTERVAL_MS = 25_000L
    }

    // ── State ─────────────────────────────────────────────────────────────────

    var isAbandoned = false
        private set
    var isGracePeriodRunning = false
        private set
    var isStationary = false
        private set

    private var currentStage = EscalationStage.SIGHING

    // Grace period expired but phone was moving — wait for it to settle
    private var pendingAbandonCheck = false

    // ── Dependencies ──────────────────────────────────────────────────────────

    private lateinit var personality: PhonePersonality
    private lateinit var deviceManager: BluetoothDeviceManager
    private lateinit var sensorManager: SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Runnables ─────────────────────────────────────────────────────────────

    private val gracePeriodExpired = Runnable {
        isGracePeriodRunning = false
        if (isStationary) startAlarm() else pendingAbandonCheck = true
    }

    private val escalate = object : Runnable {
        override fun run() {
            if (!isAbandoned) return
            personality.speak(currentStage)
            currentStage = currentStage.next()
            handler.postDelayed(this, ESCALATION_INTERVAL_MS)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        personality = PhonePersonality(this)
        deviceManager = BluetoothDeviceManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        createNotificationChannels()
        startForeground(NOTIF_PERSISTENT, buildPersistentNotification())

        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { dispatch(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        personality.shutdown()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Intent dispatch ───────────────────────────────────────────────────────

    private fun dispatch(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { onBtDisconnected(it) }
            BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { onBtConnected(it) }
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_TEST_ALARM -> personality.speak(EscalationStage.SIGHING)
        }
    }

    // ── Bluetooth events ──────────────────────────────────────────────────────

    private fun onBtDisconnected(device: BluetoothDevice) {
        if (!deviceManager.isWatched(device)) {
            if (deviceManager.isAutoLearnEnabled()) deviceManager.addWatchedDevice(device)
            else return
        }
        if (isGracePeriodRunning || isAbandoned) return
        startGracePeriod()
    }

    private fun onBtConnected(device: BluetoothDevice) {
        if (!deviceManager.isWatched(device)) return
        pendingAbandonCheck = false
        when {
            isAbandoned -> {
                stopAlarm()
                personality.greetReturn()
            }
            isGracePeriodRunning -> {
                cancelGracePeriod()
                personality.greetFalseAlarm()
            }
        }
    }

    // ── Grace period ──────────────────────────────────────────────────────────

    private fun startGracePeriod() {
        isGracePeriodRunning = true
        handler.postDelayed(gracePeriodExpired, deviceManager.getGracePeriodMs())
    }

    private fun cancelGracePeriod() {
        isGracePeriodRunning = false
        handler.removeCallbacks(gracePeriodExpired)
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    private fun startAlarm() {
        isAbandoned = true
        currentStage = EscalationStage.SIGHING
        personality.reset()
        showAlertNotification()
        handler.post(escalate)
    }

    private fun stopAlarm() {
        isAbandoned = false
        isGracePeriodRunning = false
        pendingAbandonCheck = false
        handler.removeCallbacks(escalate)
        handler.removeCallbacks(gracePeriodExpired)
        personality.silence()
        dismissAlertNotification()
    }

    // ── Accelerometer ─────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        isStationary = abs(magnitude - SensorManager.GRAVITY_EARTH) < MOTION_THRESHOLD

        if (pendingAbandonCheck && isStationary) {
            pendingAbandonCheck = false
            startAlarm()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PERSISTENT, "Watchdog Status", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Persistent monitoring notification" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Abandoned Alert", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Alert when phone is left behind"
                    enableVibration(true)
                }
        )
    }

    private fun buildPersistentNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_PERSISTENT)
            .setContentTitle("Don't Leave Me")
            .setContentText("Watching. Always watching.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun showAlertNotification() {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WatchdogService::class.java).apply { action = ACTION_STOP_ALARM },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("Don't Leave Me")
            .setContentText("Yeah, sure. Just leave me here.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "I'm coming, sorry", stopIntent)
            .setAutoCancel(false)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT, notification)
    }

    private fun dismissAlertNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ALERT)
    }

    // ── Wake lock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DontLeaveMe::WatchdogWakeLock"
        ).also { it.acquire() }
    }
}
