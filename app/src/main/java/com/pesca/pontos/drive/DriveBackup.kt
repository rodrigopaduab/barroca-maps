package com.pesca.pontos.drive

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.pesca.pontos.Backup
import com.pesca.pontos.PescaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Backup no Google Drive do usuário, numa pasta "Ceva Backups" que ele pode ver no Drive.
 * Usa só a permissão "drive.file": o app enxerga apenas os arquivos que ele mesmo criou.
 */
object DriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER = "Ceva Backups"
    private const val KEEP = 5
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    class DriveException(message: String) : Exception(message)

    data class RemoteBackup(val id: String, val name: String, val createdAt: Long, val sizeBytes: Long)

    /** Pede autorização. Se precisar mostrar a tela do Google, o resultado vem com hasResolution(). */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request).await()

    /** Token sem mostrar nada (para o backup automático). null = precisa reconectar. */
    suspend fun tokenSilently(context: Context): String? = try {
        val r = authorize(context)
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) {
        null
    }

    // ---------------- backup ----------------

    suspend fun backupNow(context: Context, token: String): Backup.Info = withContext(Dispatchers.IO) {
        val app = context.applicationContext as PescaApp
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val file = File(context.cacheDir, "drive-ceva-backup-$stamp.zip")
        try {
            val info = Backup.export(context, app.repo, Uri.fromFile(file), version)
            val folder = folderId(token)
            upload(token, folder, file, "ceva-backup-$stamp.zip")
            // mantém só os mais recentes
            list(token, folder).drop(KEEP).forEach { runCatching { delete(token, it.id) } }
            app.settings.markDriveBackup(info.createdAt)
            info
        } finally {
            file.delete()
        }
    }

    suspend fun listBackups(token: String): List<RemoteBackup> = withContext(Dispatchers.IO) {
        list(token, folderId(token))
    }

    /** Baixa o backup escolhido e restaura (troca tudo do celular). */
    suspend fun restore(context: Context, token: String, backup: RemoteBackup) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as PescaApp
        val file = File(context.cacheDir, "drive-restore.zip")
        try {
            download(token, backup.id, file)
            Backup.restore(context, app.repo, Uri.fromFile(file))
        } finally {
            file.delete()
        }
    }

    suspend fun inspect(context: Context, token: String, backup: RemoteBackup): Backup.Info = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "drive-inspect.zip")
        try {
            download(token, backup.id, file)
            Backup.inspect(context, Uri.fromFile(file))
        } finally {
            file.delete()
        }
    }

    // ---------------- agendamento ----------------

    fun schedule(context: Context, enabled: Boolean, wifiOnly: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<DriveWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private const val WORK = "drive-backup"

    // ---------------- Drive REST ----------------

    private fun folderId(token: String): String {
        val q = URLEncoder.encode(
            "name = '$FOLDER' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8"
        )
        val found = request(token, "GET", "$API/files?q=$q&fields=files(id)&spaces=drive")
        val files = JSONObject(found).getJSONArray("files")
        if (files.length() > 0) return files.getJSONObject(0).getString("id")
        val body = JSONObject().put("name", FOLDER).put("mimeType", "application/vnd.google-apps.folder").toString()
        return JSONObject(request(token, "POST", "$API/files?fields=id", body, "application/json")).getString("id")
    }

    private fun list(token: String, folder: String): List<RemoteBackup> {
        val q = URLEncoder.encode("'$folder' in parents and trashed = false", "UTF-8")
        val res = JSONObject(request(token, "GET", "$API/files?q=$q&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)&pageSize=50"))
        val arr = res.getJSONArray("files")
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            RemoteBackup(
                id = o.getString("id"),
                name = o.optString("name"),
                createdAt = runCatching { Instant.parse(o.getString("createdTime")).toEpochMilli() }.getOrDefault(0L),
                sizeBytes = o.optString("size").toLongOrNull() ?: 0L,
            )
        }
    }

    /** Envio "retomável": funciona para arquivos grandes (muitas fotos). */
    private fun upload(token: String, folder: String, file: File, name: String) {
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(folder)).toString()
        val start = (URL("$UPLOAD/files?uploadType=resumable").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/zip")
            setRequestProperty("X-Upload-Content-Length", file.length().toString())
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        start.outputStream.use { it.write(meta.toByteArray()) }
        check(start, "iniciar o envio")
        val location = start.getHeaderField("Location") ?: throw DriveException("O Drive não aceitou o envio.")

        val put = (URL(location).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setFixedLengthStreamingMode(file.length())
            setRequestProperty("Content-Type", "application/zip")
            connectTimeout = 15_000
            readTimeout = 120_000
        }
        file.inputStream().use { input -> put.outputStream.use { input.copyTo(it) } }
        check(put, "enviar o arquivo")
    }

    private fun download(token: String, id: String, dest: File) {
        val conn = (URL("$API/files/$id?alt=media").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 120_000
        }
        check(conn, "baixar o backup")
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    private fun delete(token: String, id: String) {
        request(token, "DELETE", "$API/files/$id")
    }

    private fun request(token: String, method: String, url: String, body: String? = null, contentType: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
            }
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
        check(conn, "falar com o Google Drive")
        return if (conn.responseCode == 204) "" else conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun check(conn: HttpURLConnection, what: String) {
        val code = conn.responseCode
        if (code in 200..299) return
        val detail = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        throw DriveException(
            when (code) {
                401 -> "A conexão com o Google expirou. Toque em Conectar de novo."
                403 -> if (detail.contains("accessNotConfigured") || detail.contains("has not been used")) {
                    "A Google Drive API não está ativada no projeto do Google Cloud."
                } else "O Google recusou o acesso ao Drive."
                404 -> "Arquivo não encontrado no Drive."
                else -> "Não deu para $what (erro $code)."
            }
        )
    }
}

/** Backup automático diário (roda sozinho com Wi-Fi e bateria boa). */
class DriveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val token = DriveBackup.tokenSilently(applicationContext) ?: return Result.success() // desconectado: tenta amanhã
        return try {
            DriveBackup.backupNow(applicationContext, token)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
