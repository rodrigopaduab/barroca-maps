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
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(spotId == null) }
    var original by remember { mutableStateOf<Spot?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lng by rememberSaveable { mutableStateOf(initialLng) }
    var photos by rememberSaveable { mutableStateOf(listOf<String>()) }
    var color by rememberSaveable { mutableStateOf(0) }
    var imported by rememberSaveable { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(spotId) {
        // Ponto vindo de um QR code: já chega preenchido
        if (spotId == null) {
            repo.pendingImport?.let { shared ->
                name = shared.name; notes = shared.notes; lat = shared.latitude; lng = shared.longitude
                color = shared.color.coerceIn(0, PIN_COLORS.lastIndex)
                imported = true
            }
            repo.pendingImport = null
        }
        if (spotId != null) {
            val s = repo.spots.get(spotId) ?: return@LaunchedEffect onBack()
            original = s
            if (!loaded) {
                name = s.name; notes = s.notes; lat = s.latitude; lng = s.longitude; photos = s.photos; color = s.color
                loaded = true
            }
        }
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cor do pino", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PIN_COLORS.forEachIndexed { i, pc ->
                        val selected = i == color
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(pc.swatch)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable(onClickLabel = pc.name) { color = i }
                                .semantics { contentDescription = pc.name + if (selected) ", escolhida" else "" },
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
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
                        val base = original ?: Spot(name = "", latitude = lat, longitude = lng)
                        val spot = base.copy(
                            name = name.trim(),
                            notes = notes.trim(),
                            latitude = lat,
                            longitude = lng,
                            photos = photos,
                            color = color,
                        )
                        val id = repo.saveSpot(spot, original?.photos ?: emptyList())
                        onSaved(id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Salvando..." else "Salvar ponto") }
        }
    }
}
