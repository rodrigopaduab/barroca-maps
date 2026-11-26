package com.pesca.pontos.trail

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pesca.pontos.R
import com.pesca.pontos.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Marcação feita durante a gravação (ainda não salva no banco). */
data class PendingMark(val p: P, val type: String, val label: String, val at: Long = System.currentTimeMillis())

/** Estado da gravação em andamento. */
data class Recording(
    val points: List<P> = emptyList(),
    val marks: List<PendingMark> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val paused: Boolean = false,
    val pausedSince: Long? = null,
    val pausedTotalMs: Long = 0,
    val distanceM: Double = 0.0,
) {
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        now - startedAt - pausedTotalMs - (pausedSince?.let { now - it } ?: 0)
}

/** Guarda a gravação em memória; o serviço alimenta com o GPS. */
object TrackRecorder {
    private val _state = MutableStateFlow<Recording?>(null)
    val state: StateFlow<Recording?> = _state

    /** Último ponto do GPS (mesmo pausado), para colocar marcações. */
    @Volatile var lastFix: P? = null

    fun start(context: Context) {
        if (_state.value != null) return
        _state.value = Recording()
        val i = Intent(context, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
    }

    fun togglePause() {
        val r = _state.value ?: return
        val now = System.currentTimeMillis()
        _state.value = if (r.paused) {
            r.copy(paused = false, pausedSince = null, pausedTotalMs = r.pausedTotalMs + (now - (r.pausedSince ?: now)))
        } else {
            r.copy(paused = true, pausedSince = now)
        }
    }

    fun addMark(type: String, label: String): Boolean {
        val r = _state.value ?: return false
        val p = lastFix ?: r.points.lastOrNull() ?: return false
        _state.value = r.copy(marks = r.marks + PendingMark(p, type, label))
        return true
    }

    internal fun onFix(p: P, accuracy: Float) {
        lastFix = p
        val r = _state.value ?: return
        if (r.paused || accuracy > 35f) return
        val last = r.points.lastOrNull()
        if (last == null) {
            _state.value = r.copy(points = listOf(p))
            return
        }
        val d = Geo.dist(last, p)
        if (d >= 5.0) _state.value = r.copy(points = r.points + p, distanceM = r.distanceM + d)
    }

    /** Encerra a gravação e devolve o que foi gravado. */
    fun stop(context: Context): Recording? {
        val r = _state.value
        _state.value = null
        context.stopService(Intent(context, RecordingService::class.java))
        return r
    }
}

/** Serviço em primeiro plano: mantém o GPS gravando com a tela desligada. */
class RecordingService : Service() {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { TrackRecorder.onFix(P(it.latitude, it.longitude), it.accuracy) }
            updateNotification()
        }
    }
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("Gravando caminho..."), type)
        } catch (e: Exception) {
            // sem permissão de localização: não dá para gravar
            TrackRecorder.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(4f)
            .build()
        try {
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        super.onDestroy()
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 10_000) return
        lastNotify = now
        val r = TrackRecorder.state.value ?: return
        val text = "${Geo.formatDistance(r.distanceM)} · ${r.elapsedMs(now) / 60_000} min" + if (r.paused) " · pausado" else ""
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Ceva está gravando o caminho")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Gravação de caminho", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL = "gravacao"
        private const val NOTIF_ID = 42
    }
}
