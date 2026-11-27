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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberMarkerState
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import com.pesca.pontos.trail.Geo
import com.pesca.pontos.trail.P
import com.pesca.pontos.trail.Recording
import com.pesca.pontos.trail.TrackRecorder
import com.pesca.pontos.trail.TrailShare
import kotlin.math.abs

// Centro aproximado do Brasil, usado até o GPS responder
private val BRASIL = LatLng(-15.8, -47.9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNewSpot: (Double, Double) -> Unit,
    onOpenSpot: (Long) -> Unit,
    onSettings: () -> Unit,
    onList: () -> Unit,
    onEditTrail: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = rememberRepo()
    val settings = rememberSettings()
    val scope = rememberCoroutineScope()
    val spotsFlow = remember { repo.spots.observeAll() }
    val spots by spotsFlow.collectAsState(initial = emptyList())
    val trailsFlow = remember { repo.trails.observeAll() }
    val trails by trailsFlow.collectAsState(initial = emptyList())
    val marksFlow = remember { repo.trails.observeAllMarks() }
    val allMarks by marksFlow.collectAsState(initial = emptyList())
    val recording by TrackRecorder.state.collectAsState()

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var centeredOnce by rememberSaveable { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var myPos by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var compassMode by rememberSaveable { mutableStateOf(false) }
    var toSave by remember { mutableStateOf<Recording?>(null) }
    var followId by rememberSaveable { mutableStateOf<Long?>(null) }
    var askBattery by remember { mutableStateOf(false) }
    var selectedTrailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var trailQr by remember { mutableStateOf<Pair<Trail, List<TrailMark>>?>(null) }
    var newMarkPos by remember { mutableStateOf<P?>(null) }
    val addMarkTrailId = repo.addMarkTrailId

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(BRASIL, 4f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    // Leitor de QR: ponto (abre o cadastro preenchido) ou caminho (pede confirmação)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        if (TrailShare.isTrailQr(text)) {
            val t = TrailShare.decodeQr(text)
            if (t == null) Toast.makeText(context, "Não consegui ler esse caminho", Toast.LENGTH_LONG).show()
            else repo.pendingTrailImport = t
            return@rememberLauncherForActivityResult
        }
        val shared = SpotQr.decode(text)
        if (shared == null) {
            Toast.makeText(context, "Esse QR code não é de um ponto de pesca", Toast.LENGTH_LONG).show()
        } else {
            repo.pendingImport = shared
            onNewSpot(shared.latitude, shared.longitude)
        }
    }

    // Pedido vindo da tela do ponto: seguir um caminho
    LaunchedEffect(repo.followTrailId) {
        repo.followTrailId?.let { followId = it; repo.followTrailId = null }
    }

    // Posição contínua do GPS (painel, seguir caminho, centralizar)
    val live by rememberLocationUpdates(hasPermission, fast = followId != null || recording != null)
    LaunchedEffect(live) { live?.let { myPos = it.lat to it.lng } }

    // Na primeira abertura, centraliza na posição atual
    LaunchedEffect(hasPermission) {
        if (hasPermission && !centeredOnce) {
            currentLocation(context)?.let { (lat, lng) ->
                myPos = lat to lng
                camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
                centeredOnce = true
            }
        }
    }

    // Modo Bússola: o mapa gira conforme a direção do celular
    val heading by rememberHeading(compassMode)
    LaunchedEffect(heading, compassMode) {
        val h = heading ?: return@LaunchedEffect
        if (!compassMode) return@LaunchedEffect
        val pos = camera.position
        if (abs(((h - pos.bearing + 540f) % 360f) - 180f) < 2f) return@LaunchedEffect
        camera.move(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(pos).bearing(h).build()))
    }

    // Caminho sendo seguido
    val followTrail = trails.firstOrNull { it.id == followId }
    val followSpot = followTrail?.spotId?.let { id -> spots.firstOrNull { it.id == id } }
    val followPath = remember(followTrail?.id, followSpot?.id) {
        followTrail?.let { t ->
            val pts = Geo.decode(t.points)
            // orienta o caminho para terminar no ponto (se o ponto estiver no começo, inverte)
            val sp = followSpot
            if (sp != null && pts.size >= 2 &&
                Geo.dist(pts.first(), P(sp.latitude, sp.longitude)) < Geo.dist(pts.last(), P(sp.latitude, sp.longitude))
            ) pts.reversed() else pts
        }.orEmpty()
    }
    val followMarks = allMarks.filter { it.trailId == followId }
    val progress = live?.let { me -> Geo.progress(followPath, P(me.lat, me.lng)) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                mapType = if (settings.satellite) MapType.HYBRID else MapType.NORMAL,
                isMyLocationEnabled = hasPermission,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                compassEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                rotationGesturesEnabled = !compassMode,
            ),
            contentPadding = PaddingValues(top = 88.dp, bottom = 120.dp),
            onMapLongClick = { latLng -> if (recording == null && followId == null && addMarkTrailId == null) onNewSpot(latLng.latitude, latLng.longitude) },
            onMapClick = { latLng ->
                val tid = addMarkTrailId ?: return@GoogleMap
                val t = trails.firstOrNull { it.id == tid } ?: return@GoogleMap
                val path = Geo.decode(t.points)
                val pr = Geo.progress(path, P(latLng.latitude, latLng.longitude))
                if (pr == null || pr.offPathM > 150) {
                    Toast.makeText(context, "Toque mais perto da linha do caminho", Toast.LENGTH_SHORT).show()
                } else {
                    val a = path[pr.segment]
                    val b = path[pr.segment + 1]
                    newMarkPos = P(a.lat + (b.lat - a.lat) * pr.t, a.lng + (b.lng - a.lng) * pr.t)
                }
            },
        ) {
            // caminhos salvos (finos) e o que está sendo seguido (destacado)
            trails.forEach { t ->
                key("trail-${t.id}") {
                    val segs = remember(t.points) { Geo.decodeSegments(t.points).map { seg -> seg.map { LatLng(it.lat, it.lng) } } }
                    if (t.id == followId) {
                        segs.forEach { pts ->
                            Polyline(points = pts, color = Color.White, width = 22f, zIndex = 1f)
                            Polyline(points = pts, color = pinComposeColor(t.color), width = 12f, zIndex = 2f)
                        }
                        progress?.let { pr ->
                            val done = Geo.traveledPart(followPath, pr).map { LatLng(it.lat, it.lng) }
                            if (done.size >= 2) Polyline(points = done, color = Color(0xFF8C968F), width = 12f, zIndex = 3f)
                        }
                    } else {
                        val lineColor = pinComposeColor(t.color)
                        val highlight = t.id == selectedTrailId || t.id == addMarkTrailId
                        segs.forEach { pts ->
                            Polyline(points = pts, color = Color.White.copy(alpha = 0.85f), width = if (highlight) 26f else 18f, zIndex = 1f)
                            Polyline(
                                points = pts, color = lineColor, width = if (highlight) 14f else 9f, zIndex = 1.5f,
                                clickable = recording == null && followId == null && addMarkTrailId == null,
                                onClick = { selectedTrailId = t.id },
                            )
                        }
                    }
                }
            }
            // caminho sendo gravado
            recording?.let { rec ->
                rec.segments().filter { it.size >= 2 }.forEach { seg ->
                    val pts = seg.map { LatLng(it.lat, it.lng) }
                    Polyline(points = pts, color = Color.White, width = 22f, zIndex = 4f)
                    Polyline(points = pts, color = MaterialTheme.colorScheme.secondary, width = 12f, zIndex = 5f)
                }
                rec.marks.forEachIndexed { i, m ->
                    key("rec-mark-$i") {
                        MarkerComposable(m.type, state = rememberMarkerState(position = LatLng(m.p.lat, m.p.lng)), anchor = Offset(0.5f, 0.5f), zIndex = 6f) {
                            MarkBadge(m.type)
                        }
                    }
                }
            }
            // marcações dos caminhos salvos
            allMarks.forEach { m ->
                key("mark-${m.id}") {
                    MarkerComposable(
                        m.type, m.id,
                        state = rememberMarkerState(position = LatLng(m.latitude, m.longitude)),
                        title = m.label,
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = 6f,
                    ) { MarkBadge(m.type) }
                }
            }
            spots.forEach { spot ->
                key(spot.id) {
                    val markerState = remember(spot.latitude, spot.longitude) {
                        MarkerState(position = LatLng(spot.latitude, spot.longitude))
                    }
                    Marker(
                        state = markerState,
                        title = spot.name,
                        icon = pinIcon(spot.color),
                        zIndex = 7f,
                        onClick = {
                            if (recording == null) selectedId = spot.id
                            true // não abre o balão padrão; mostramos o nosso cartão
                        },
                    )
                }
            }
        }

        // Barra superior: marca, tipo de mapa, busca, ler QR, configurações
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
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CevaMark(Modifier.size(width = 22.dp, height = 28.dp))
                Spacer(Modifier.size(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Barroca", style = BrandStyle, maxLines = 1)
                    Text(
                        "MAPS",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                }
                FilledIconButton(
                    onClick = { settings.updateSatellite(!settings.satellite) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { Icon(Icons.Default.Layers, if (settings.satellite) "Mapa normal" else "Satélite") }
                IconButton(onClick = onList) { Icon(Icons.Default.Search, "Lista e busca de pontos") }
                IconButton(onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Aponte para o QR code do ponto ou do caminho")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }) { Icon(Icons.Default.QrCodeScanner, "Ler QR code de ponto ou caminho") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurações") }
            }
        }

        // Faixa do topo (abaixo da barra): gravando / seguindo / painel de pescaria
        val topMod = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(top = 84.dp)
        when {
            addMarkTrailId != null -> Surface(
                color = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.then(topMod).padding(start = 12.dp, end = 72.dp),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Toque no caminho onde a marcação deve ficar", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val id = addMarkTrailId
                        repo.addMarkTrailId = null
                        onEditTrail(id)
                    }) { Text("Cancelar", color = MaterialTheme.colorScheme.onSecondary) }
                }
            }
            recording != null -> RecordingBanner(recording!!, Modifier.then(topMod).padding(start = 12.dp, end = 72.dp))
            followTrail != null && progress != null -> Column(topMod.padding(start = 12.dp, end = 72.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val nextPoint = followPath.getOrNull(progress.segment + 1)
                val bearing = nextPoint?.let { np -> live?.let { me -> bearingTo(me, np) - camera.position.bearing } }
                FollowTop(progress.remainingM, bearing, followSpot?.name ?: "o fim do caminho")
                if (progress.offPathM > 50) OffPathBanner(progress.offPathM)
            }
            followTrail != null -> FollowTop(Geo.length(followPath), null, followSpot?.name ?: "o fim do caminho", Modifier.then(topMod).padding(start = 12.dp, end = 72.dp))
            else -> {
                val panelPos = myPos ?: camera.position.target.takeIf { camera.position.zoom > 6f }?.let { it.latitude to it.longitude }
                FishingPanel(lat = panelPos?.first, lng = panelPos?.second, modifier = topMod)
            }
        }

        // Bússola e centralizar (lado direito, abaixo da barra)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 84.dp, end = 12.dp),
        ) {
            CompassButton(camera.position.bearing, compassMode) {
                compassMode = !compassMode
                if (!compassMode) scope.launch {
                    camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera.position).bearing(0f).build()))
                }
            }
            CenterButton {
                val pos = myPos
                if (!hasPermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else if (pos != null) {
                    scope.launch {
                        val z = camera.position.zoom.coerceAtLeast(15f)
                        camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera.position).target(LatLng(pos.first, pos.second)).zoom(z).build()))
                    }
                } else {
                    Toast.makeText(context, "Procurando o GPS...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Parte de baixo
        val bottomMod = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        when {
            addMarkTrailId != null -> Unit
            recording != null -> RecordingControls(
                rec = recording!!,
                onMark = { type, label ->
                    if (!TrackRecorder.addMark(type, label)) Toast.makeText(context, "Aguardando o GPS para marcar", Toast.LENGTH_SHORT).show()
                },
                onPause = { TrackRecorder.togglePause() },
                onStop = { toSave = TrackRecorder.stop(context) },
                modifier = bottomMod,
            )
            followTrail != null -> {
                val traveled = progress?.traveledM ?: 0.0
                val next = followMarks
                    .map { it to Geo.alongPath(followPath, P(it.latitude, it.longitude)) }
                    .filter { it.second > traveled + 5 }
                    .minByOrNull { it.second }
                    ?.let { it.first to (it.second - traveled) }
                FollowBottom(followTrail.name, next, onStop = { followId = null }, modifier = bottomMod)
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "Ou segure o dedo no mapa para marcar um ponto",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(Icons.Default.AddLocationAlt, null)
                        Text(if (locating) " GPS..." else " Marcar ponto", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else if (!ignoringBattery(context)) {
                                askBattery = true
                            } else {
                                TrackRecorder.start(context)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Box(Modifier.size(12.dp).background(Color(0xFFFF4D4D), CircleShape))
                        Text("  Gravar caminho", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }

    // Salvar caminho gravado
    toSave?.let { rec ->
        val last = rec.points.lastOrNull()
        val suggested = last?.let { p -> spots.minByOrNull { Geo.dist(p, P(it.latitude, it.longitude)) }?.takeIf { Geo.dist(p, P(it.latitude, it.longitude)) < 300 }?.id }
        SaveTrailDialog(
            rec = rec,
            spots = spots,
            suggestedSpotId = suggested,
            onSave = { name, spotId ->
                toSave = null
                scope.launch {
                    repo.saveTrail(
                        Trail(name = name, spotId = spotId, createdAt = rec.startedAt, durationMs = rec.elapsedMs(), distanceM = rec.distanceM, points = Geo.encodeSegments(rec.segments())),
                        rec.marks.map { TrailMark(trailId = 0, latitude = it.p.lat, longitude = it.p.lng, type = it.type, label = it.label, at = it.at) },
                    )
                    Toast.makeText(context, "Caminho salvo", Toast.LENGTH_SHORT).show()
                }
            },
            onDiscard = { toSave = null },
        )
    }

    // Pede para tirar o app da economia de bateria (senão o GPS para com a tela desligada)
    if (askBattery) {
        AlertDialog(
            onDismissRequest = { askBattery = false },
            title = { Text("Gravar com a tela desligada") },
            text = {
                Text(
                    "Para o celular não parar o GPS quando a tela apagar, libere o Barroca Maps da economia de bateria.\n\n" +
                        "Em celulares Samsung, confira também: Configurações › Aplicativos › Barroca Maps › Bateria › Sem restrições."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false
                    requestIgnoreBattery(context)
                    TrackRecorder.start(context)
                }) { Text("Liberar e gravar") }
            },
            dismissButton = {
                TextButton(onClick = { askBattery = false; TrackRecorder.start(context) }) { Text("Gravar assim mesmo") }
            },
        )
    }

    // Escolher o tipo da marcação nova
    newMarkPos?.let { p ->
        AddMarkDialog(onDismiss = { newMarkPos = null }) { type, label ->
            val tid = addMarkTrailId
            newMarkPos = null
            if (tid != null) scope.launch {
                repo.trails.insertMark(TrailMark(trailId = tid, latitude = p.lat, longitude = p.lng, type = type, label = label))
                repo.addMarkTrailId = null
                Toast.makeText(context, "Marcação adicionada", Toast.LENGTH_SHORT).show()
                onEditTrail(tid)
            }
        }
    }

    // Cartão do caminho tocado no mapa
    trails.firstOrNull { it.id == selectedTrailId }?.let { t ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedTrailId = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            TrailSheetContent(
                trail = t,
                spot = spots.firstOrNull { it.id == t.spotId },
                marks = allMarks.count { it.trailId == t.id },
                onFollow = { selectedTrailId = null; followId = t.id },
                onEdit = { selectedTrailId = null; onEditTrail(t.id) },
                onQr = { scope.launch { trailQr = t to repo.trails.marks(t.id) } },
            )
        }
    }
    trailQr?.let { (t, m) -> TrailQrDialog(t, m, spots.firstOrNull { it.id == t.spotId }) { trailQr = null } }

    // Caminho recebido por QR
    repo.pendingTrailImport?.let { t ->
        AlertDialog(
            onDismissRequest = { repo.pendingTrailImport = null },
            title = { Text("Receber caminho?") },
            text = {
                Text(
                    "\"${t.name}\" · ${Geo.formatDistance(Geo.length(t.points))} · ${t.marks.size} marcações" +
                        (t.spot?.let { "\nLeva ao ponto \"${it.name}\", que também será salvo se você ainda não tiver." } ?: "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.pendingTrailImport = null
                    scope.launch {
                        val id = repo.importTrail(t)
                        followId = null
                        Toast.makeText(context, "Caminho salvo", Toast.LENGTH_SHORT).show()
                        t.points.firstOrNull()?.let { p -> camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 14f)) }
                        if (id < 0) Unit
                    }
                }) { Text("Salvar caminho") }
            },
            dismissButton = { TextButton(onClick = { repo.pendingTrailImport = null }) { Text("Cancelar") } },
        )
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

/** Direção (graus, 0 = norte) de "a" até "b". */
private fun bearingTo(a: P, b: P): Float {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val y = kotlin.math.sin(dLng) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) - kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLng)
    return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360).toFloat()
}

