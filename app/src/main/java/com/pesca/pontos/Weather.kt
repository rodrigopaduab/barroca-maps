package com.pesca.pontos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * Clima de um lugar numa hora, pelo Open-Meteo (gratuito, sem chave).
 * Até 7 dias atrás usa a previsão com histórico recente; antes disso, o arquivo histórico.
 */
object Weather {
    data class Data(val tempC: Double?, val code: Int?, val windKmh: Double?, val humidity: Int?)

    private const val HOURLY = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"

    /** null = sem internet ou serviço fora do ar (tentar de novo depois). */
    suspend fun fetch(lat: Double, lng: Double, atMillis: Long): Data? = withContext(Dispatchers.IO) {
        try {
            val coords = String.format(Locale.US, "latitude=%.5f&longitude=%.5f", lat, lng)
            val ageDays = (System.currentTimeMillis() - atMillis) / 86_400_000L
            val url = if (ageDays < 7) {
                "https://api.open-meteo.com/v1/forecast?$coords&hourly=$HOURLY&past_days=7&forecast_days=1&timezone=UTC&timeformat=unixtime"
            } else {
                val day = Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC).toLocalDate()
                "https://archive-api.open-meteo.com/v1/archive?$coords&start_date=$day&end_date=$day&hourly=$HOURLY&timezone=UTC&timeformat=unixtime"
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return@withContext null
            val hourly = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            if (times.length() == 0) return@withContext null
            val target = atMillis / 1000
            var best = 0
            for (i in 0 until times.length()) {
                if (abs(times.getLong(i) - target) < abs(times.getLong(best) - target)) best = i
            }
            fun num(key: String): Double? = hourly.optJSONArray(key)?.let { if (it.isNull(best)) null else it.getDouble(best) }
            Data(
                tempC = num("temperature_2m"),
                code = num("weather_code")?.toInt(),
                windKmh = num("wind_speed_10m"),
                humidity = num("relative_humidity_2m")?.toInt(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Código WMO do clima em português. */
    fun describe(code: Int?): String = when (code) {
        null -> "—"
        0 -> "Céu limpo"
        1 -> "Poucas nuvens"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Garoa"
        61, 63, 66, 80, 81 -> "Chuva"
        65, 67, 82 -> "Chuva forte"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Tempestade"
        else -> "Clima"
    }

    fun summary(tempC: Double?, code: Int?, windKmh: Double?, humidity: Int?): String =
        listOfNotNull(
            describe(code).takeIf { code != null },
            tempC?.let { String.format(Locale("pt", "BR"), "%.0f °C", it) },
            windKmh?.let { String.format(Locale("pt", "BR"), "vento %.0f km/h", it) },
            humidity?.let { "umidade $it%" },
        ).joinToString(" · ").ifEmpty { "—" }
}
