package com.pesca.pontos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pesca.pontos.SpotQr
import com.pesca.pontos.data.Spot

/** Mostra o QR do ponto para um amigo ler na hora, ou enviar como imagem. */
@Composable
fun QrDialog(spot: Spot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(spot) { SpotQr.qrBitmap(SpotQr.encode(spot)).asImageBitmap() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(spot.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                // fundo sempre branco: QR em tema escuro não é lido por muitas câmeras
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "QR code do ponto ${spot.name}",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(260.dp),
                    )
                }
                Text(
                    "Quem tem o Ceva lê pelo botão de QR no mapa e recebe o ponto com nome e informações. " +
                        "Quem não tem pode ler com a câmera: abre o Google Maps no local. Fotos e peixes não vão junto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { SpotQr.share(context, spot) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.Share, null)
                    Text("  Enviar como imagem")
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    }
}