/** Símbolo do Ceva desenhado em código: pino com a boia na água (o mesmo desenho do ícone). */
private object MarkPaths {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val pin = p("M48,4C25,4 8,21 8,43c0,29 40,73 40,73s40,-44 40,-73C88,21 71,4 48,4z")
    val window = p("M48,44m-31,0a31,31 0,1 1,62 0a31,31 0,1 1,-62 0")
    val water = p("M10,50q9.5,-5 19,0t19,0t19,0t19,0V90H10z")
    val ripple1 = p("M28,61q10,4 20,0t20,0")
    val ripple2 = p("M34,70q7,3 14,0t14,0")
    val bobber = p("M35,50a13,13 0,0 1,26 0z")
}

@Composable
fun CevaMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = minOf(size.width / 96f, size.height / 120f)
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            drawPath(MarkPaths.pin, Ceva.Boia)
            drawPath(MarkPaths.window, Ceva.Mata)
            clipPath(MarkPaths.window) {
                drawPath(MarkPaths.water, Ceva.Nevoa)
                drawPath(MarkPaths.ripple1, Ceva.Mata, style = Stroke(width = 3f, cap = StrokeCap.Round))
                drawPath(MarkPaths.ripple2, Ceva.Mata, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
            drawPath(MarkPaths.bobber, Ceva.Boia)
            drawPath(MarkPaths.bobber, Ceva.Mata, style = Stroke(width = 3f))
            drawLine(Ceva.Nevoa, Offset(48f, 37f), Offset(48f, 21f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        }
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
            Canvas(Modifier.size(14.dp)) { drawCircle(pinComposeColor(spot.color)) }
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

private fun ignoringBattery(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)

@android.annotation.SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: android.content.Context) {
    val direct = android.content.Intent(
        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        android.net.Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(direct)
    } catch (e: Exception) {
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}
