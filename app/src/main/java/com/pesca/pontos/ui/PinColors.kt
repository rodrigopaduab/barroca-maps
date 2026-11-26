package com.pesca.pontos.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.asAndroidPath
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** Atalhos de cor no cadastro do ponto. */
data class PinColor(val name: String, val swatch: Color)

val PIN_COLORS = listOf(
    PinColor("Laranja", Color(0xFFFF7A2E)),
    PinColor("Vermelho", Color(0xFFE53935)),
    PinColor("Amarelo", Color(0xFFFDD835)),
    PinColor("Verde", Color(0xFF43A047)),
    PinColor("Ciano", Color(0xFF00ACC1)),
    PinColor("Azul", Color(0xFF1E63D6)),
    PinColor("Roxo", Color(0xFF8E24AA)),
    PinColor("Rosa", Color(0xFFE91E63)),
)

val DEFAULT_PIN_COLOR: Int = PIN_COLORS[0].swatch.toArgb()

/**
 * O campo "color" do ponto guarda uma cor ARGB.
 * Pontos da versão 0.1 guardavam o índice 0..7 de PIN_COLORS: são convertidos aqui.
 */
fun resolvePinColor(stored: Int): Int =
    if (stored in PIN_COLORS.indices) PIN_COLORS[stored].swatch.toArgb() else stored

fun pinComposeColor(stored: Int): Color = Color(resolvePinColor(stored))

private val pinPath by lazy {
    PathParser().parsePathString("M48,4C25,4 8,21 8,43c0,29 40,73 40,73s40,-44 40,-73C88,21 71,4 48,4z").toPath().asAndroidPath()
}
private val iconCache = LruCache<Int, BitmapDescriptor>(64)

/** Pino desenhado na cor escolhida (qualquer cor). Chamar só depois que o mapa carregou. */
fun pinIcon(stored: Int): BitmapDescriptor {
    val argb = resolvePinColor(stored)
    iconCache.get(argb)?.let { return it }
    val density = Resources.getSystem().displayMetrics.density
    val w = (34 * density).toInt()
    val h = (44 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val k = minOf(w / 100f, h / 124f)
    c.translate((w - 96 * k) / 2f, 2 * k)
    c.scale(k, k)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; style = Paint.Style.FILL }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF10241B.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f; strokeJoin = Paint.Join.ROUND
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10241B.toInt() }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F6F1.toInt() }
    c.drawPath(pinPath, fill)
    c.drawPath(pinPath, outline)
    c.drawCircle(48f, 43f, 17f, ring)
    c.drawCircle(48f, 43f, 11f, dot)
    return BitmapDescriptorFactory.fromBitmap(bmp).also { iconCache.put(argb, it) }
}
