package com.pesca.pontos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Procura uma versão nova na página de versões (Releases) do GitHub.
 * Troque GITHUB_REPO pelo "usuario/repositorio" quando o projeto estiver no GitHub.
 */
object Updates {
    const val GITHUB_REPO = "rodrigopaduab/barroca-maps"
    val releasesPage get() = "https://github.com/$GITHUB_REPO/releases"
    val configured get() = !GITHUB_REPO.startsWith("SEU_USUARIO")

    sealed class Result {
        data class UpToDate(val current: String) : Result()
        data class NewVersion(val latest: String, val url: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        if (!configured) return@withContext Result.Failed("O endereço do GitHub ainda não foi configurado no app.")
        try {
            val conn = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 404) return@withContext Result.Failed("Nenhuma versão publicada ainda.")
            if (conn.responseCode != 200) return@withContext Result.Failed("O GitHub respondeu com erro ${conn.responseCode}.")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val latest = json.optString("tag_name").removePrefix("v")
            val url = json.optString("html_url", releasesPage)
            if (isNewer(latest, currentVersion)) Result.NewVersion(latest, url) else Result.UpToDate(currentVersion)
        } catch (e: Exception) {
            Result.Failed("Sem internet ou GitHub fora do ar.")
        }
    }

    /** Compara "1.2.10" com "1.2.9" número a número. */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
