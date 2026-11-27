package com.pesca.pontos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import com.pesca.pontos.trail.Geo
import com.pesca.pontos.trail.Recording
import com.pesca.pontos.trail.TrailShare
import kotlinx.coroutines.delay

private val Dark = Color(0xF010241B)

/** Tipos de marcação no caminho. */
data class MarkType(val key: String, val label: String, val icon: ImageVector)

val MARK_TYPES = listOf(
    MarkType("PORTEIRA", "Porteira", Icons.Filled.ViewWeek),
    MarkType("ATOLEIRO", "Atoleiro", Icons.Filled.Waves),
    MarkType("CARRO", "Carro", Icons.Filled.DirectionsCar),
    MarkType("PONTE", "Ponte", Icons.Filled.Landscape),
    MarkType("SEM_SINAL", "Sem sinal", Icons.Filled.SignalCellularOff),
)

fun markIcon(type: String): ImageVector = MARK_TYPES.firstOrNull { it.key == type }?.icon ?: Icons.Filled.Edit

/** Bolinha da marcação desenhada no mapa. */
@Composable
fun MarkBadge(type: String) {
    Box(
        Modifier
            .size(28.dp)
            .background(Ceva.Nevoa, CircleShape)
            .padding(2.dp)
            .background(Ceva.Boia, CircleShape)
            .padding(2.dp)
            .background(Ceva.Nevoa, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(markIcon(type), null, tint = Ceva.Mata, modifier = Modifier.size(15.dp)) }
}

private fun minutes(ms: Long) = "${(ms / 60_000).coerceAtLeast(0)} min"

// ---------------- gravação ----------------

/** Faixa no topo durante a gravação. */
@Composable
fun RecordingBanner(rec: Recording, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Surface(color = Dark, contentColor = Ceva.Nevoa, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (rec.paused) Color(0xFFB8C4BC) else Color(0xFFFF4D4D), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (rec.paused) "Gravação pausada" else "Gravando caminho", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${Geo.formatDistance(rec.distanceM)} · ${minutes(rec.elapsedMs(now))} · ${rec.marks.size} marcaç${if (rec.marks.size == 1) "ão" else "ões"}",
                    fontSize = 12.sp, color = Color(0xFFB8C4BC),
                )
            }
        }
    }
}

