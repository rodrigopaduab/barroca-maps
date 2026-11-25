package com.pesca.pontos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pesca.pontos.data.Spot
import java.io.File
import java.util.Locale

/** O que vai dentro do QR code de um ponto. Fotos e peixes não vão (não cabem no QR). */
data class SharedSpot(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String,
    val color: Int,
)

/**
 * O QR guarda um link normal do Google Maps com dados extras do Ceva.
 * - Quem tem o Ceva lê pelo app e o ponto é importado com nome, informações e cor.
 * - Quem não tem lê com a câmera do celular e o Google Maps abre direto no ponto.
 */
object SpotQr {
    private const val MAX_NOTES = 300

    fun encode(spot: Spot): String {
        val coords = String.format(Locale.US, "%.6f,%.6f", spot.latitude, spot.longitude)
        return Uri.Builder()
            .scheme("https").authority("www.google.com").path("maps/search/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("query", coords)
            .appendQueryParameter("ceva", "1")
            .appendQueryParameter("n", spot.name)
            .appendQueryParameter("c", spot.color.toString())
            .apply { if (spot.notes.isNotBlank()) appendQueryParameter("i", spot.notes.take(MAX_NOTES)) }
            .build()
            .toString()
    }

    /** Aceita o QR do Ceva e também links simples do Google Maps com coordenadas. */
    fun decode(text: String): SharedSpot? {
        val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
        val raw = uri.getQueryParameter("query") ?: uri.getQueryParameter("q") ?: return null
        val parts = raw.split(",").map { it.trim().toDoubleOrNull() }
        if (parts.size != 2 || parts.any { it == null }) return null
        val lat = parts[0]!!
        val lng = parts[1]!!
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return SharedSpot(
            name = uri.getQueryParameter("n")?.take(80) ?: "Ponto recebido",
            latitude = lat,
            longitude = lng,
            notes = uri.getQueryParameter("i")?.take(MAX_NOTES) ?: "",
            color = uri.getQueryParameter("c")?.toIntOrNull() ?: 0,
        )
    }

    fun qrBitmap(content: String, size: Int = 800): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Imagem para mandar no WhatsApp: nome do ponto, QR e uma instrução curta. */
    private fun shareImage(spot: Spot): Bitmap {
        val w = 1000
        val h = 1240
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF10241B.toInt())
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(60f, 200f, w - 60f, 1100f, 48f, 48f, card)
        c.drawBitmap(qrBitmap(encode(spot), 780), 110f, 260f, null)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4F6F1.toInt(); textSize = 64f; typeface = Typeface.DEFAULT_BOLD
        }
        val name = TextUtils.ellipsize(spot.name, android.text.TextPaint(title), w - 120f, TextUtils.TruncateAt.END)
        c.drawText(name.toString(), 60f, 130f, title)

        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF7A2E.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("Escaneie com o app Ceva ou com a câmera", 60f, 1180f, small)
        return out
    }

    /** Abre o menu de compartilhar do Android com a imagem do QR e o link. */
    fun share(context: Context, spot: Spot) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "ponto_qr.png")
        file.outputStream().use { shareImage(spot).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Ponto de pesca: ${spot.name}\n${encode(spot)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar ponto"))
    }
}
