package com.pesca.pontos.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.pesca.pontos.Weather
import com.pesca.pontos.data.Spot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpotEditScreen(
    spotId: Long?,
    initialLat: Double,
    initialLng: Double,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(spotId == null) }
    var original by remember { mutableStateOf<Spot?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lng by rememberSaveable { mutableStateOf(initialLng) }
    var photos by rememberSaveable { mutableStateOf(listOf<String>()) }
    var color by rememberSaveable { mutableStateOf(DEFAULT_PIN_COLOR) }
    var mainFish by rememberSaveable { mutableStateOf("") }
    var createdAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var imported by rememberSaveable { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showWheel by remember { mutableStateOf(false) }
    var fishOptions by remember { mutableStateOf(COMMON_SPECIES) }

    // Clima: null em weatherFetched = ainda buscando
    var weather by remember { mutableStateOf<Weather.Data?>(null) }
    var weatherFetched by remember { mutableStateOf(false) }
    var weatherLoading by remember { mutableStateOf(false) }

    LaunchedEffect(spotId) {
        val mine = repo.spots.listAll().map { it.mainFish } + repo.catches.allSpecies()
        fishOptions = (mine.filter { it.isNotBlank() } + COMMON_SPECIES).distinctBy { it.lowercase() }
        // Ponto vindo de um QR code: já chega preenchido
        if (spotId == null) {
            repo.pendingImport?.let { shared ->
                name = shared.name; notes = shared.notes; lat = shared.latitude; lng = shared.longitude
                color = resolvePinColor(shared.color)
                mainFish = shared.mainFish
                imported = true
            }
            repo.pendingImport = null
        }
        if (spotId != null) {
            val s = repo.spots.get(spotId) ?: return@LaunchedEffect onBack()
            original = s
            if (!loaded) {
                name = s.name; notes = s.notes; lat = s.latitude; lng = s.longitude; photos = s.photos
                color = resolvePinColor(s.color); mainFish = s.mainFish; createdAt = s.createdAt
                loaded = true
            }
            if (s.weatherFetched) {
                weather = Weather.Data(s.weatherTemp, s.weatherCode, s.weatherWind, s.weatherHumidity)
                weatherFetched = true
            }
        }
    }

    // Busca o clima do momento (ponto novo) ou do dia em que o ponto foi criado (se ainda faltar)
    LaunchedEffect(lat, lng, loaded) {
        if (!loaded || weatherFetched && spotId != null) return@LaunchedEffect
        weatherLoading = true
        val w = Weather.fetch(lat, lng, createdAt)
        weatherLoading = false
        if (w != null) { weather = w; weatherFetched = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (spotId == null) "Novo ponto" else "Editar ponto") },
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
            if (imported) {
                Text(
                    "Ponto recebido por QR code. Confira e toque em Salvar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Nome do ponto *") },
                placeholder = { Text("Ex.: Curva do Rio Uberabinha") },
                isError = nameError,
                supportingText = { if (nameError) Text("Dê um nome ao ponto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Informações") },
                placeholder = { Text("Acesso, profundidade, estrutura, onde deixar o carro...") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Localização", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatCoord(lat, lng), modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (!hasLocationPermission(context)) {
                            Toast.makeText(context, "Permita o acesso à localização", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        scope.launch {
                            currentLocation(context)?.let { (a, b) -> lat = a; lng = b }
                                ?: Toast.makeText(context, "GPS sem sinal", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, null)
                        Text("  Usar onde estou")
                    }
                }
            }

            // Data e clima
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text("Criado em", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatDateTime(createdAt), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Clima no dia", style = MaterialTheme.typography.titleSmall)
                    val w = weather
                    Text(
                        when {
                            w != null -> Weather.summary(w.tempC, w.code, w.windKmh, w.humidity)
                            weatherLoading -> "Buscando o clima..."
                            else -> "Sem internet agora. O clima será preenchido sozinho quando houver conexão."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (w != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Peixe principal
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mainFish,
                    onValueChange = { mainFish = it },
                    label = { Text("Peixe principal") },
                    placeholder = { Text("O peixe que mais sai aqui") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                val allFish = (settings.customFish + fishOptions).distinctBy { it.lowercase() }
                val typed = mainFish.trim()
                if (typed.isNotEmpty() && allFish.none { it.equals(typed, ignoreCase = true) }) {
                    AssistChip(
                        onClick = { settings.addFish(typed) },
                        label = { Text("Adicionar \"$typed\" à lista de peixes") },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allFish.forEach { sp ->
                        FilterChip(
                            selected = mainFish.equals(sp, ignoreCase = true),
                            onClick = { mainFish = if (mainFish.equals(sp, ignoreCase = true)) "" else sp },
                            label = { Text(sp) },
                        )
                    }
                }
            }

            // Cor do pino
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cor do pino", style = MaterialTheme.typography.titleSmall)
                val presetIndex = PIN_COLORS.indexOfFirst { it.swatch.toArgb() == color }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PIN_COLORS.forEachIndexed { i, pc ->
                        ColorDot(
                            label = pc.name,
                            selected = i == presetIndex,
                            modifier = Modifier.background(pc.swatch),
                        ) { color = pc.swatch.toArgb() }
                    }
                    // roda de cores: mostra a cor livre escolhida no meio
                    ColorDot(
                        label = "Outra cor",
                        selected = presetIndex < 0,
                        modifier = Modifier.background(Brush.sweepGradient(RAINBOW)),
                    ) { showWheel = true }
                }
                if (presetIndex < 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
                        Text("Cor escolhida na roda", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            PhotoEditor(photos = photos, onChange = { photos = it })

            Button(
                enabled = !saving,
                onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    saving = true
                    scope.launch {
                        val base = original ?: Spot(name = "", latitude = lat, longitude = lng, createdAt = createdAt)
                        val w = weather
                        val spot = base.copy(
                            name = name.trim(),
                            notes = notes.trim(),
                            latitude = lat,
                            longitude = lng,
                            photos = photos,
                            color = color,
                            mainFish = mainFish.trim(),
                            weatherTemp = w?.tempC ?: base.weatherTemp,
                            weatherCode = w?.code ?: base.weatherCode,
                            weatherWind = w?.windKmh ?: base.weatherWind,
                            weatherHumidity = w?.humidity ?: base.weatherHumidity,
                            weatherFetched = weatherFetched || base.weatherFetched,
                        )
                        if (spot.mainFish.isNotEmpty()) settings.addFish(spot.mainFish)
                        val id = repo.saveSpot(spot, original?.photos ?: emptyList())
                        onSaved(id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Salvando..." else "Salvar ponto") }
        }
    }

    if (showWheel) {
        ColorWheelDialog(
            initial = color,
            onDismiss = { showWheel = false },
            onPick = { color = it; showWheel = false },
        )
    }
}

private val RAINBOW = List(7) { Color.hsv(it * 60f % 360f, 0.9f, 1f) }

@Composable
private fun ColorDot(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(modifier)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label + if (selected) ", escolhida" else "" },
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