/** Painel de baixo durante a gravação: marcações, pausar e parar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingControls(
    rec: Recording,
    onMark: (type: String, label: String) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var writing by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = modifier) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Marcar aqui:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                MARK_TYPES.forEach { t ->
                    AssistChip(onClick = { onMark(t.key, t.label) }, label = { Text(t.label) }, leadingIcon = { Icon(t.icon, null, Modifier.size(16.dp)) })
                }
                AssistChip(onClick = { writing = true }, label = { Text("Escrever…") }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPause, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(if (rec.paused) "Continuar" else "Pausar", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Parar e salvar", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("Escrever marcação") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(30) }, singleLine = true, placeholder = { Text("Ex.: Cerca elétrica") })
                    Text("Fica marcada neste ponto do caminho.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { onMark("TEXTO", text.trim()); writing = false }) { Text("Marcar") }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("Cancelar") } },
        )
    }
}

/** Salvar o caminho gravado e escolher o ponto ao qual ele leva. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTrailDialog(
    rec: Recording,
    spots: List<Spot>,
    suggestedSpotId: Long?,
    onSave: (name: String, spotId: Long?) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var spotId by remember { mutableStateOf(suggestedSpotId) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Salvar caminho", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(Geo.formatDistance(rec.distanceM), "distância", Modifier.weight(1f))
                    Stat(minutes(rec.elapsedMs()), "tempo", Modifier.weight(1f))
                    Stat(rec.marks.size.toString(), "marcações", Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text("Nome do caminho") }, placeholder = { Text("Ex.: Entrada pela fazenda") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = spots.firstOrNull { it.id == spotId }?.name ?: "Nenhum ponto",
                        onValueChange = {}, readOnly = true, label = { Text("Ligar ao ponto") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Nenhum ponto") }, onClick = { spotId = null; expanded = false })
                        spots.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { spotId = s.id; expanded = false })
                        }
                    }
                }
                if (rec.points.size < 2) {
                    Text("Ainda não há caminho gravado (o GPS não registrou deslocamento).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { confirmDiscard = true }, modifier = Modifier.weight(1f).height(50.dp)) { Text("Descartar") }
                    Button(
                        enabled = rec.points.size >= 2,
                        onClick = { onSave(name.trim().ifEmpty { "Caminho de ${formatDate(rec.startedAt)}" }, spotId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Salvar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar caminho?") },
            text = { Text("O caminho gravado será perdido.") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- seguindo um caminho ----------------

@Composable
fun FollowTop(remainingM: Double, bearingToNext: Float?, destination: String, modifier: Modifier = Modifier) {
    Surface(color = Ceva.Boia, contentColor = Ceva.Mata, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Ceva.Nevoa, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Navigation, "Direção", tint = Ceva.Mata, modifier = Modifier.size(28.dp).rotate(bearingToNext ?: 0f))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(Geo.formatDistance(remainingM), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("até $destination, pelo caminho", fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
fun OffPathBanner(offM: Double, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFFB3261E), contentColor = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Text(
            "Você saiu do caminho: está a ${Geo.formatDistance(offM)} dele",
            fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun FollowBottom(trailName: String, next: Pair<TrailMark, Double>?, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = modifier) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) {
                        Icon(markIcon(next.first.type), null, Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            if (next != null) "${next.first.label} em ${Geo.formatDistance(next.second)}" else "Sem marcações pela frente",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("$trailName · funciona sem internet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Encerrar", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------------- QR do caminho ----------------

@Composable
fun TrailQrDialog(trail: Trail, marks: List<TrailMark>, spot: Spot?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = remember(trail, marks, spot) { TrailShare.qrText(trail, marks, spot) }
    val bmp = remember(content) { TrailShare.qrBitmap(content).asImageBitmap() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(trail.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    listOfNotNull(Geo.formatDistance(trail.distanceM), "${marks.size} marcações", spot?.let { "leva ao ${it.name}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Box(Modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Image(bmp, "QR code do caminho", filterQuality = FilterQuality.None, modifier = Modifier.size(260.dp))
                }
                Text(
                    "Quem tem o Barroca Maps lê pelo botão de QR no mapa e recebe o caminho, as marcações e o ponto. Para caminhos longos, envie como arquivo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Button(onClick = { TrailShare.shareQrImage(context, trail, content) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Share, null); Text("  Enviar como imagem")
                }
                OutlinedButton(onClick = { TrailShare.shareFile(context, trail, marks, spot) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Enviar como arquivo")
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    }
}

// ---------------- controles do mapa ----------------

/** Bússola: toque alterna entre modo Norte e modo Bússola (mapa gira com o celular). */
@Composable
fun CompassButton(mapBearing: Float, compassMode: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (compassMode) Ceva.Boia else Dark,
            shadowElevation = 4.dp,
            modifier = Modifier.size(46.dp),
        ) {
            val north = if (compassMode) Ceva.Mata else Ceva.Boia
            Canvas(Modifier.fillMaxSize().rotate(-mapBearing)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2
                val n = Path().apply { moveTo(cx, cy - r * 0.62f); lineTo(cx + r * 0.2f, cy); lineTo(cx - r * 0.2f, cy); close() }
                val s = Path().apply { moveTo(cx, cy + r * 0.62f); lineTo(cx + r * 0.2f, cy); lineTo(cx - r * 0.2f, cy); close() }
                drawPath(n, north)
                drawPath(s, Ceva.Nevoa)
                drawCircle(Ceva.Mata, r * 0.09f, Offset(cx, cy))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            if (compassMode) "Bússola" else "Norte",
            color = Ceva.Nevoa, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Dark, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun CenterButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Dark, shadowElevation = 4.dp, modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Navigation, "Centralizar em mim", tint = Ceva.Boia, modifier = Modifier.size(22.dp).rotate(45f))
        }
    }
}

