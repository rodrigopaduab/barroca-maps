package com.pesca.pontos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

/** Linha de fotos editável: tirar foto, escolher da galeria, remover. */
@Composable
fun PhotoEditor(
    photos: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = rememberRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    fun importAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            val saved = uris.mapNotNull { repo.photos.import(it) }
            onChange(photos + saved)
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCamera?.let(Uri::parse)
        pendingCamera = null
        if (ok && uri != null) importAll(listOf(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        importAll(uris)
    }

    fun openCamera() {
        val uri = repo.photos.newCameraUri()
        pendingCamera = uri.toString()
        camera.launch(uri)
    }

    // A biblioteca do leitor de QR declara a permissão de câmera no app. Com ela declarada,
    // o Android exige que a permissão esteja concedida até para abrir o app de câmera.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
        else Toast.makeText(context, "Sem permissão de câmera. Use a Galeria ou libere nas configurações do Android.", Toast.LENGTH_LONG).show()
    }

    Column(modifier) {
        Text("Fotos", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            items(photos) { path ->
                Box {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewerIndex = photos.indexOf(path) },
                    )
                    IconButton(
                        onClick = { onChange(photos - path) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (busy) item {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }) {
                Icon(Icons.Default.PhotoCamera, null)
                Text("  Câmera")
            }
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Text("  Galeria")
            }
        }
    }

    viewerIndex?.let { PhotoViewer(photos, it) { viewerIndex = null } }
}

/** Fotos só para ver (tocar abre em tela cheia). */
@Composable
fun PhotoStrip(photos: List<String>, height: Int = 180, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        items(photos) { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = (height * 1.33f).dp, height = height.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewerIndex = photos.indexOf(path) },
            )
        }
    }
    viewerIndex?.let { PhotoViewer(photos, it) { viewerIndex = null } }
}

@Composable
fun PhotoViewer(photos: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { photos.size }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = File(photos[page]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, "Fechar", tint = Color.White)
            }
            Text(
                "${pager.currentPage + 1} / ${photos.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
