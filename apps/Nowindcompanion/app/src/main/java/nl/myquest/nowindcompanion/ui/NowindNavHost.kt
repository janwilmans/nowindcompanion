package nl.myquest.nowindcompanion.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.myquest.nowindcompanion.NowindApp
import nl.myquest.nowindcompanion.Settings

object Routes {
    const val MENU = "menu"
    const val NOWIND = "nowind"
    const val BROWSE = "browse"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
fun NowindNavHost(app: NowindApp) {
    val nav = rememberNavController()
    val connection by app.state.connection.collectAsState()
    val hostedDisk by app.state.hostedDisk.collectAsState()
    val device by app.state.deviceInfo.collectAsState()
    val messages by app.state.messages.collectAsState()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(
        navController = nav,
        startDestination = Routes.MENU,
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(300)) + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
    ) {
        composable(Routes.MENU) {
            MenuScreen(
                connection = connection,
                hostedDisk = hostedDisk,
                settingsSummary = "DOS2 ${if (settings.enableDos2) "on" else "off"}  ·  debug information ${if (settings.debugEnabled) "on" else "off"}",
                onNowind = { nav.navigate(Routes.NOWIND) },
                onBrowse = { nav.navigate(Routes.BROWSE) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NOWIND) {
            NowindStatusScreen(
                connection = connection,
                device = device,
                hostedDisk = hostedDisk,
                onDebugLog = { nav.navigate(Routes.DEBUG) },
                onBack = back,
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(app.library, settings.allSources.filter { it.enabled }, hostedDisk, back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onEnableDos2 = { app.appScope.launch { app.settings.setEnableDos2(it) } },
                onDebugEnabled = { app.appScope.launch { app.settings.setDebugEnabled(it) } },
                onKeepActive = { app.appScope.launch { app.settings.setKeepActive(it) } },
                onSourceEnabled = { source, enabled -> app.appScope.launch { app.settings.setSourceEnabled(source, enabled) } },
                onRemoveSource = { app.appScope.launch { app.settings.removeSource(it.url) } },
                onAddSource = { app.appScope.launch { app.settings.addSource(it) } },
                onBack = back,
            )
        }
        composable(Routes.DEBUG) { DebugScreen(messages, settings.debugEnabled, back) }
    }
}
