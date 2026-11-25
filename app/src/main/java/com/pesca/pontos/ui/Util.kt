package com.pesca.pontos.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pesca.pontos.PescaApp
import com.pesca.pontos.Repository
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberRepo(): Repository = (LocalContext.current.applicationContext as PescaApp).repo

@Composable
fun rememberSettings(): com.pesca.pontos.Settings = (LocalContext.current.applicationContext as PescaApp).settings

/** Abre um link no navegador ou no app que cuidar dele. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Não encontrei um app para abrir o link", Toast.LENGTH_SHORT).show()
    }
}

/** Abre o app do Google Maps (para o usuário baixar a região off-line). */
fun openGoogleMapsApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
    if (intent != null) context.startActivity(intent)
    else openUrl(context, "https://play.google.com/store/apps/details?id=com.google.android.apps.maps")
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))

fun formatDateTime(millis: Long): String =
    dateTimeFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatDate(millis: Long): String =
    dateFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatCoord(lat: Double, lng: Double): String =
    String.format(Locale.US, "%.6f, %.6f", lat, lng)

fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale("pt", "BR"), "%.2f", v).trimEnd('0').trimEnd(',')

/** Converte "1,5" ou "1.5" em número. */
fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Abre o Google Maps já em modo navegação até o ponto. Se não tiver o app, abre no navegador. */
fun openNavigation(context: Context, lat: Double, lng: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d"))
        .setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(nav)
    } catch (e: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving")
        )
        try {
            context.startActivity(web)
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app de mapas encontrado", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Pega a posição atual pelo GPS (precisa da permissão já concedida). */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Pair<Double, Double>? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
        loc?.let { it.latitude to it.longitude }
    } catch (e: Exception) {
        null
    }
}

fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
