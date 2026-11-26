package com.pesca.pontos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pesca.pontos.Weather
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import com.pesca.pontos.trail.Geo
import com.pesca.pontos.data.Catch
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spotId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddCatch: () -> Unit,
    onOpenCatch: (Long) -> Unit,
    onDeleted: () -> Unit,
    onFollowTrail: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val spotFlow = remember(spotId) { repo.spots.observe(spotId) }
    val catchesFlow = remember(spotId) { repo.catches.observeForSpot(spotId) }
    val spot by spotFlow.collectAsState(initial = null)
    val catches by catchesFlow.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val trailsFlow = remember(spotId) { repo.trails.observeForSpot(spotId) }
    val trails by trailsFlow.collectAsState(initial = emptyList())
    var qrTrail by remember { mutableStateOf<Pair<Trail, List<TrailMark>>?>(null) }
    var deleteTrail by remember { mutableStateOf<Trail?>(null) }

    LaunchedEffect(spotId) { repo.fillMissingWeather() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spot?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = { showQr = true }) { Icon(Icons.Default.QrCode2, "Compartilhar por QR code") }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Apagar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCatch,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Registrar peixe") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        },
    ) { padding ->
        val s = spot ?: return@Scaffold
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.photos.isNotEmpty()) item { PhotoStrip(s.photos, height = 200) }

            item {
                Button(
                    onClick = { openNavigation(context, s.latitude, s.longitude) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.Directions, null)
                    Text("  Ir até lá (Google Maps)")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (s.mainFish.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Canvas(Modifier.size(12.dp)) { drawCircle(pinComposeColor(s.color)) }
                            Text("Peixe principal: ${s.mainFish}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Criado em ${formatDateTime(s.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (s.weatherFetched) "Clima no dia: " + Weather.summary(s.weatherTemp, s.weatherCode, s.weatherWind, s.weatherHumidity)
                        else "Clima no dia: será preenchido quando houver internet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.weatherFetched) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatCoord(s.latitude, s.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (trails.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.size(8.dp))
                    Text("Caminhos até aqui (${trails.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(trails, key = { "t${it.id}" }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Geo.formatDistance(t.distanceM)} · ${t.durationMs / 60_000} min · gravado em ${formatDate(t.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onFollowTrail(t.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Seguir caminho") }
                                IconButton(onClick = { scope.launch { qrTrail = t to repo.trails.marks(t.id) } }) {
                                    Icon(Icons.Default.QrCode2, "Compartilhar caminho")
                                }
                                IconButton(onClick = { deleteTrail = t }) { Icon(Icons.Default.Delete, "Apagar caminho") }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                Text(
                    "Peixes (${catches.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (catches.isEmpty()) {
                item {
                    Text(
                        "Nenhum peixe registrado aqui ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(catches, key = { it.id }) { c -> CatchRow(c) { onOpenCatch(c.id) } }
        }
    }

    if (showQr) spot?.let { QrDialog(it) { showQr = false } }

    qrTrail?.let { (t, marks) -> TrailQrDialog(t, marks, spot) { qrTrail = null } }

    deleteTrail?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTrail = null },
            title = { Text("Apagar caminho?") },
            text = { Text("\"${t.name}\" e as marcações dele serão apagados.") },
            confirmButton = {
                TextButton(onClick = { deleteTrail = null; scope.launch { repo.trails.delete(t) } }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { deleteTrail = null }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar ponto?") },
            text = { Text("O ponto, as fotos e todos os peixes registrados nele serão apagados. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val s = spot ?: return@TextButton
                    scope.launch {
                        repo.deleteSpot(s)
                        onDeleted()
                    }
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CatchRow(c: Catch, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.photos.isNotEmpty()) {
                AsyncImage(
                    model = File(c.photos.first()),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(Icons.Default.Phishing, null, Modifier.size(72.dp).padding(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.species, style = MaterialTheme.typography.titleMedium)
                val medidas = listOfNotNull(
                    c.weightKg?.let { "${formatNumber(it)} kg" },
                    c.lengthCm?.let { "${formatNumber(it)} cm" },
                ).joinToString(" · ")
                if (medidas.isNotEmpty()) Text(medidas, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatDateTime(c.caughtAt) + if (c.bait.isNotBlank()) " · isca: ${c.bait}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.weatherFetched) {
                    Text(
                        listOfNotNull(c.weatherTemp?.let { "${it.toInt()}°C" }, Weather.describe(c.weatherCode).takeIf { c.weatherCode != null })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (c.released) {
                AssistChip(onClick = onClick, label = { Text("Solto") })
            }
        }
    }
}
