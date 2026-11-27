package com.pesca.pontos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme(val label: String, val description: String) {
    PADRAO("Padrão", "Verde-mata e laranja-boia."),
    CONTRASTE("Alto contraste", "Preto, branco e amarelo para ler no sol forte ou com baixa visão."),
    NOITE("Noite (luz vermelha)", "Tons de vermelho preservam a visão no escuro, para pescar à noite."),
    VERMELHO_VERDE("Daltonismo vermelho-verde", "Azul e amarelo, para protanopia e deuteranopia (quem confunde vermelho e verde)."),
    AZUL_AMARELO("Daltonismo azul-amarelo", "Grafite e rosa, para tritanopia (quem confunde azul e amarelo)."),
    SEM_CORES("Sem cores", "Preto, branco e cinza, para acromatopsia (quem enxerga sem cores)."),
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

    /** Painel de pescaria aberto ou recolhido. Sempre começa recolhido ao abrir o app. */
    var panelOpen by mutableStateOf(false)
        private set

    fun updatePanelOpen(v: Boolean) { panelOpen = v }

    /** Último clima buscado para o painel (mostrado quando estiver sem internet). */
    var lastWeather: String?
        get() = prefs.getString("last_weather", null)
        set(v) { prefs.edit().putString("last_weather", v).apply() }

    /** A tela de boas-vindas (permissões) já foi mostrada. */
    var onboardingDone by mutableStateOf(prefs.getBoolean("onboarding_done", false))
        private set

    fun finishOnboarding() { onboardingDone = true; prefs.edit().putBoolean("onboarding_done", true).apply() }

    // ---- Google Drive ----
    var driveConnected by mutableStateOf(prefs.getBoolean("drive_connected", false))
        private set
    var driveAuto by mutableStateOf(prefs.getBoolean("drive_auto", true))
        private set
    var driveWifiOnly by mutableStateOf(prefs.getBoolean("drive_wifi", true))
        private set
    var lastDriveBackupAt by mutableStateOf(prefs.getLong("drive_last", 0L))
        private set

    fun updateDriveConnected(v: Boolean) { driveConnected = v; prefs.edit().putBoolean("drive_connected", v).apply() }
    fun updateDriveAuto(v: Boolean) { driveAuto = v; prefs.edit().putBoolean("drive_auto", v).apply() }
    fun updateDriveWifiOnly(v: Boolean) { driveWifiOnly = v; prefs.edit().putBoolean("drive_wifi", v).apply() }
    fun markDriveBackup(at: Long) {
        // pode ser chamado fora da thread principal (backup automático)
        prefs.edit().putLong("drive_last", at).apply()
        android.os.Handler(android.os.Looper.getMainLooper()).post { lastDriveBackupAt = at }
    }

    fun updateTheme(t: AppTheme) { theme = t; prefs.edit().putString("theme", t.name).apply() }
    fun updateBigText(v: Boolean) { bigText = v; prefs.edit().putBoolean("big_text", v).apply() }
    fun updateSatellite(v: Boolean) { satellite = v; prefs.edit().putBoolean("satellite", v).apply() }
}
