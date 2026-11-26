package com.pesca.pontos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import com.pesca.pontos.AppTheme
import com.pesca.pontos.Backup
import com.pesca.pontos.drive.DriveBackup
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pesca.pontos.Updates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var updateText by remember { mutableStateOf("Versão instalada: $version") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Aparência e acessibilidade")
            SettingsCard {
                Text("Cores do app", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { t ->
                        FilterChip(
                            selected = settings.theme == t,
                            onClick = { settings.updateTheme(t) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Text(settings.theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Letras maiores", settings.bigText) { settings.updateBigText(it) }
            }

            Section("Backup")
            DriveCard()
            BackupCard(version)

            Section("Caminhos")
            ImportTrailCard()

            Section("Mapa off-line")
            SettingsCard {
                Text(
                    "Para ver o mapa sem sinal, baixe a região no Google Maps antes de sair: foto do perfil › Mapas off-line › Selecionar seu mapa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { openGoogleMapsApp(context) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Baixar mapa off-line no Google Maps")
                }
            }

            Section("Atualizações")
            SettingsCard {
                Text(updateText, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        updateText = "Procurando..."
                        scope.launch {
                            when (val r = Updates.check(version)) {
                                is Updates.Result.UpToDate -> { updateText = "Você está na versão mais recente ($version)."; updateUrl = null }
                                is Updates.Result.NewVersion -> { updateText = "Versão ${r.latest} disponível!"; updateUrl = r.url }
                                is Updates.Result.Failed -> { updateText = r.reason; updateUrl = Updates.releasesPage }
                            }
                            checking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Procurar atualização") }
                updateUrl?.let { url ->
                    TextButton(onClick = { openUrl(context, url) }) { Text("Abrir página de versões no GitHub") }
                }
            }

            Text(
                "Ceva $version · seus pontos ficam só no seu celular",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BackupCard(version: String) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<Uri, Backup.Info>?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Salvando backup..."
        message = null
        scope.launch {
            message = try {
                val info = Backup.export(context, repo, uri, version)
                settings.markBackup(info.createdAt)
                "Backup salvo: ${info.spots} pontos, ${info.catches} peixes e ${info.photos} fotos."
            } catch (e: Exception) {
                "Não deu para salvar: ${e.message ?: "erro desconhecido"}"
            }
            busy = null
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Lendo o backup..."
        message = null
        scope.launch {
            try {
                pending = uri to Backup.inspect(context, uri)
            } catch (e: Exception) {
                message = e.message ?: "Não consegui ler esse arquivo."
            }
            busy = null
        }
    }

    SettingsCard {
        Text("Backup em arquivo", style = MaterialTheme.typography.titleSmall)
        Text(
            if (settings.lastBackupAt == 0L) "Nenhum backup feito ainda. Hoje seus pontos estão só neste celular."
            else "Último backup: ${formatDateTime(settings.lastBackupAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Gera um arquivo .zip com pontos, peixes e fotos. Salve no Google Drive, nos Downloads ou mande para você mesmo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(busy!!, style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        Button(
            enabled = busy == null,
            onClick = { saveLauncher.launch("ceva-backup-${LocalDate.now()}.zip") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Salvar backup") }
        OutlinedButton(
            enabled = busy == null,
            onClick = { openLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Restaurar um backup") }
    }

    pending?.let { (uri, info) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restaurar este backup?") },
            text = {
                Text(
                    "Backup de ${formatDateTime(info.createdAt)} com ${info.spots} pontos, ${info.catches} peixes e ${info.photos} fotos.\n\n" +
                        "Tudo o que está no celular agora será trocado por ele. Se quiser guardar o que tem hoje, salve um backup antes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    busy = "Restaurando..."
                    scope.launch {
                        message = try {
                            Backup.restore(context, repo, uri)
                            "Backup restaurado: ${info.spots} pontos e ${info.catches} peixes."
                        } catch (e: Exception) {
                            "Não deu para restaurar: ${e.message ?: "erro desconhecido"}"
                        }
                        busy = null
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ImportTrailCard() {
    val context = LocalContext.current
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            }
            val t = text?.let { com.pesca.pontos.trail.TrailShare.decodeFile(it) }
            message = if (t == null) "Esse arquivo não é um caminho do Ceva." else {
                repo.importTrail(t)
                "Caminho \"${t.name}\" recebido."
            }
        }
    }
    SettingsCard {
        Text("Receber caminho por arquivo", style = MaterialTheme.typography.titleSmall)
        Text(
            "Abra aqui o arquivo de caminho que alguém te mandou (ceva-caminho-....json).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        OutlinedButton(
            onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Abrir arquivo de caminho") }
    }
}

@Composable
private fun DriveCard() {
    val context = LocalContext.current
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<DriveBackup.RemoteBackup>?>(null) }
    var chosen by remember { mutableStateOf<Pair<DriveBackup.RemoteBackup, com.pesca.pontos.Backup.Info>?>(null) }
    var pendingAction by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pendingAction
        pendingAction = null
        try {
            val r = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data)
            val token = r.accessToken
            if (token == null) { busy = null; message = "Conexão com o Google cancelada."; return@rememberLauncherForActivityResult }
            settings.setDriveConnected(true)
            if (settings.driveAuto) DriveBackup.schedule(context, true, settings.driveWifiOnly)
            if (action != null) scope.launch { action(token) } else busy = null
        } catch (e: Exception) {
            busy = null
            message = "Conexão com o Google cancelada."
        }
    }

    /** Garante um token (pedindo a tela do Google se preciso) e executa a ação. */
    fun withToken(action: suspend (String) -> Unit) {
        scope.launch {
            message = null
            try {
                val r = DriveBackup.authorize(context)
                if (r.hasResolution()) {
                    pendingAction = action
                    authLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
                } else {
                    settings.setDriveConnected(true)
                    action(r.accessToken!!)
                }
            } catch (e: Exception) {
                busy = null
                message = "Não deu para conectar ao Google: ${e.message ?: "erro"}"
            }
        }
    }

    SettingsCard {
        Text("Google Drive", style = MaterialTheme.typography.titleSmall)
        if (!settings.driveConnected) {
            Text(
                "Guarda uma cópia dos pontos, peixes, caminhos e fotos numa pasta \"Ceva Backups\" no seu Google Drive. " +
                    "O app só enxerga os arquivos que ele mesmo criou.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (settings.lastDriveBackupAt == 0L) "Conectado. Nenhum backup no Drive ainda."
                else "Último backup no Drive: ${formatDateTime(settings.lastDriveBackupAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        busy?.let {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }

        if (!settings.driveConnected) {
            Button(
                enabled = busy == null,
                onClick = {
                    busy = "Conectando..."
                    withToken {
                        busy = null
                        message = "Google Drive conectado."
                        if (settings.driveAuto) DriveBackup.schedule(context, true, settings.driveWifiOnly)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Conectar Google Drive") }
        } else {
            Button(
                enabled = busy == null,
                onClick = {
                    busy = "Enviando backup para o Drive..."
                    withToken { token ->
                        message = try {
                            val info = DriveBackup.backupNow(context, token)
                            "Backup no Drive: ${info.spots} pontos, ${info.catches} peixes e ${info.photos} fotos."
                        } catch (e: Exception) {
                            "Não deu para fazer o backup: ${e.message ?: "erro"}"
                        }
                        busy = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Fazer backup no Drive agora") }
            SwitchRow("Backup automático (1 vez por dia)", settings.driveAuto) {
                settings.updateDriveAuto(it)
                DriveBackup.schedule(context, it, settings.driveWifiOnly)
            }
            SwitchRow("Só no Wi-Fi", settings.driveWifiOnly) {
                settings.updateDriveWifiOnly(it)
                if (settings.driveAuto) DriveBackup.schedule(context, true, it)
            }
            OutlinedButton(
                enabled = busy == null,
                onClick = {
                    busy = "Buscando backups no Drive..."
                    withToken { token ->
                        try {
                            backups = DriveBackup.listBackups(token)
                            if (backups.isNullOrEmpty()) message = "Nenhum backup do Ceva no seu Drive."
                        } catch (e: Exception) {
                            message = e.message
                        }
                        busy = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Restaurar do Drive") }
            TextButton(onClick = {
                settings.setDriveConnected(false)
                DriveBackup.schedule(context, false, false)
                message = "Google Drive desconectado neste app."
            }) { Text("Desconectar") }
        }
    }

    // escolher qual backup restaurar
    backups?.takeIf { it.isNotEmpty() }?.let { list ->
        AlertDialog(
            onDismissRequest = { backups = null },
            title = { Text("Backups no Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    list.forEach { b ->
                        TextButton(
                            onClick = {
                                backups = null
                                busy = "Conferindo o backup..."
                                withToken { token ->
                                    try {
                                        chosen = b to DriveBackup.inspect(context, token, b)
                                    } catch (e: Exception) {
                                        message = e.message
                                    }
                                    busy = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${formatDateTime(b.createdAt)}  ·  " + String.format(java.util.Locale("pt", "BR"), "%.1f MB", b.sizeBytes / 1_000_000.0),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { backups = null }) { Text("Fechar") } },
        )
    }

    chosen?.let { (b, info) ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("Restaurar este backup?") },
            text = {
                Text(
                    "Backup de ${formatDateTime(info.createdAt)} com ${info.spots} pontos, ${info.catches} peixes e ${info.photos} fotos.\n\n" +
                        "Tudo o que está no celular agora será trocado por ele."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chosen = null
                    busy = "Restaurando do Drive..."
                    withToken { token ->
                        message = try {
                            DriveBackup.restore(context, token, b)
                            "Backup restaurado do Drive."
                        } catch (e: Exception) {
                            "Não deu para restaurar: ${e.message ?: "erro"}"
                        }
                        busy = null
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancelar") } },
        )
    }
}

