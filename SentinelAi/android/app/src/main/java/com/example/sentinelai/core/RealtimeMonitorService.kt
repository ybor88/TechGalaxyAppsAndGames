package com.example.sentinelai.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sentinelai.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class RealtimeEvent {
    data class FileScanned(val result: DetectionResult) : RealtimeEvent()
    data class ThreatBlocked(val result: DetectionResult) : RealtimeEvent()
}

/**
 * Real-Time Protection. Watches the app's own protected sandbox folder
 * (getExternalFilesDir/Protetta) with FileObserver and scans new/modified
 * files automatically. Android's scoped storage does not let a normal app
 * watch arbitrary system folders in real time — this protected folder is
 * the legitimate, always-available equivalent, and the UI names it clearly
 * rather than implying broader system access than Android actually permits.
 */
class RealtimeMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "sentinelai_realtime"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_THRESHOLD = "threshold"
        private const val SETTLE_DELAY_MS = 1200L

        private val _events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 20)
        val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

        var isRunning: Boolean = false
            private set

        fun protectedDir(context: Context): File {
            val dir = File(context.getExternalFilesDir(null), "Protetta")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    private var observer: FileObserver? = null
    private lateinit var db: Database
    private lateinit var quarantine: Quarantine
    private var threshold = 70
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // FileObserver can fire multiple events (CREATE + MODIFY) for a single
    // write; without this, both would race to read/quarantine the same file.
    private val pathsBeingHandled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        db = Database(applicationContext)
        quarantine = Quarantine(applicationContext, db)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        threshold = intent?.getIntExtra(EXTRA_THRESHOLD, 70) ?: 70
        startForeground(NOTIFICATION_ID, buildNotification())
        startWatching()
        isRunning = true
        return START_STICKY
    }

    private fun startWatching() {
        val dir = protectedDir(applicationContext)
        db.logEvent("info", "Protezione in tempo reale avviata su ${dir.absolutePath}")

        @Suppress("DEPRECATION")
        observer = object : FileObserver(dir.absolutePath, CREATE or MODIFY or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!pathsBeingHandled.add(path)) return // already being processed by another event
                val file = File(dir, path)
                serviceScope.launch {
                    try {
                        delay(SETTLE_DELAY_MS)
                        if (!file.exists() || !file.isFile) return@launch
                        handleFile(file)
                    } finally {
                        pathsBeingHandled.remove(path)
                    }
                }
            }
        }
        observer?.startWatching()
    }

    private suspend fun handleFile(file: File) {
        val scannable = ScannableFile(
            displayPath = file.absolutePath,
            name = file.name,
            size = file.length(),
            opener = { file.inputStream() }
        )
        val result = try {
            Scanner.scanFile(scannable)
        } catch (exc: Exception) {
            return
        }
        if (result.error.isNotEmpty()) return

        _events.emit(RealtimeEvent.FileScanned(result))

        if (result.isFlagged) {
            db.addDetection(result, source = "realtime")
            if (result.riskScore >= threshold) {
                try {
                    quarantine.quarantineFile(
                        originalPath = result.path,
                        threatName = result.threatName,
                        riskScore = result.riskScore,
                        opener = { file.inputStream() },
                        deleteOriginal = { file.delete() }
                    )
                    _events.emit(RealtimeEvent.ThreatBlocked(result))
                } catch (exc: Exception) {
                    db.logEvent("error", "Quarantena non riuscita per ${file.name}: ${exc.message}")
                }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protezione in tempo reale",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica persistente mentre SentinelAI monitora la cartella protetta"
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SentinelAI — Protezione attiva")
            .setContentText("Monitoraggio in tempo reale della cartella protetta")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        observer?.stopWatching()
        observer = null
        isRunning = false
        serviceJob.cancel()
        db.logEvent("info", "Protezione in tempo reale disattivata")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
