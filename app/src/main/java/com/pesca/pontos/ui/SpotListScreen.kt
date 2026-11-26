package com.pesca.pontos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.Normalizer

/** Todos os pontos, com busca por texto e filtro pelo peixe principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotListScreen(onBack: () -> Unit, onOpenSpot: (Long) -> Unit) {
    val repo = rememberRepo()
    val spotsFlow = remember { repo.spots.observeAll() }
    val fishFlow = remember { repo.spots.observeMainFish() }
    val countsFlow = remember { repo.catches.observeCounts() }
    val spots by spotsFlow.collectAsState(initial = emptyList())
    val fishes by fishFlow.collectAsState(initial = emptyList())
    val counts by countsFlow.collectAsState(initial = emptyList())
    val countBySpot = remember(counts) { counts.associate { it.spotId to it.n } }

    var query by rememberSaveable { mutableStateOf("") }
    var fishFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val q = normalize(query)
    val shown = spots.filter { s ->
        (fishFilter == null || s.mainFish.equals(fishFilter, ignoreCase = true)) &&
            (q.isEmpty() || normalize(s.name).contains(q) || normalize(s.notes).contains(q) || normalize(s.mainFish).contains(q))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus pontos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar ao mapa") }
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
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar por nome, informação ou peixe") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpar busca") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fishes.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = fishFilter == null, onClick = { fishFilter = null }, label = { Text("Todos") })
                        fishes.forEach { f ->
                            FilterChip(
                                selected = fishFilter.equals(f, ignoreCase = true),
                                onClick = { fishFilter = if (fishFilter.equals(f, ignoreCase = true)) null else f },
                                label = { Text(f) },
                            )
                        }
                    }
                }
                Text(
                    when {
                        spots.isEmpty() -> "Nenhum ponto salvo ainda."
                        shown.isEmpty() -> "Nenhum ponto encontrado."
                        shown.size == 1 -> "1 ponto"
                        else -> "${shown.size} pontos"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { s ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSpot(s.id) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(16.dp)) { drawCircle(pinComposeColor(s.color)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = countBySpot[s.id] ?: 0
                                val line = listOfNotNull(
                                    s.mainFish.takeIf { it.isNotBlank() },
                                    if (n == 1) "1 peixe" else "$n peixes",
                                    formatDate(s.createdAt),
                                ).joinToString(" · ")
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (s.notes.isNotBlank()) {
                                    Text(s.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Busca sem diferenciar maiúsculas e acentos ("traira" acha "Traíra"). */
private fun normalize(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
