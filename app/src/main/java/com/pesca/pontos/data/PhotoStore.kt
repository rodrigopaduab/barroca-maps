package com.pesca.pontos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Guarda as fotos dentro da pasta privada do app, reduzidas para no máximo 1920 px
 * e em JPEG 85%. O arquivo salvo não leva dados de GPS (EXIF), então pode ser
 * compartilhado sem revelar o ponto.
 */
class PhotoStore(private val context: Context) {

    val dir: File = File(context.filesDir, "photos").apply { mkdirs() }

    /** Cria um arquivo temporário para a câmera escrever e devolve a Uri dele. */
    fun newCameraUri(): Uri {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(camDir, "cam_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Copia uma foto (câmera ou galeria) para o app. Devolve o caminho salvo, ou null se falhar. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // 1) só lê o tamanho, para escolher a redução
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
                sample *= 2
            }

            // 2) decodifica já reduzido
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            // 3) corrige a rotação da câmera
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            // 4) ajuste fino para o lado maior ficar em MAX_SIDE
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            if (rotation != 0f || scale < 1f) {
                val m = Matrix().apply {
                    if (scale < 1f) postScale(scale, scale)
                    if (rotation != 0f) postRotate(rotation)
                }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated != bmp) bmp.recycle()
                bmp = rotated
            }

            val out = File(dir, "${UUID.randomUUID()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()

            // apaga o temporário da câmera, se for o caso
            if (uri.authority == "${context.packageName}.fileprovider") {
                File(context.cacheDir, "camera/${uri.lastPathSegment}").delete()
            }
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun delete(paths: Collection<String>) {
        paths.forEach { p -> runCatching { File(p).takeIf { it.parentFile == dir }?.delete() } }
    }

    companion object {
        private const val MAX_SIDE = 1920
    }
}
