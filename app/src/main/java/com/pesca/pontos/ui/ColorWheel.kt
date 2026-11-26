package com.pesca.pontos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Roda de cores: o ângulo escolhe o tom, a distância do centro escolhe a intensidade. */
@Composable
fun ColorWheelDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.25f)) }
    val color = Color.hsv(hue, sat, value)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Escolha a cor do pino", style = MaterialTheme.typography.titleLarge)

                Box(contentAlignment = Alignment.Center) {
                    val update = { pos: Offset, size: Float ->
                        val r = size / 2f
                        val dx = pos.x - r
                        val dy = pos.y - r
                        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                        sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                    }
                    Canvas(
                        Modifier
                            .size(240.dp)
                            .pointerInput(Unit) { detectTapGestures { update(it, size.width.toFloat()) } }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { update(it, size.width.toFloat()) },
                                    onDrag = { change, _ -> change.consume(); update(change.position, size.width.toFloat()) },
                                )
                            }
                    ) {
                        val r = size.minDimension / 2f
                        val hues = List(13) { Color.hsv((it * 30f) % 360f, 1f, value) }
                        drawCircle(Brush.sweepGradient(hues, center), r)
                        drawCircle(Brush.radialGradient(listOf(Color.hsv(0f, 0f, value), Color.Transparent), center, r), r)
                        // marcador
                        val ang = Math.toRadians(hue.toDouble())
                        val p = Offset(center.x + (cos(ang) * sat * r).toFloat(), center.y + (sin(ang) * sat * r).toFloat())
                        drawCircle(Color.White, 14.dp.toPx(), p, style = Stroke(4.dp.toPx()))
                        drawCircle(Color.Black, 16.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Brilho", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = value, onValueChange = { value = it }, valueRange = 0.25f..1f, modifier = Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(color, CircleShape)
                    )
                    Text("Assim fica o pino", style = MaterialTheme.typography.bodyMedium)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(onClick = { onPick(color.toArgb()) }) { Text("Usar esta cor") }
                }
            }
        }
    }
}
