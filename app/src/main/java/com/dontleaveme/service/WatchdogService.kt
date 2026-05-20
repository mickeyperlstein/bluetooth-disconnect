package com.dontleaveme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dontleaveme.R
import com.dontleaveme.ui.MainActivity

// v2 imports — uncomment when activating personality / accelerometer features:
// import android.hardware.Sensor
// import android.hardware.SensorEvent
// import android.hardware.SensorEventListener
// import android.hardware.SensorManager
// import android.os.Handler
// import android.os.Looper
// import android.os.PowerManager
// import com.dontleaveme.audio.EscalationStage
// import com.dontleaveme.audio.PhonePersonality
// import kotlin.math.abs
// import kotlin.math.sqrt

class WatchdogService : Service() {
// v2: class WatchdogService : Service(), SensorEventListener {

    companion object {
        const val ACTION_STOP_ALARM = "com.dontleaveme.STOP_ALARM"
        const val ACTION_TEST_ALARM = "com.dontleaveme.TEST_ALARM"
        private const val CHANNEL_PERSISTENT = "dontleaveme_persistent"
        private const val CHANNEL_ALERT = "dontleaveme_alert"
        private const val NOTIF_PERSISTENT = 1
        private const val NOTIF_ALERT = 2

        // v2 constants:
        // private const val MOTION_THRESHOLD = 1.5f       // m/s² delta from gravity
        // private const val ESCALATION_INTERVAL_MS = 25_000L
    }

    // ── MVP state ─────────────────────────────────────────────────────────────

    private lateinit var deviceManager: BluetoothDeviceManager
    private val audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var isAlarming = false

    // ── v2 state — uncomment for personality + accelerometer ─────────────────
    // private lateinit var personality: PhonePersonality
    // private lateinit var sensorManager: SensorManager
    // private val handler = Handler(Looper.getMainLooper())
    // private var wakeLock: PowerManager.WakeLock? = null
    //
    // var isAbandoned = false;  private set
    // var isGracePeriodRunning = false; private set
    // var isStationary = false; private set
    // private var currentStage = EscalationStage.SIGHING
    // private var pendingAbandonCheck = false
    //
    // private val gracePeriodExpired = Runnable {
    //     isGracePeriodRunning = false
    //     if (isStationary) startAlarm() else pendingAbandonCheck = true
    // }
    //
    // private val escalate = object : Runnable {
    //     override fun run() {
    //         if (!isAbandoned) return
    //         personality.speak(currentStage)
    //         currentStage = currentStage.next()
    //         handler.postDelayed(this, ESCALATION_INTERVAL_MS)
    //     }
    // }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        deviceManager = BluetoothDeviceManager(this)
        createNotificationChannels()
        startForeground(NOTIF_PERSISTENT, buildPersistentNotification())

        // v2: init personality, sensor, wake lock:
        // personality = PhonePersonality(this)
        // sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // sensorManager.registerListener(this,
        //     sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        //     SensorManager.SENSOR_DELAY_NORMAL)
        // acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { dispatch(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        // v2: sensorManager.unregisterListener(this)
        // v2: handler.removeCallbacksAndMessages(null)
        // v2: personality.shutdown()
        // v2: wakeLock?.release()
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
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                if (device != null && deviceManager.isWatched(device)) onBtDisconnected()
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                if (device != null && deviceManager.isWatched(device)) onBtConnected()
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_TEST_ALARM -> startAlarm()
        }
    }

    // ── Bluetooth events ──────────────────────────────────────────────────────

    private fun onBtDisconnected() {
        startAlarm()
        // v2: replace with grace period — startGracePeriod()
        // v2: auto-learn handled in dispatch before this call
    }

    private fun onBtConnected() {
        stopAlarm()
        // v2: replace with:
        // pendingAbandonCheck = false
        // when {
        //     isAbandoned        -> { stopAlarm(); personality.greetReturn() }
        //     isGracePeriodRunning -> { cancelGracePeriod(); personality.greetFalseAlarm() }
        // }
    }

    // ── Alarm (MVP: system ringtone) ──────────────────────────────────────────

    private fun startAlarm() {
        if (isAlarming) return
        isAlarming = true

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@WatchdogService, uri)
            isLooping = true
            prepare()
            start()
        }

        // v2: replace MediaPlayer block above with:
        // isAbandoned = true
        // currentStage = EscalationStage.SIGHING
        // personality.reset()
        // handler.post(escalate)

        showAlertNotification()
    }

    private fun stopAlarm() {
        isAlarming = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // v2: also:
        // isAbandoned = false; isGracePeriodRunning = false; pendingAbandonCheck = false
        // handler.removeCallbacks(escalate); handler.removeCallbacks(gracePeriodExpired)
        // personality.silence()

        getSystemService(NotificationManager::class.java).cancel(NOTIF_ALERT)
    }

    // ── v2: grace period, accelerometer, wake lock ────────────────────────────
    // private fun startGracePeriod() {
    //     if (isGracePeriodRunning || isAbandoned) return
    //     isGracePeriodRunning = true
    //     handler.postDelayed(gracePeriodExpired, deviceManager.getGracePeriodMs())
    // }
    //
    // private fun cancelGracePeriod() {
    //     isGracePeriodRunning = false
    //     handler.removeCallbacks(gracePeriodExpired)
    // }
    //
    // override fun onSensorChanged(event: SensorEvent) {
    //     if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
    //     val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
    //     isStationary = abs(sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH) < MOTION_THRESHOLD
    //     if (pendingAbandonCheck && isStationary) { pendingAbandonCheck = false; startAlarm() }
    // }
    //
    // override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    //
    // private fun acquireWakeLock() {
    //     val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    //     wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DontLeaveMe::Watchdog").also { it.acquire() }
    // }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PERSISTENT, "Watchdog Status", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Device Disconnected", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
        )
    }

    private fun buildPersistentNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_PERSISTENT)
            .setContentTitle("Don't Leave Me")
            .setContentText("Watching your Bluetooth devices")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun showAlertNotification() {
        val silence = PendingIntent.getService(
            this, 0,
            Intent(this, WatchdogService::class.java).apply { action = ACTION_STOP_ALARM },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("Device disconnected!")
            .setContentText("Tap Silence to stop the alarm.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Silence", silence)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT, n)
    }
}
