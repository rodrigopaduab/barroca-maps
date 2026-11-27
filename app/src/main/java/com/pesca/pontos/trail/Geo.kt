package com.pesca.pontos.trail

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Ponto simples (latitude, longitude). */
data class P(val lat: Double, val lng: Double)

object Geo {
    private const val R = 6_371_000.0

    fun dist(a: P, b: P): Double {
        val dLat = (b.lat - a.lat) * PI / 180
        val dLng = (b.lng - a.lng) * PI / 180
        val h = sin(dLat / 2).let { it * it } +
            cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2).let { it * it }
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun length(pts: List<P>): Double = pts.zipWithNext { a, b -> dist(a, b) }.sum()

    // ---------- linha codificada (formato "polyline" do Google) ----------

    fun encode(pts: List<P>, precision: Double = 1e6): String {
        val sb = StringBuilder()
        var pLat = 0L
        var pLng = 0L
        for (p in pts) {
            val lat = round(p.lat * precision).toLong()
            val lng = round(p.lng * precision).toLong()
            encodeValue(lat - pLat, sb)
            encodeValue(lng - pLng, sb)
            pLat = lat
            pLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    /** Trechos separados por espaço (um buraco no GPS vira dois trechos). */
    fun encodeSegments(segments: List<List<P>>, precision: Double = 1e6): String =
        segments.filter { it.isNotEmpty() }.joinToString(" ") { encode(it, precision) }

    fun decodeSegments(s: String, precision: Double = 1e6): List<List<P>> =
        s.split(' ').filter { it.isNotEmpty() }.map { decodeOne(it, precision) }.filter { it.isNotEmpty() }

    /** Todos os pontos em sequência (os trechos são emendados). */
    fun decode(s: String, precision: Double = 1e6): List<P> = decodeSegments(s, precision).flatten()

    private fun decodeOne(s: String, precision: Double): List<P> {
        val out = ArrayList<P>()
        var i = 0
        var lat = 0L
        var lng = 0L
        fun next(): Long {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = s[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && i < s.length)
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        try {
            while (i < s.length) {
                lat += next()
                lng += next()
                out += P(lat / precision, lng / precision)
            }
        } catch (e: Exception) {
            // linha corrompida: devolve o que deu para ler
        }
        return out
    }

    // ---------- simplificação (Douglas-Peucker) ----------

    fun simplify(pts: List<P>, toleranceM: Double): List<P> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to pts.lastIndex)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxD = 0.0
            var idx = -1
            for (k in a + 1 until b) {
                val d = segmentDistance(pts[k], pts[a], pts[b]).first
                if (d > maxD) { maxD = d; idx = k }
            }
            if (idx >= 0 && maxD > toleranceM) {
                keep[idx] = true
                stack.add(a to idx)
                stack.add(idx to b)
            }
        }
        return pts.filterIndexed { k, _ -> keep[k] }
    }

    /** Distância (m) do ponto ao segmento a-b e a fração t (0..1) do ponto mais próximo no segmento. */
    fun segmentDistance(p: P, a: P, b: P): Pair<Double, Double> {
        val k = cos(p.lat * PI / 180) * 111_320.0
        val ax = a.lng * k; val ay = a.lat * 110_540.0
        val bx = b.lng * k; val by = b.lat * 110_540.0
        val px = p.lng * k; val py = p.lat * 110_540.0
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy) to t
    }

    /** Onde o ponto está em relação ao caminho. */
    data class Progress(val offPathM: Double, val traveledM: Double, val remainingM: Double, val segment: Int, val t: Double)

    fun progress(path: List<P>, me: P): Progress? {
        if (path.size < 2) return null
        val cum = DoubleArray(path.size)
        for (k in 1 until path.size) cum[k] = cum[k - 1] + dist(path[k - 1], path[k])
        var best = Double.MAX_VALUE
        var seg = 0
        var bestT = 0.0
        for (k in 0 until path.lastIndex) {
            val (d, t) = segmentDistance(me, path[k], path[k + 1])
            if (d < best) { best = d; seg = k; bestT = t }
        }
        val segLen = cum[seg + 1] - cum[seg]
        val traveled = cum[seg] + segLen * bestT
        return Progress(best, traveled, cum.last() - traveled, seg, bestT)
    }

    /** Distância percorrida (ao longo do caminho) até o ponto do caminho mais próximo de "p". */
    fun alongPath(path: List<P>, p: P): Double = progress(path, p)?.traveledM ?: 0.0

    /** Pontos do caminho já percorridos (até a posição atual), para pintar de cinza. */
    fun traveledPart(path: List<P>, pr: Progress): List<P> {
        val a = path[pr.segment]
        val b = path[pr.segment + 1]
        val mid = P(a.lat + (b.lat - a.lat) * pr.t, a.lng + (b.lng - a.lng) * pr.t)
        return path.subList(0, pr.segment + 1) + mid
    }

    fun formatDistance(m: Double): String =
        if (m < 1000) "${(m / 10).toInt() * 10} m" else String.format(java.util.Locale("pt", "BR"), "%.1f km", m / 1000)
}
