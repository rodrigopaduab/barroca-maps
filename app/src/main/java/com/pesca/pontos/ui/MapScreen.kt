package com.pesca.pontos.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pesca.pontos.SpotQr
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.SpotSummary
import kotlinx.coroutines.launch

// Centro aproximado do Brasil, usado até o GPS responder
private val BRASIL = LatLng(-15.8, -47.9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNewSpot: (Double, Double) -> Unit,
    onOpenSpot: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val spotsFlow = remember { repo.spots.observeAll() }
    val spots by spotsFlow.collectAsState(initial = emptyList())

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var centeredOnce by rememberSaveable { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(BRASIL, 4f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    // Leitor de QR: importa o ponto e abre o cadastro já preenchido para conferir
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val shared = SpotQr.decode(text)
        if (shared == null) {
            Toast.makeText(context, "Esse QR code não é de um ponto de pesca", Toast.LENGTH_LONG).show()
        } else {
            repo.pendingImport = shared
            onNewSpot(shared.latitude, shared.longitude)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Na primeira abertura, centraliza na posição atual
    LaunchedEffect(hasPermission) {
        if (hasPermission && !centeredOnce) {
            currentLocation(context)?.let { (lat, lng) ->
                camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14f))
                centeredOnce = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                mapType = if (settings.satellite) MapType.HYBRID else MapType.NORMAL,
                isMyLocationEnabled = hasPermission,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasPermission,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            contentPadding = PaddingValues(top = 88.dp, bottom = 110.dp),
            onMapLongClick = { latLng -> onNewSpot(latLng.latitude, latLng.longitude) },
        ) {
            spots.forEach { spot ->
                key(spot.id) {
                    val markerState = remember(spot.latitude, spot.longitude) {
                        MarkerState(position = LatLng(spot.latitude, spot.longitude))
                    }
                    Marker(
                        state = markerState,
                        title = spot.name,
                        icon = pinIcon(spot.color),
                        onClick = {
                            selectedId = spot.id
                            true // não abre o balão padrão; mostramos o nosso cartão
                        },
                    )
                }
            }
        }

        // Barra superior: marca, tipo de mapa, ler QR, configurações
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CevaMark(Modifier.size(width = 22.dp, height = 28.dp))
                Spacer(Modifier.size(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("ceva", style = BrandStyle)
                    Text(
                        when (spots.size) {
                            0 -> "Segure o dedo no mapa para marcar"
                            1 -> "1 ponto salvo"
                            else -> "${spots.size} pontos salvos"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    )
                }
                FilledIconButton(
                    onClick = { settings.updateSatellite(!settings.satellite) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { Icon(Icons.Default.Layers, if (settings.satellite) "Mapa normal" else "Satélite") }
                IconButton(onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Aponte para o QR code do ponto")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }) { Icon(Icons.Default.QrCodeScanner, "Ler QR code de um ponto") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurações") }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .fillMaxWidth(),
        ) {
            if (spots.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "Ou segure o dedo no mapa para marcar",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    } else if (!locating) {
                        locating = true
                        scope.launch {
                            val pos = currentLocation(context)
                            locating = false
                            if (pos != null) onNewSpot(pos.first, pos.second)
                            else Toast.makeText(context, "Não consegui pegar o GPS. Tente de novo ou segure o dedo no mapa.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            ) {
                Icon(Icons.Default.AddLocationAlt, null)
                Text(if (locating) "  Buscando GPS..." else "  Marcar onde estou", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    val selected = spots.firstOrNull { it.id == selectedId }
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            SpotCard(
                spot = selected,
                onNavigate = { openNavigation(context, selected.latitude, selected.longitude) },
                onDetails = {
                    selectedId = null
                    onOpenSpot(selected.id)
                },
            )
        }
    }
}

/** Símbolo do Ceva desenhado em código (pino com boia). */
@Composable
fun CevaMark(modifier: Modifier = Modifier, pin: Color = Ceva.Boia) {
    Canvas(modifier) {
        val sx = size.width / 96f
        val sy = size.height / 120f
        val outer = Path().apply {
            moveTo(48f * sx, 4f * sy)
            cubicTo(25f * sx, 4f * sy, 8f * sx, 21f * sy, 8f * sx, 43f * sy)
            cubicTo(8f * sx, 72f * sy, 48f * sx, 116f * sy, 48f * sx, 116f * sy)
            cubicTo(48f * sx, 116f * sy, 88f * sx, 72f * sy, 88f * sx, 43f * sy)
            cubicTo(88f * sx, 21f * sy, 71f * sx, 4f * sy, 48f * sx, 4f * sy)
            close()
        }
        val lower = Path().apply {
            moveTo(14f * sx, 52f * sy)
            cubicTo(18f * sx, 49f * sy, 86f * sx, 49f * sy, 84f * sx, 52f * sy)
            cubicTo(78f * sx, 70f * sy, 48f * sx, 106f * sy, 48f * sx, 106f * sy)
            cubicTo(48f * sx, 106f * sy, 20f * sx, 70f * sy, 14f * sx, 52f * sy)
            close()
        }
        drawPath(outer, pin)
        drawPath(lower, Ceva.Nevoa)
        drawCircle(Ceva.Mata, radius = 12f * sx, center = Offset(48f * sx, 40f * sy))
    }
}

@Composable
private fun SpotCard(spot: Spot, onNavigate: () -> Unit, onDetails: () -> Unit) {
    val repo = rememberRepo()
    val summaryFlow = remember(spot.id) { repo.catches.observeSummary(spot.id) }
    val summary by summaryFlow.collectAsState(initial = SpotSummary(0, null))

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(14.dp)) { drawCircle(pinColor(spot.color).swatch) }
            Text(spot.name, style = MaterialTheme.typography.headlineMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onDetails,
                label = {
                    Text(
                        when (summary.catchCount) {
                            0 -> "Nenhum peixe"
                            1 -> "1 peixe"
                            else -> "${summary.catchCount} peixes"
                        }
                    )
                },
            )
            summary.lastCatchAt?.let { AssistChip(onClick = onDetails, label = { Text("último em ${formatDate(it).take(5)}") }) }
        }
        PhotoStrip(spot.photos, height = 120)
        if (spot.notes.isNotBlank()) {
            Text(spot.notes, maxLines = 3, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onNavigate,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Icon(Icons.Outlined.Navigation, null, Modifier.size(20.dp))
                Text("  Ir até lá", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onDetails,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text("Detalhes", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        Text(
            "\"Ir até lá\" abre o Google Maps com a rota",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
