package com.pesca.pontos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Phishing
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pesca.pontos.Astro
import com.pesca.pontos.Weather
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// O painel fica sempre escuro sobre o mapa (legível em cima do satélite), nas cores do tema
private val PanelBg: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
private val PanelText: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
private val PanelMuted: Color @Composable get() = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
private val PanelAccent: Color @Composable get() = MaterialTheme.colorScheme.secondary
private val PanelOnAccent: Color @Composable get() = MaterialTheme.colorScheme.onSecondary

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val longDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR"))
private fun hhmm(ms: Long) = hm.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Painel lateral com informações de pescaria. lat/lng = onde o usuário está (ou o centro do mapa).
 */
@Composable
fun FishingPanel(lat: Double?, lng: Double?, modifier: Modifier = Modifier) {
    val settings = rememberSettings()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var weather by remember { mutableStateOf(settings.lastWeather?.let { Weather.Now.fromJson(it) }) }
    var updating by remember { mutableStateOf(false) }

    // relógio
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    // clima: busca ao abrir e a cada 15 minutos
    LaunchedEffect(lat?.let { (it * 100).toInt() }, lng?.let { (it * 100).toInt() }) {
        if (lat == null || lng == null) return@LaunchedEffect
        while (true) {
            updating = true
            Weather.fetchNow(lat, lng)?.let {
                weather = it
                settings.lastWeather = it.toJson()
            }
            updating = false
            delay(15 * 60_000L)
        }
    }

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val day = remember(today, lat?.let { (it * 10).toInt() }, lng?.let { (it * 10).toInt() }) {
        if (lat != null && lng != null) Astro.day(today, lat, lng) else null
    }
    val moon = remember(today) { Astro.moon(now) }

    AnimatedContent(targetState = settings.panelOpen, label = "painel", modifier = modifier) { open ->
        if (!open) {
            // aba recolhida
            Surface(
                onClick = { settings.updatePanelOpen(true) },
                color = PanelBg,
                contentColor = PanelText,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                shadowElevation = 6.dp,
            ) {
                Column(
                    Modifier.padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    weather?.tempC?.let { Text("${it.toInt()}°", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                    Text(hhmm(now), fontSize = 11.sp, color = PanelMuted, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir painel de pescaria")
                }
            }
        } else {
            Surface(
                color = PanelBg,
                contentColor = PanelText,
                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.width(236.dp),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    // hora e data + botão recolher
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(hhmm(now), style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
                            Text(longDate.format(today).replaceFirstChar { it.uppercase() }, fontSize = 11.5.sp, color = PanelMuted)
                        }
                        FilledIconButton(
                            onClick = { settings.updatePanelOpen(false) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = PanelAccent, contentColor = PanelOnAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Recolher painel") }
                    }

                    Divider()

                    // clima agora
                    val w = weather
                    if (w == null) {
                        Text(
                            if (updating) "Buscando o clima..." else "Clima indisponível sem internet.",
                            fontSize = 12.sp, color = PanelMuted,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(w.tempC?.let { "${it.toInt()}°C" } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(Weather.describe(w.code), fontSize = 12.sp, color = PanelMuted)
                        }
                        Spacer(Modifier.size(8.dp))
                        val arrow = when {
                            w.pressureDelta == null -> ""
                            w.pressureDelta > 0.7 -> " ↑"
                            w.pressureDelta < -0.7 -> " ↓"
                            else -> " →"
                        }
                        Grid(
                            "Vento" to listOfNotNull(
                                w.windDir?.let { windArrow(it) },
                                w.windKmh?.let { "${it.toInt()} km/h" },
                                w.windDir?.let { Weather.compass(it) },
                            ).joinToString(" ").ifEmpty { "—" },
                            "Pressão" to (w.pressure?.let { "${it.toInt()} hPa$arrow" } ?: "—"),
                            "Umidade" to (w.humidity?.let { "$it%" } ?: "—"),
                            "Chuva (3h)" to (w.rainChance?.let { "$it%" } ?: "—"),
                        )
                        val age = (now - w.fetchedAt) / 60_000
                        if (age >= 30) {
                            Spacer(Modifier.size(6.dp))
                            Text("Atualizado às ${hhmm(w.fetchedAt)} (sem internet)", fontSize = 10.5.sp, color = PanelMuted)
                        }
                    }

                    Divider()

                    // sol
                    Label(icon = { Icon(Icons.Outlined.WbSunny, null, tint = PanelAccent, modifier = Modifier.size(16.dp)) }, text = "Sol")
                    Spacer(Modifier.size(6.dp))
                    Grid(
                        "Nascer" to (day?.sunrise?.let(::hhmm) ?: "—"),
                        "Pôr" to (day?.sunset?.let(::hhmm) ?: "—"),
                    )

                    Divider()

                    // lua
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoonIcon(moon.phase, Modifier.size(26.dp))
                        Column {
                            Value(moon.name)
                            Text("${(moon.fraction * 100).toInt()}% iluminada", fontSize = 11.5.sp, color = PanelMuted)
                        }
                    }

                    Divider()

                    // solunar
                    Label(icon = { Icon(Icons.Outlined.Phishing, null, tint = PanelAccent, modifier = Modifier.size(16.dp)) }, text = "Tábua solunar de hoje")
                    Spacer(Modifier.size(8.dp))
                    val periods = day?.periods.orEmpty()
                    if (periods.isEmpty()) {
                        Text(if (lat == null) "Aguardando a localização..." else "Sem períodos hoje.", fontSize = 12.sp, color = PanelMuted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        periods.forEach { p ->
                            val active = now in p.start..p.end
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (p.major) "Maior" else "Menor",
                                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                    color = if (p.major) PanelOnAccent else PanelText,
                                    modifier = Modifier
                                        .background(if (p.major) PanelAccent else PanelText.copy(alpha = 0.14f), RoundedCornerShape(50))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                                Value("${hhmm(p.start)} – ${hhmm(p.end)}" + if (active) "  agora" else "")
                            }
                        }
                    }
                    val active = periods.firstOrNull { now in it.start..it.end }
                    val next = periods.firstOrNull { it.start > now }
                    val hint = when {
                        active != null -> "Período ${if (active.major) "maior" else "menor"} agora, até ${hhmm(active.end)}"
                        next != null -> "Próximo período em ${duration(next.start - now)}"
                        else -> null
                    }
                    hint?.let {
                        Spacer(Modifier.size(6.dp))
                        Text(it, fontSize = 11.5.sp, color = PanelAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Seta para onde o vento sopra (ele vem da direção "deg"). */
private fun windArrow(deg: Double): String {
    val arrows = listOf("↓", "↙", "←", "↖", "↑", "↗", "→", "↘")
    return arrows[(((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
}

private fun duration(ms: Long): String {
    val min = (ms / 60_000).toInt()
    return if (min < 60) "$min min" else "${min / 60}h${(min % 60).toString().padStart(2, '0')}"
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PanelText.copy(alpha = 0.12f))
}

@Composable
private fun Value(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Label(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Grade de 2 colunas: rótulo pequeno em cima, valor embaixo. */
@Composable
private fun Grid(vararg cells: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.toList().chunked(2).forEach { row ->
            Row {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 10.5.sp, color = PanelMuted)
                        Value(value)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Lua desenhada conforme a fase (0 = nova, 0.5 = cheia). */
@Composable
private fun MoonIcon(phase: Double, modifier: Modifier) {
    val litColor = PanelText
    val darkColor = PanelText.copy(alpha = 0.18f)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(darkColor, r, c)
        // parte iluminada: metade + elipse (terminador)
        val lit = litColor
        val waxing = phase < 0.5
        val k = cos2(phase) // 1 = nova, -1 = cheia
        val path = androidx.compose.ui.graphics.Path().apply {
            // metade iluminada (direita se crescente, esquerda se minguante)
            val startAngle = if (waxing) -90f else 90f
            arcTo(androidx.compose.ui.geometry.Rect(c, r), startAngle, 180f, false)
            // terminador: elipse de largura |k|*r
            val w = abs(k) * r
            val sweep = if (k > 0) -180f else 180f
            arcTo(androidx.compose.ui.geometry.Rect(Offset(c.x - w, c.y - r), Size(w * 2, r * 2)), startAngle + 180f, sweep, false)
            close()
        }
        drawPath(path, lit)
    }
}

private fun cos2(phase: Double): Float = kotlin.math.cos(phase * 2 * Math.PI).toFloat()
