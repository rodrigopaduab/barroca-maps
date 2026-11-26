package com.pesca.pontos

import android.content.Context
import android.net.Uri
import com.pesca.pontos.data.Catch
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup em arquivo .zip:
 *   ceva.json  -> pontos e peixes
 *   photos/    -> as fotos
 * O usuário escolhe onde salvar (Downloads, Google Drive, pendrive...).
 */
object Backup {
    private const val FORMAT = 1
    private const val DATA_FILE = "ceva.json"
    private const val PHOTOS_DIR = "photos/"
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,80}\\.jpg$")

    data class Info(val createdAt: Long, val spots: Int, val catches: Int, val photos: Int, val appVersion: String)

    class BackupException(message: String) : Exception(message)

    // ---------- salvar ----------

    suspend fun export(context: Context, repo: Repository, target: Uri, appVersion: String): Info = withContext(Dispatchers.IO) {
        val spots = repo.spots.listAll()
        val catches = repo.catches.listAll()
        val trails = repo.trails.listAll()
        val marks = repo.trails.listAllMarks()
        val photoFiles = (spots.flatMap { it.photos } + catches.flatMap { it.photos })
            .map(::File).filter { it.isFile }.distinctBy { it.name }

        val json = JSONObject().apply {
            put("format", FORMAT)
            put("app", "Ceva")
            put("appVersion", appVersion)
            put("createdAt", System.currentTimeMillis())
            put("spots", JSONArray(spots.map(::spotToJson)))
            put("catches", JSONArray(catches.map(::catchToJson)))
            put("trails", JSONArray(trails.map { t ->
                JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("spotId", t.spotId ?: JSONObject.NULL)
                    put("createdAt", t.createdAt); put("durationMs", t.durationMs); put("distanceM", t.distanceM); put("points", t.points)
                }
            }))
            put("marks", JSONArray(marks.map { m ->
                JSONObject().apply {
                    put("trailId", m.trailId); put("lat", m.latitude); put("lng", m.longitude)
                    put("type", m.type); put("label", m.label); put("at", m.at)
                }
            }))
        }

        val out = context.contentResolver.openOutputStream(target)
            ?: throw BackupException("Não consegui criar o arquivo nesse lugar.")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_FILE))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photoFiles.forEach { f ->
                zip.putNextEntry(ZipEntry(PHOTOS_DIR + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Info(json.getLong("createdAt"), spots.size, catches.size, photoFiles.size, appVersion)
    }

    // ---------- conferir antes de restaurar ----------

    /** Lê só o resumo do backup, para mostrar ao usuário antes de trocar os dados. */
    suspend fun inspect(context: Context, source: Uri): Info = withContext(Dispatchers.IO) {
        var json: JSONObject? = null
        var photos = 0
        openZip(context, source).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == DATA_FILE -> json = parseJson(zip.readBytes())
                    e.name.startsWith(PHOTOS_DIR) -> photos++
                }
            }
        }
        val j = json ?: throw BackupException("Esse arquivo não é um backup do Ceva.")
        Info(
            createdAt = j.optLong("createdAt"),
            spots = j.getJSONArray("spots").length(),
            catches = j.getJSONArray("catches").length(),
            photos = photos,
            appVersion = j.optString("appVersion"),
        )
    }

    // ---------- restaurar ----------

    /** Troca TODOS os pontos, peixes e fotos do celular pelos do backup. */
    suspend fun restore(context: Context, repo: Repository, source: Uri) = withContext(Dispatchers.IO) {
        val photosDir = repo.photos.dir
        val temp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            // 1) extrai para uma pasta temporária e confere tudo antes de mexer nos dados atuais
            var json: JSONObject? = null
            openZip(context, source).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (e.name == DATA_FILE) {
                        json = parseJson(zip.readBytes())
                    } else if (e.name.startsWith(PHOTOS_DIR)) {
                        val name = e.name.removePrefix(PHOTOS_DIR)
                        if (!SAFE_NAME.matches(name)) continue // ignora nomes estranhos (proteção)
                        File(temp, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val j = json ?: throw BackupException("Esse arquivo não é um backup do Ceva.")
            if (j.optInt("format") > FORMAT) throw BackupException("Backup de uma versão mais nova do app. Atualize o Ceva primeiro.")

            fun localPath(name: String) = File(photosDir, File(name).name).absolutePath
            val spots = j.getJSONArray("spots").let { arr -> List(arr.length()) { spotFromJson(arr.getJSONObject(it), ::localPath) } }
            val spotIds = spots.map { it.id }.toSet()
            val catches = j.getJSONArray("catches").let { arr -> List(arr.length()) { catchFromJson(arr.getJSONObject(it), ::localPath) } }
                .filter { it.spotId in spotIds }

            val trails = j.optJSONArray("trails")?.let { arr ->
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    Trail(
                        id = o.getLong("id"), name = o.getString("name"),
                        spotId = if (o.isNull("spotId")) null else o.getLong("spotId").takeIf { s -> s in spotIds },
                        createdAt = o.optLong("createdAt"), durationMs = o.optLong("durationMs"),
                        distanceM = o.optDouble("distanceM", 0.0), points = o.getString("points"),
                    )
                }
            }.orEmpty()
            val trailIds = trails.map { it.id }.toSet()
            val marks = j.optJSONArray("marks")?.let { arr ->
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    TrailMark(
                        trailId = o.getLong("trailId"), latitude = o.getDouble("lat"), longitude = o.getDouble("lng"),
                        type = o.optString("type", "TEXTO"), label = o.optString("label"), at = o.optLong("at"),
                    )
                }
            }.orEmpty().filter { it.trailId in trailIds }

            // 2) troca os dados (transação: se falhar, nada muda)
            repo.replaceAll(spots, catches, trails, marks)

            // 3) troca as fotos
            photosDir.listFiles()?.forEach { it.delete() }
            temp.listFiles()?.forEach { f -> f.copyTo(File(photosDir, f.name), overwrite = true) }
        } finally {
            temp.deleteRecursively()
        }
    }

    // ---------- conversões ----------

    private fun openZip(context: Context, uri: Uri): ZipInputStream {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Não consegui abrir o arquivo.")
        return ZipInputStream(input.buffered())
    }

    private fun parseJson(bytes: ByteArray): JSONObject =
        runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
            ?: throw BackupException("O arquivo de backup está corrompido.")

    private fun names(paths: List<String>) = JSONArray(paths.map { File(it).name })

    private fun JSONObject.strings(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { arr.getString(it) }
    }

    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)

    private fun spotToJson(s: Spot) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("notes", s.notes)
        put("lat", s.latitude); put("lng", s.longitude); put("color", s.color)
        put("createdAt", s.createdAt); put("photos", names(s.photos))
        put("mainFish", s.mainFish)
        put("weatherTemp", s.weatherTemp ?: JSONObject.NULL); put("weatherCode", s.weatherCode ?: JSONObject.NULL)
        put("weatherWind", s.weatherWind ?: JSONObject.NULL); put("weatherHumidity", s.weatherHumidity ?: JSONObject.NULL)
        put("weatherFetched", s.weatherFetched)
    }

    private fun spotFromJson(o: JSONObject, path: (String) -> String) = Spot(
        id = o.getLong("id"),
        name = o.getString("name"),
        notes = o.optString("notes"),
        latitude = o.getDouble("lat"),
        longitude = o.getDouble("lng"),
        color = o.optInt("color"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        photos = o.strings("photos").filter { SAFE_NAME.matches(it) }.map(path),
        mainFish = o.optString("mainFish"),
        weatherTemp = o.doubleOrNull("weatherTemp"),
        weatherCode = o.doubleOrNull("weatherCode")?.toInt(),
        weatherWind = o.doubleOrNull("weatherWind"),
        weatherHumidity = o.doubleOrNull("weatherHumidity")?.toInt(),
        weatherFetched = o.optBoolean("weatherFetched"),
    )

    private fun catchToJson(c: Catch) = JSONObject().apply {
        put("id", c.id); put("spotId", c.spotId); put("species", c.species)
        put("weightKg", c.weightKg ?: JSONObject.NULL); put("lengthCm", c.lengthCm ?: JSONObject.NULL)
        put("caughtAt", c.caughtAt); put("bait", c.bait); put("notes", c.notes)
        put("released", c.released); put("photos", names(c.photos))
        put("weatherTemp", c.weatherTemp ?: JSONObject.NULL); put("weatherCode", c.weatherCode ?: JSONObject.NULL)
        put("weatherWind", c.weatherWind ?: JSONObject.NULL); put("weatherHumidity", c.weatherHumidity ?: JSONObject.NULL)
        put("weatherFetched", c.weatherFetched)
    }

    private fun catchFromJson(o: JSONObject, path: (String) -> String) = Catch(
        id = o.getLong("id"),
        spotId = o.getLong("spotId"),
        species = o.getString("species"),
        weightKg = o.doubleOrNull("weightKg"),
        lengthCm = o.doubleOrNull("lengthCm"),
        caughtAt = o.optLong("caughtAt", System.currentTimeMillis()),
        bait = o.optString("bait"),
        notes = o.optString("notes"),
        released = o.optBoolean("released"),
        photos = o.strings("photos").filter { SAFE_NAME.matches(it) }.map(path),
        weatherTemp = o.doubleOrNull("weatherTemp"),
        weatherCode = o.doubleOrNull("weatherCode")?.toInt(),
        weatherWind = o.doubleOrNull("weatherWind"),
        weatherHumidity = o.doubleOrNull("weatherHumidity")?.toInt(),
        weatherFetched = o.optBoolean("weatherFetched"),
    )
}
