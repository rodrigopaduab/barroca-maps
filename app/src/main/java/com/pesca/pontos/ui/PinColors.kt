package com.pesca.pontos.ui

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** Cores que o usuário pode escolher para o pino. O índice é o que fica salvo no ponto. */
data class PinColor(val name: String, val hue: Float, val swatch: Color)

val PIN_COLORS = listOf(
    PinColor("Laranja", 28f, Color(0xFFFF7A2E)),
    PinColor("Vermelho", BitmapDescriptorFactory.HUE_RED, Color(0xFFE53935)),
    PinColor("Amarelo", BitmapDescriptorFactory.HUE_YELLOW, Color(0xFFFDD835)),
    PinColor("Verde", BitmapDescriptorFactory.HUE_GREEN, Color(0xFF43A047)),
    PinColor("Ciano", BitmapDescriptorFactory.HUE_CYAN, Color(0xFF00ACC1)),
    PinColor("Azul", BitmapDescriptorFactory.HUE_BLUE, Color(0xFF1E63D6)),
    PinColor("Roxo", BitmapDescriptorFactory.HUE_VIOLET, Color(0xFF8E24AA)),
    PinColor("Rosa", BitmapDescriptorFactory.HUE_ROSE, Color(0xFFE91E63)),
)

fun pinColor(index: Int): PinColor = PIN_COLORS.getOrElse(index) { PIN_COLORS[0] }

/** Precisa ser chamado depois que o mapa carregou (dentro do conteúdo do GoogleMap). */
fun pinIcon(index: Int): BitmapDescriptor = BitmapDescriptorFactory.defaultMarker(pinColor(index).hue)
