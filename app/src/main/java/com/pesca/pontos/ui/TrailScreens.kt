package com.pesca.pontos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import com.pesca.pontos.trail.Geo
import com.pesca.pontos.trail.P
import kotlinx.coroutines.launch

/** Ícone redondo de caminho, na cor da linha. */
@Composable
fun TrailIcon(color: Int, size: Int = 40) {
    Box(
        Modifier
            .size(size.dp)
            .background(Ceva.Mata, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Timeline, null, tint = pinComposeColor(color), modifier = Modifier.size((size * 0.55).dp)) }
}

fun trailMeta(t: Trail, marks: Int): String =
    listOf(
        Geo.formatDistance(t.distanceM),
        "${t.durationMs / 60_000} min",
        if (marks == 1) "1 marcação" else "$marks marcações",
        formatDate(t.createdAt),
    ).joinToString(" · ")

/** Conteúdo do cartão que abre ao tocar na linha do caminho no mapa. */
@Composable
fun TrailSheetContent(
    trail: Trail,
    spot: Spot?,
    marks: Int,
    onFollow: () -> Unit,
    onEdit: () -> Unit,
    onQr: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrailIcon(trail.color, 46)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(trail.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${Geo.formatDistance(trail.distanceM)} · ${trail.durationMs / 60_000} min · gravado em ${formatDate(trail.createdAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onEdit, label = { Text(spot?.let { "leva ao ${it.name}" } ?: "sem ponto ligado") })
            AssistChip(onClick = onEdit, label = { Text(if (marks == 1) "1 marcação" else "$marks marcações") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onFollow,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text("Seguir caminho", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).height(50.dp)) {
                Text("Editar", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onQr, shape = RoundedCornerShape(15.dp), modifier = Modifier.height(50.dp)) {
                Icon(Icons.Filled.QrCode2, "Compartilhar por QR")
            }
        }
    }
}

/** Aba Caminhos da lupa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailsTab(
    onFollow: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val trailsFlow = remember { repo.trails.observeAll() }
    val trails by trailsFlow.collectAsState(initial = emptyList())
    val marksFlow = remember { repo.trails.observeAllMarks() }
    val marks by marksFlow.collectAsState(initial = emptyList())
    val spotsFlow = remember { repo.spots.observeAll() }
    val spots by spotsFlow.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var qr by remember { mutableStateOf<Trail?>(null) }
    var rename by remember { mutableStateOf<Trail?>(null) }
    var link by remember { mutableStateOf<Trail?>(null) }
    var delete by remember { mutableStateOf<Trail?>(null) }

    val shown = trails.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Buscar caminho pelo nome") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    trails.isEmpty() -> "Nenhum caminho gravado ainda. Use \"Gravar caminho\" no mapa."
                    shown.isEmpty() -> "Nenhum caminho encontrado."
                    shown.size == 1 -> "1 caminho"
                    else -> "${shown.size} caminhos"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown.size, key = { shown[it].id }) { i ->
                val t = shown[i]
                val spot = spots.firstOrNull { it.id == t.spotId }
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            TrailIcon(t.color)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(trailMeta(t, marks.count { it.trailId == t.id }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    spot?.let { "leva ao ${it.name}" } ?: "sem ponto ligado",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (spot != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (spot != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit(t.id) })
                                    DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; rename = t })
                                    DropdownMenuItem(text = { Text("Ligar a um ponto") }, onClick = { menu = false; link = t })
                                    DropdownMenuItem(text = { Text("Apagar", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; delete = t })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onFollow(t.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                                modifier = Modifier.weight(1f),
                            ) { Text("Seguir caminho") }
                            OutlinedButton(onClick = { qr = t }) { Icon(Icons.Filled.QrCode2, "Compartilhar por QR") }
                        }
                    }
                }
            }
        }
    }

    qr?.let { t ->
        var tMarks by remember(t.id) { mutableStateOf<List<TrailMark>?>(null) }
        LaunchedEffect(t.id) { tMarks = repo.trails.marks(t.id) }
        tMarks?.let { TrailQrDialog(t, it, spots.firstOrNull { s -> s.id == t.spotId }) { qr = null } }
    }
    rename?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Renomear caminho") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    rename = null
                    scope.launch { repo.trails.update(t.copy(name = name.trim())) }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancelar") } },
        )
    }
    link?.let { t ->
        AlertDialog(
            onDismissRequest = { link = null },
            title = { Text("Ligar a um ponto") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { link = null; scope.launch { repo.trails.update(t.copy(spotId = null)) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Nenhum ponto", modifier = Modifier.fillMaxWidth())
                    }
                    spots.forEach { s ->
                        TextButton(onClick = { link = null; scope.launch { repo.trails.update(t.copy(spotId = s.id)) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.name + if (s.id == t.spotId) "  ✓" else "", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { link = null }) { Text("Fechar") } },
        )
    }
    delete?.let { t -> ConfirmDeleteTrail(t, onDismiss = { delete = null }) { scope.launch { repo.trails.delete(t) }; delete = null } }
}

@Composable
fun ConfirmDeleteTrail(t: Trail, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apagar caminho?") },
        text = { Text("\"${t.name}\" e as marcações dele serão apagados. Não dá para desfazer.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Tela de edição do caminho: nome, cor, ponto, marcações, apagar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrailEditScreen(trailId: Long, onBack: () -> Unit, onAddMark: () -> Unit) {
    val repo = rememberRepo()
    val scope = rememberCoroutineScope()
    val trailFlow = remember(trailId) { repo.trails.observe(trailId) }
    val trail by trailFlow.collectAsState(initial = null)
    val marksFlow = remember(trailId) { repo.trails.observeMarks(trailId) }
    val marks by marksFlow.collectAsState(initial = emptyList())
    val spotsFlow = remember { repo.spots.observeAll() }
    val spots by spotsFlow.collectAsState(initial = emptyList())

    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(DEFAULT_PIN_COLOR) }
    var spotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showWheel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(trail) {
        val t = trail ?: return@LaunchedEffect
        if (!loaded) {
            name = t.name; color = resolvePinColor(t.color); spotId = t.spotId
            loaded = true
        }
    }

    val path = remember(trail?.points) { trail?.let { Geo.decode(it.points) }.orEmpty() }

    fun save(then: () -> Unit) {
        val t = trail ?: return
        scope.launch {
            repo.trails.update(t.copy(name = name.trim().ifEmpty { t.name }, color = color, spotId = spotId))
            then()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar caminho") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        val t = trail
        if (t == null || !loaded) return@Scaffold
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
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cor da linha", style = MaterialTheme.typography.titleSmall)
                val presetIndex = PIN_COLORS.indexOfFirst { it.swatch.toArgb() == color }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PIN_COLORS.forEachIndexed { i, pc ->
                        ColorCircle(pc.name, i == presetIndex, Modifier.background(pc.swatch)) { color = pc.swatch.toArgb() }
                    }
                    ColorCircle(
                        "Outra cor", presetIndex < 0,
                        Modifier.background(Brush.sweepGradient(List(7) { Color.hsv(it * 60f % 360f, 0.9f, 1f) })),
                    ) { showWheel = true }
                }
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = spots.firstOrNull { it.id == spotId }?.name ?: "Nenhum ponto",
                    onValueChange = {}, readOnly = true, label = { Text("Leva ao ponto") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Nenhum ponto") }, onClick = { spotId = null; expanded = false })
                    spots.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { spotId = s.id; expanded = false }) }
                }
            }

            Text("Marcações (${marks.size})", style = MaterialTheme.typography.titleSmall)
            if (marks.isEmpty()) {
                Text("Nenhuma marcação neste caminho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        marks.sortedBy { Geo.alongPath(path, P(it.latitude, it.longitude)) }.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                MarkBadge(m.type)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "a ${Geo.formatDistance(Geo.alongPath(path, P(m.latitude, m.longitude)))} do início",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { scope.launch { repo.trails.deleteMark(m) } }) {
                                    Icon(Icons.Filled.Close, "Excluir marcação", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { save { onAddMark() } }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("+ Adicionar marcação") }
            Text(
                "Ao adicionar, toque no caminho no mapa onde a marcação deve ficar.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Apagar caminho", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { save(onBack) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("Salvar", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showWheel) {
        ColorWheelDialog(initial = color, onDismiss = { showWheel = false }, onPick = { color = it; showWheel = false })
    }
    if (confirmDelete) {
        trail?.let { t ->
            ConfirmDeleteTrail(t, onDismiss = { confirmDelete = false }) {
                confirmDelete = false
                scope.launch { repo.trails.delete(t); onBack() }
            }
        }
    }
}

@Composable
private fun ColorCircle(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(modifier)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
    ) { if (selected) Icon(Icons.Filled.Check, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

/** Escolher o tipo da marcação ao tocar no caminho no modo "adicionar marcação". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMarkDialog(onDismiss: () -> Unit, onPick: (type: String, label: String) -> Unit) {
    var writing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova marcação") },
        text = {
            if (!writing) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MARK_TYPES.forEach { t ->
                        AssistChip(onClick = { onPick(t.key, t.label) }, label = { Text(t.label) }, leadingIcon = { Icon(t.icon, null, Modifier.size(16.dp)) })
                    }
                    AssistChip(onClick = { writing = true }, label = { Text("Escrever…") }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)) })
                }
            } else {
                OutlinedTextField(value = text, onValueChange = { text = it.take(30) }, singleLine = true, placeholder = { Text("Ex.: Cerca elétrica") })
            }
        },
        confirmButton = {
            if (writing) TextButton(enabled = text.isNotBlank(), onClick = { onPick("TEXTO", text.trim()) }) { Text("Marcar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

