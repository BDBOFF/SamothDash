package com.samoth.dash

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.text.DateFormat
import java.util.Date

data class BatteryState(val level: Int, val charging: Boolean, val temperature: Double) {
    val icon: String
        get() {
            val tens = (level / 10) * 10
            return when {
                charging -> "mdi:battery-charging"
                tens >= 100 -> "mdi:battery"
                tens == 0 -> "mdi:battery-outline"
                else -> "mdi:battery-$tens"
            }
        }
}

/** Tourne en permanence : envoie la batterie à HA à chaque changement et au moins chaque minute. */
class BatteryService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var client: HaClient
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var last: BatteryState? = null
    private var declared = false

    private val tick = object : Runnable {
        override fun run() {
            send(force = true)
            handler.postDelayed(this, PERIOD_MS)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            handler.post { send(force = false) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        client = HaClient(prefs)
        goForeground()
        thread = HandlerThread("battery").apply { start() }
        handler = Handler(thread.looper)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Réveil : l'alarme survit à la veille profonde, contrairement à la boucle d'une minute
        handler.post { send(force = true) }
        scheduleAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    private fun read(): BatteryState? {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale
        if (level < 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        // « En charge » = alimentée : à 100 %, le statut peut être FULL avec la prise toujours active
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        return BatteryState(level, charging, temp)
    }

    private fun send(force: Boolean) {
        val b = read() ?: return
        val prev = last
        if (!force && prev != null && prev.level == b.level && prev.charging == b.charging) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "samothdash:send")
        lock.acquire(30_000)
        try {
            client.send(b, declare = !declared)
            declared = true
            last = b
            prefs.lastSend = "${b.level} % · ${if (b.charging) "en charge" else "sur batterie"} · " +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        } catch (e: Exception) {
            // Réseau coupé ou HA en redémarrage : on retentera à la prochaine minute
            declared = false
            prefs.lastSend = "Échec : ${e.message ?: e.javaClass.simpleName}"
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private fun scheduleAlarm() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(this, WakeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + ALARM_MS, pi)
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Batterie", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Samoth Dash")
            .setContentText("Envoi de la batterie à Home Assistant")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val CHANNEL = "battery"
        private const val PERIOD_MS = 60_000L
        private const val ALARM_MS = 5 * 60_000L

        fun start(context: Context) {
            if (Prefs(context).refreshToken == null) return
            runCatching { context.startForegroundService(Intent(context, BatteryService::class.java)) }
        }
    }
}

/** Démarrage de la tablette, mise à jour de l'appli et réveil périodique. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BatteryService.start(context)
    }
}
