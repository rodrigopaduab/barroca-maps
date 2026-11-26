package com.pesca.pontos.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pesca.pontos.data.Catch
import kotlinx.coroutines.launch
import java.util.Calendar

/** Espécies comuns em água doce no Brasil, para preencher com um toque. */
val COMMON_SPECIES = listOf(
    "Tucunaré", "Traíra", "Tilápia", "Pacu", "Dourado", "Pintado", "Piau",
    "Lambari", "Curimba", "Matrinxã", "Tambaqui", "Corvina", "Bagre", "Piranha",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchEditScreen(
    catchId: Long?,
    spotId: Long?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(catchId == null) }
    var original by remember { mutableStateOf<Catch?>(null) }
    var species by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf("") }
    var caughtAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var bait by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var released by rememberSaveable { mutableStateOf(false) }
    var photos by rememberSaveable { mutableStateOf(listOf<String>()) }
    var speciesError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var knownSpecies by remember { mutableStateOf(COMMON_SPECIES) }

    LaunchedEffect(catchId) {
        val mine = repo.catches.allSpecies()
        knownSpecies = (settings.customFish + mine + COMMON_SPECIES).distinctBy { it.lowercase() }
        if (catchId != null) {
            val c = repo.catches.get(catchId) ?: return@LaunchedEffect onDone()
            original = c
            if (!loaded) {
                species = c.species
                weight = c.weightKg?.let(::formatNumber) ?: ""
                length = c.lengthCm?.let(::formatNumber) ?: ""
                caughtAt = c.caughtAt
                bait = c.bait
                notes = c.notes
                released = c.released
                photos = c.photos
                loaded = true
            }
        }
    }

    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = caughtAt }
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                cal.set(y, m, d, h, min, 0)
                caughtAt = cal.timeInMillis
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (catchId == null) "Registrar peixe" else "Editar peixe") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (catchId != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Apagar") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = species,
                onValueChange = { species = it; speciesError = false },
                label = { Text("Espécie *") },
                isError = speciesError,
                supportingText = { if (speciesError) Text("Informe a espécie") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                knownSpecies.forEach { sp ->
                    FilterChip(
                        selected = species.equals(sp, ignoreCase = true),
                        onClick = { species = sp; speciesError = false },
                        label = { Text(sp) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text("Tamanho (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Schedule, null)
                Text("  Hora: ${formatDateTime(caughtAt)}")
            }

            OutlinedTextField(
                value = bait,
                onValueChange = { bait = it },
                label = { Text("Isca") },
                placeholder = { Text("Ex.: minhoca, isca artificial de superfície") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Peixe foi solto (pesque e solte)", modifier = Modifier.weight(1f))
                Switch(checked = released, onCheckedChange = { released = it })
            }

            PhotoEditor(photos = photos, onChange = { photos = it })

            Button(
                enabled = !saving,
                onClick = {
                    if (species.isBlank()) { speciesError = true; return@Button }
                    val targetSpot = original?.spotId ?: spotId ?: return@Button
                    saving = true
                    scope.launch {
                        val base = original ?: Catch(spotId = targetSpot, species = "")
                        val c = base.copy(
                            species = species.trim(),
                            weightKg = parseNumber(weight),
                            lengthCm = parseNumber(length),
                            caughtAt = caughtAt,
                            bait = bait.trim(),
                            notes = notes.trim(),
                            released = released,
                            photos = photos,
                        )
                        settings.addFish(c.species)
                        repo.saveCatch(c, original?.photos ?: emptyList())
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Salvando..." else "Salvar peixe") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar este peixe?") },
            text = { Text("O registro e as fotos dele serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val c = original ?: return@TextButton
                    scope.launch {
                        repo.deleteCatch(c)
                        onDone()
                    }
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
