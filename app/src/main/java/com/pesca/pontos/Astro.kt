package com.pesca.pontos

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sol, lua e tábua solunar calculados no próprio celular (funciona sem internet).
 * Fórmulas de baixa precisão (as mesmas da biblioteca SunCalc), erro de poucos minutos.
 */
object Astro {
    private const val RAD = PI / 180
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val E = RAD * 23.4397

    private fun toDays(ms: Long) = ms / DAY_MS - 0.5 + J1970 - J2000
    private fun ra(l: Double, b: Double) = atan2(sin(l) * cos(E) - tan(b) * sin(E), cos(l))
    private fun dec(l: Double, b: Double) = asin(sin(b) * cos(E) + cos(b) * sin(E) * sin(l))
    private fun altitude(h: Double, phi: Double, d: Double) = asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h))
    private fun sidereal(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw

    private class Coords(val ra: Double, val dec: Double, val dist: Double = 0.0)

    private fun sunCoords(d: Double): Coords {
        val m = RAD * (357.5291 + 0.98560028 * d)
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val l = m + c + RAD * 102.9372 + PI
        return Coords(ra(l, 0.0), dec(l, 0.0))
    }

    private fun moonCoords(d: Double): Coords {
        val l0 = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val l = l0 + RAD * 6.289 * sin(m)
        val b = RAD * 5.128 * sin(f)
        val dist = 385001 - 20905 * cos(m)
        return Coords(ra(l, b), dec(l, b), dist)
    }

    /** Altura (graus) do sol ou da lua acima do horizonte. */
    private fun alt(ms: Long, lat: Double, lng: Double, moon: Boolean): Double {
        val d = toDays(ms)
        val lw = RAD * -lng
        val phi = RAD * lat
        val c = if (moon) moonCoords(d) else sunCoords(d)
        return altitude(sidereal(d, lw) - c.ra, phi, c.dec) / RAD
    }

    data class MoonInfo(val fraction: Double, val phase: Double) {
        val name: String
            get() = when {
                phase < 0.03 || phase > 0.97 -> "Lua nova"
                phase < 0.22 -> "Lua crescente"
                phase < 0.28 -> "Quarto crescente"
                phase < 0.47 -> "Crescente gibosa"
                phase < 0.53 -> "Lua cheia"
                phase < 0.72 -> "Minguante gibosa"
                phase < 0.78 -> "Quarto minguante"
                else -> "Lua minguante"
            }
    }

    fun moon(ms: Long): MoonInfo {
        val d = toDays(ms)
        val s = sunCoords(d)
        val m = moonCoords(d)
        val sdist = 149598000.0
        val phi = acos(sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra))
        val inc = atan2(sdist * sin(phi), m.dist - sdist * cos(phi))
        val angle = atan2(
            cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra),
        )
        return MoonInfo((1 + cos(inc)) / 2, 0.5 + 0.5 * inc * sign(angle) / PI)
    }

    data class Period(val start: Long, val end: Long, val major: Boolean)

    data class Day(
        val sunrise: Long?,
        val sunset: Long?,
        val periods: List<Period>,
    )

    /** Calcula o dia (meia-noite a meia-noite no fuso do celular) amostrando a cada 5 minutos. */
    fun day(date: LocalDate, lat: Double, lng: Double, zone: ZoneId = ZoneId.systemDefault()): Day {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val step = 5 * 60_000L
        val n = (24 * 60 / 5)
        val sun = DoubleArray(n + 1) { alt(start + it * step, lat, lng, moon = false) }
        val moon = DoubleArray(n + 1) { alt(start + it * step, lat, lng, moon = true) }
        fun t(i: Int) = start + i * step

        var sunrise: Long? = null
        var sunset: Long? = null
        val h0 = -0.833 // refração + raio do disco
        for (i in 1..n) {
            if (sun[i - 1] < h0 && sun[i] >= h0 && sunrise == null) sunrise = interp(t(i - 1), sun[i - 1], t(i), sun[i], h0)
            if (sun[i - 1] >= h0 && sun[i] < h0 && sunset == null) sunset = interp(t(i - 1), sun[i - 1], t(i), sun[i], h0)
        }

        // Solunar: períodos maiores = lua no ponto mais alto/baixo (±1h); menores = nascer/pôr da lua (±30 min)
        val periods = mutableListOf<Period>()
        for (i in 1 until n) {
            val up = moon[i] > moon[i - 1] && moon[i] >= moon[i + 1]
            val down = moon[i] < moon[i - 1] && moon[i] <= moon[i + 1]
            if (up || down) periods += Period(t(i) - 3_600_000, t(i) + 3_600_000, major = true)
        }
        val mh = 0.133
        for (i in 1..n) {
            val crossUp = moon[i - 1] < mh && moon[i] >= mh
            val crossDown = moon[i - 1] >= mh && moon[i] < mh
            if (crossUp || crossDown) {
                val c = interp(t(i - 1), moon[i - 1], t(i), moon[i], mh)
                periods += Period(c - 1_800_000, c + 1_800_000, major = false)
            }
        }
        return Day(sunrise, sunset, periods.sortedBy { it.start })
    }

    private fun interp(t0: Long, a0: Double, t1: Long, a1: Double, target: Double): Long {
        if (a1 == a0) return t0
        return t0 + ((target - a0) / (a1 - a0) * (t1 - t0)).toLong()
    }
}
