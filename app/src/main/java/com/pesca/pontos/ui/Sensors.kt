package com.pesca.pontos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pesca.pontos.trail.P

/** Posição do GPS atualizada enquanto a tela estiver aberta. fast = a cada 2 s (seguindo/gravando). */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocationUpdates(enabled: Boolean, fast: Boolean): State<P?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<P?>(null) }
    DisposableEffect(enabled, fast) {
        if (!enabled) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val req = LocationRequest.Builder(
            if (fast) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if (fast) 2000L else 15_000L,
        ).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { state.value = P(it.latitude, it.longitude) }
            }
        }
        try {
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // sem permissão
        }
        onDispose { client.removeLocationUpdates(cb) }
    }
    return state
}

/** Direção para onde o celular aponta (graus, 0 = norte). null quando desligado ou sem sensor. */
@Composable
fun rememberHeading(enabled: Boolean): State<Float?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sensor == null) {
            state.value = null
            return@DisposableEffect onDispose { }
        }
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        var smooth: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val deg = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
                // suaviza (evita o mapa tremendo), tratando a virada 359° -> 0°
                val prev = smooth
                smooth = if (prev == null) deg else {
                    val diff = ((deg - prev + 540f) % 360f) - 180f
                    (prev + diff * 0.15f + 360f) % 360f
                }
                state.value = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
