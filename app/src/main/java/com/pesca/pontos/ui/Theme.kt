package com.pesca.pontos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.pesca.pontos.AppTheme

/** Cores da marca Ceva. */
object Ceva {
    val Mata = Color(0xFF10241B)
    val Boia = Color(0xFFFF7A2E)
    val Rio = Color(0xFF2E8C7A)
    val Nevoa = Color(0xFFF4F6F1)
}

private val Padrao = lightColorScheme(
    primary = Ceva.Mata,
    onPrimary = Ceva.Nevoa,
    primaryContainer = Color(0xFFE2E7E1),
    onPrimaryContainer = Ceva.Mata,
    secondary = Ceva.Boia,
    onSecondary = Ceva.Mata,
    secondaryContainer = Ceva.Boia,
    onSecondaryContainer = Ceva.Mata,
    tertiary = Ceva.Rio,
    onTertiary = Color.White,
    background = Ceva.Nevoa,
    onBackground = Ceva.Mata,
    surface = Ceva.Nevoa,
    onSurface = Ceva.Mata,
    surfaceVariant = Color(0xFFE2E7E1),
    onSurfaceVariant = Color(0xFF55625B),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFCBD2CA),
)

private val Contraste = darkColorScheme(
    primary = Color(0xFFFFE600),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFFFFE600),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    outline = Color.White,
)

private val Noite = darkColorScheme(
    primary = Color(0xFF7A1414),
    onPrimary = Color(0xFFFFD6D2),
    primaryContainer = Color(0xFF2A0C0B),
    onPrimaryContainer = Color(0xFFFF8A80),
    secondary = Color(0xFFB71C1C),
    onSecondary = Color(0xFFFFD6D2),
    secondaryContainer = Color(0xFFB71C1C),
    onSecondaryContainer = Color(0xFFFFD6D2),
    tertiary = Color(0xFFE5534B),
    onTertiary = Color(0xFF120404),
    background = Color(0xFF120404),
    onBackground = Color(0xFFFF8A80),
    surface = Color(0xFF120404),
    onSurface = Color(0xFFFF8A80),
    surfaceVariant = Color(0xFF2A0C0B),
    onSurfaceVariant = Color(0xFFD9776E),
    surfaceContainerLow = Color(0xFF1E0808),
    surfaceContainer = Color(0xFF1E0808),
    surfaceContainerHigh = Color(0xFF250A0A),
    outline = Color(0xFF5A1A16),
)

private val VermelhoVerde = lightColorScheme(
    primary = Color(0xFF0E2A47),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E8F0),
    onPrimaryContainer = Color(0xFF0E2A47),
    secondary = Color(0xFFFFB000),
    onSecondary = Color(0xFF0E2A47),
    secondaryContainer = Color(0xFFFFB000),
    onSecondaryContainer = Color(0xFF0E2A47),
    tertiary = Color(0xFF2F6DB5),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0E2A47),
    surface = Color(0xFFF3F6FA),
    onSurface = Color(0xFF0E2A47),
    surfaceVariant = Color(0xFFE1E8F0),
    onSurfaceVariant = Color(0xFF46566A),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFC3CEDA),
)

private val AzulAmarelo = lightColorScheme(
    primary = Color(0xFF25212A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE5EA),
    onPrimaryContainer = Color(0xFF25212A),
    secondary = Color(0xFFD8335B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8335B),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFB0244F),
    onTertiary = Color.White,
    background = Color(0xFFF7F4F6),
    onBackground = Color(0xFF25212A),
    surface = Color(0xFFF7F4F6),
    onSurface = Color(0xFF25212A),
    surfaceVariant = Color(0xFFECE5EA),
    onSurfaceVariant = Color(0xFF5E5662),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFD2C8CF),
)

private val SemCores = darkColorScheme(
    primary = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color.White,
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFFE0E0E0),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCFCFCF),
    surfaceContainerLow = Color(0xFF1E1E1E),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF9E9E9E),
)

private val BaseType = Typography()
private val CevaType = BaseType.copy(
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
)

/** Estilo do nome "Barroca" no topo do mapa. */
val BrandStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.4).sp, lineHeight = 22.sp)

@Composable
fun CevaTheme(theme: AppTheme, bigText: Boolean, content: @Composable () -> Unit) {
    val colors = when (theme) {
        AppTheme.PADRAO -> Padrao
        AppTheme.CONTRASTE -> Contraste
        AppTheme.NOITE -> Noite
        AppTheme.VERMELHO_VERDE -> VermelhoVerde
        AppTheme.AZUL_AMARELO -> AzulAmarelo
        AppTheme.SEM_CORES -> SemCores
    }
    val d = LocalDensity.current
    val density = if (bigText) Density(d.density, d.fontScale * 1.15f) else d
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colors, typography = CevaType, content = content)
    }
}
