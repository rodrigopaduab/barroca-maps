package com.pesca.pontos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.pesca.pontos.PescaApp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PescaApp
        val settings = app.settings
        // Ao abrir o app, tenta completar o clima dos pontos marcados sem internet
        lifecycleScope.launch { app.repo.fillMissingWeather() }
        setContent {
            CevaTheme(theme = settings.theme, bigText = settings.bigText) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (settings.onboardingDone) AppNav() else OnboardingScreen(onDone = { settings.finishOnboarding() })
                }
            }
        }
    }
}

object Routes {
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val LIST = "list"
    const val NEW_SPOT = "newspot?lat={lat}&lng={lng}"
    const val SPOT = "spot/{spotId}"
    const val EDIT_SPOT = "spot/{spotId}/edit"
    const val NEW_CATCH = "spot/{spotId}/catch/new"
    const val EDIT_CATCH = "catch/{catchId}/edit"

    fun newSpot(lat: Double, lng: Double) = "newspot?lat=$lat&lng=$lng"
    fun spot(id: Long) = "spot/$id"
    fun editSpot(id: Long) = "spot/$id/edit"
    fun newCatch(spotId: Long) = "spot/$spotId/catch/new"
    fun editCatch(id: Long) = "catch/$id/edit"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as PescaApp
    NavHost(navController = nav, startDestination = Routes.MAP) {

        composable(Routes.MAP) {
            MapScreen(
                onNewSpot = { lat, lng -> nav.navigate(Routes.newSpot(lat, lng)) },
                onOpenSpot = { id -> nav.navigate(Routes.spot(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onList = { nav.navigate(Routes.LIST) },
            )
        }

        composable(Routes.LIST) {
            SpotListScreen(
                onBack = { nav.popBackStack() },
                onOpenSpot = { id -> nav.navigate(Routes.spot(id)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(
            Routes.NEW_SPOT,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; defaultValue = "0" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) { entry ->
            val lat = entry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lng = entry.arguments?.getString("lng")?.toDoubleOrNull() ?: 0.0
            SpotEditScreen(
                spotId = null,
                initialLat = lat,
                initialLng = lng,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.spot(id))
                },
            )
        }

        composable(Routes.SPOT, arguments = listOf(navArgument("spotId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("spotId")
            SpotDetailScreen(
                spotId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editSpot(id)) },
                onAddCatch = { nav.navigate(Routes.newCatch(id)) },
                onOpenCatch = { catchId -> nav.navigate(Routes.editCatch(catchId)) },
                onDeleted = { nav.popBackStack(Routes.MAP, inclusive = false) },
                onFollowTrail = { trailId ->
                    app.repo.followTrailId = trailId
                    nav.popBackStack(Routes.MAP, inclusive = false)
                },
            )
        }

        composable(Routes.EDIT_SPOT, arguments = listOf(navArgument("spotId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("spotId")
            SpotEditScreen(
                spotId = id,
                initialLat = 0.0,
                initialLng = 0.0,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }

        composable(Routes.NEW_CATCH, arguments = listOf(navArgument("spotId") { type = NavType.LongType })) { entry ->
            CatchEditScreen(
                catchId = null,
                spotId = entry.arguments!!.getLong("spotId"),
                onDone = { nav.popBackStack() },
            )
        }

        composable(Routes.EDIT_CATCH, arguments = listOf(navArgument("catchId") { type = NavType.LongType })) { entry ->
            CatchEditScreen(
                catchId = entry.arguments!!.getLong("catchId"),
                spotId = null,
                onDone = { nav.popBackStack() },
            )
        }
    }
}
