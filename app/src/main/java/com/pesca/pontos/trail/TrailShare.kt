package com.pesca.pontos.trail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Caminho recebido de outra pessoa (QR ou arquivo). */
data class SharedTrail(
    val name: String,
    val points: List<P>,
    val marks: List<PendingMark>,
    val durationMs: Long,
    val spot: SharedTrailSpot?,
)

data class SharedTrailSpot(
    val name: String, val latitude: Double, val longitude: Double,
    val notes: String, val color: Int, val mainFish: String,
)

/**
 * Compartilhar caminho.
 * - QR: traçado simplificado (cabe num QR), marcações e o ponto de destino.
 * - Arquivo .json: traçado completo, para caminhos longos.
 */
object TrailShare {
    private const val QR_LIMIT = 2200 // caracteres; acima disso o QR fica difícil de ler

    private val MARK_TYPES = setOf("PORTEIRA", "ATOLEIRO", "CARRO", "PONTE", "SEM_SINAL", "TEXTO")

    // ---------- QR ----------

    fun qrText(trail: Trail, marks: List<TrailMark>, spot: Spot?): String {
        val full = Geo.decode(trail.points)
        var tol = 3.0
        var text: String
        do {
            val simple = Geo.simplify(full, tol)
            text = build(trail, marks, spot, Geo.encode(simple, 1e5))
            tol *= 1.6
        } while (text.length > QR_LIMIT && tol < 400)
        return text
    }

    private fun build(trail: Trail, marks: List<TrailMark>, spot: Spot?, poly: String): String {
        val b = Uri.Builder().scheme("ceva").authority("caminho")
            .appendQueryParameter("v", "1")
            .appendQueryParameter("n", trail.name.take(60))
            .appendQueryParameter("t", (trail.durationMs / 1000).toString())
            .appendQueryParameter("p", poly)
        if (marks.isNotEmpty()) {
            val m = marks.joinToString("|") {
                String.format(Locale.US, "%s~%.5f~%.5f~%s", it.type, it.latitude, it.longitude, it.label.take(30).replace("|", " ").replace("~", " "))
            }
            b.appendQueryParameter("m", m)
        }
        spot?.let {
            b.appendQueryParameter("sn", it.name.take(60))
                .appendQueryParameter("sa", String.format(Locale.US, "%.6f", it.latitude))
                .appendQueryParameter("so", String.format(Locale.US, "%.6f", it.longitude))
                .appendQueryParameter("sc", it.color.toString())
            if (it.mainFish.isNotBlank()) b.appendQueryParameter("sf", it.mainFish.take(40))
        }
        return b.build().toString()
    }

    fun isTrailQr(text: String) = text.trim().startsWith("ceva://caminho")

    fun decodeQr(text: String): SharedTrail? = runCatching {
        val uri = Uri.parse(text.trim())
        val pts = Geo.decode(uri.getQueryParameter("p") ?: return null, 1e5)
        if (pts.size < 2) return null
        val marks = uri.getQueryParameter("m")?.split("|")?.mapNotNull { part ->
            val f = part.split("~")
            if (f.size < 4) return@mapNotNull null
            val type = f[0].takeIf { it in MARK_TYPES } ?: "TEXTO"
            PendingMark(P(f[1].toDouble(), f[2].toDouble()), type, f[3])
        }.orEmpty()
        val spot = uri.getQueryParameter("sa")?.toDoubleOrNull()?.let { lat ->
            val lng = uri.getQueryParameter("so")?.toDoubleOrNull() ?: return@let null
            SharedTrailSpot(
                name = uri.getQueryParameter("sn") ?: "Ponto recebido", latitude = lat, longitude = lng,
                notes = "", color = uri.getQueryParameter("sc")?.toIntOrNull() ?: 0,
                mainFish = uri.getQueryParameter("sf") ?: "",
            )
        }
        SharedTrail(
            name = uri.getQueryParameter("n") ?: "Caminho recebido",
            points = pts, marks = marks,
            durationMs = (uri.getQueryParameter("t")?.toLongOrNull() ?: 0) * 1000,
            spot = spot,
        )
    }.getOrNull()

    fun qrBitmap(content: String, size: Int = 900): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    fun shareQrImage(context: Context, trail: Trail, content: String) {
        val w = 1000
        val h = 1300
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF10241B.toInt())
        c.drawRoundRect(50f, 190f, w - 50f, 1140f, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawBitmap(qrBitmap(content, 840), 80f, 245f, null)
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F6F1.toInt(); textSize = 60f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText(TextUtils.ellipsize("Caminho: ${trail.name}", title, w - 100f, TextUtils.TruncateAt.END).toString(), 50f, 120f, title)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF7A2E.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("Leia com o app Ceva (botão de QR no mapa)", 50f, 1220f, small)
        share(context, "caminho_qr.png", "image/png", "Caminho de pesca: ${trail.name}") { f ->
            f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    // ---------- arquivo ----------

    fun shareFile(context: Context, trail: Trail, marks: List<TrailMark>, spot: Spot?) {
        val json = JSONObject().apply {
            put("app", "Ceva"); put("type", "caminho"); put("v", 1)
            put("name", trail.name); put("durationMs", trail.durationMs); put("points", trail.points)
            put("marks", JSONArray(marks.map {
                JSONObject().apply { put("type", it.type); put("label", it.label); put("lat", it.latitude); put("lng", it.longitude) }
            }))
            spot?.let {
                put("spot", JSONObject().apply {
                    put("name", it.name); put("lat", it.latitude); put("lng", it.longitude)
                    put("notes", it.notes); put("color", it.color); put("mainFish", it.mainFish)
                })
            }
        }
        val safe = trail.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40).ifEmpty { "caminho" }
        share(context, "ceva-caminho-$safe.json", "application/json", "Caminho de pesca: ${trail.name}") { f ->
            f.writeText(json.toString())
        }
    }

    fun decodeFile(text: String): SharedTrail? = runCatching {
        val o = JSONObject(text)
        if (o.optString("type") != "caminho") return null
        val pts = Geo.decode(o.getString("points"))
        if (pts.size < 2) return null
        val marksArr = o.optJSONArray("marks") ?: JSONArray()
        val marks = List(marksArr.length()) {
            val m = marksArr.getJSONObject(it)
            PendingMark(P(m.getDouble("lat"), m.getDouble("lng")), m.optString("type").takeIf { t -> t in MARK_TYPES } ?: "TEXTO", m.optString("label"))
        }
        val spot = o.optJSONObject("spot")?.let {
            SharedTrailSpot(
                it.optString("name", "Ponto recebido"), it.getDouble("lat"), it.getDouble("lng"),
                it.optString("notes"), it.optInt("color"), it.optString("mainFish"),
            )
        }
        SharedTrail(o.optString("name", "Caminho recebido"), pts, marks, o.optLong("durationMs"), spot)
    }.getOrNull()

    private fun share(context: Context, fileName: String, mime: String, text: String, write: (File) -> Unit) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName)
        write(file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar caminho"))
    }
}
