package com.pesca.pontos.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Primeira abertura: explica e pede as permissões de uma vez (uma caixinha do Android por permissão). */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ceva.Mata)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            CevaMark(Modifier.size(width = 64.dp, height = 80.dp))
            Text("Bem-vindo ao ceva", color = Ceva.Nevoa, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Seus pontos de pesca, caminhos e peixes, guardados no seu celular. Para funcionar direito, o app precisa de algumas permissões:",
                color = Ceva.Nevoa.copy(alpha = 0.8f), fontSize = 16.sp,
            )
            Perm(Icons.Default.LocationOn, "Localização", "Mostrar onde você está, marcar pontos e gravar caminhos.")
            Perm(Icons.Default.PhotoCamera, "Câmera", "Tirar fotos dos pontos e dos peixes e ler QR codes.")
            if (Build.VERSION.SDK_INT >= 33) {
                Perm(Icons.Default.Notifications, "Notificações", "Exigida pelo Android para gravar o caminho com a tela desligada.")
            }
            Text(
                "O Android vai perguntar uma por uma. Se negar alguma, o app pede de novo quando você usar a função.",
                color = Ceva.Nevoa.copy(alpha = 0.6f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { launcher.launch(perms) },
                colors = ButtonDefaults.buttonColors(containerColor = Ceva.Boia, contentColor = Ceva.Mata),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Permitir e começar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Agora não", color = Ceva.Nevoa.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun Perm(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .background(Ceva.Boia, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Ceva.Mata) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ceva.Nevoa, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(text, color = Ceva.Nevoa.copy(alpha = 0.75f), fontSize = 14.sp)
        }
    }
}

