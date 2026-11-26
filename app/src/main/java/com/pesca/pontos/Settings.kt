package com.pesca.pontos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme(val label: String, val description: String) {
    PADRAO("Padrão", "Verde-mata e laranja-boia."),
    CONTRASTE("Alto contraste", "Preto, branco e amarelo para ler no sol forte ou com baixa visão."),
    NOITE("Noite (luz vermelha)", "Tons de vermelho preservam a visão no escuro, para pescar à noite."),
}

/** Preferências do app. Os campos são estado do Compose: mudar aqui já redesenha a tela. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("ceva_prefs", Context.MODE_PRIVATE)

    var theme by mutableStateOf(
        runCatching { AppTheme.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(AppTheme.PADRAO)
    )
        private set

    var bigText by mutableStateOf(prefs.getBoolean("big_text", false))
        private set

    var satellite by mutableStateOf(prefs.getBoolean("satellite", true))
        private set

    /** Data do último backup em arquivo (0 = nunca). */
    var lastBackupAt by mutableStateOf(prefs.getLong("last_backup", 0L))
        private set

    fun markBackup(at: Long) { lastBackupAt = at; prefs.edit().putLong("last_backup", at).apply() }

    /** Peixes que o usuário digitou e salvou na lista de atalhos. */
    var customFish by mutableStateOf(prefs.getStringSet("custom_fish", emptySet())!!.sorted())
        private set

    fun addFish(name: String) {
        val n = name.trim()
        if (n.isEmpty() || customFish.any { it.equals(n, ignoreCase = true) }) return
        customFish = (customFish + n).sorted()
        prefs.edit().putStringSet("custom_fish", customFish.toSet()).apply()
    }

    fun removeFish(name: String) {
        customFish = customFish.filterNot { it.equals(name, ignoreCase = true) }
        prefs.edit().putStringSet("custom_fish", customFish.toSet()).apply()
    }

    fun updateTheme(t: AppTheme) { theme = t; prefs.edit().putString("theme", t.name).apply() }
    fun updateBigText(v: Boolean) { bigText = v; prefs.edit().putBoolean("big_text", v).apply() }
    fun updateSatellite(v: Boolean) { satellite = v; prefs.edit().putBoolean("satellite", v).apply() }
}
