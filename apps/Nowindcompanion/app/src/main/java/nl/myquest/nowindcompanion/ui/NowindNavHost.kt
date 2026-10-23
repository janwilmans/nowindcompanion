package nl.myquest.nowindcompanion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.myquest.nowindcompanion.APP_FOLDER
import nl.myquest.nowindcompanion.NowindApp
import nl.myquest.nowindcompanion.Settings

object Routes {
    const val MENU = "menu"
    const val NOWIND = "nowind"
    const val BROWSE = "browse"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
    const val FIRMWARE = "firmware"
}

@Composable
fun NowindNavHost(app: NowindApp) {
    val nav = rememberNavController()
    val connection by app.state.connection.collectAsState()
    val hostedDisk by app.state.hostedDisk.collectAsState()
    val device by app.state.deviceInfo.collectAsState()
    val readStats by app.state.readStats.collectAsState()
    val writeStats by app.state.writeStats.collectAsState()
    val commands by app.state.commands.collectAsState()
    val messages by app.state.messages.collectAsState()
    val settings by app.settings.settings.collectAsState(initial = Settings())
    val storageRoot by app.storage.root.collectAsState()
    val back: () -> Unit = { nav.popBackStack() }

    StorageFolderPrompt(app)

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
                commands = commands,
                recentReads = app.state.recentReads,
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
                readStats = readStats,
                writeStats = writeStats,
                onResetStats = app.state::resetStats,
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
                onEditDefaultSource = { name, kind -> app.appScope.launch { app.settings.setDefaultSource(name, kind) } },
                onFirmware = { nav.navigate(Routes.FIRMWARE) },
                onBack = back,
            )
        }
        composable(Routes.FIRMWARE) {
            FirmwareScreen(
                firmware = app.firmware,
                connection = connection,
                firmwareSource = settings.firmwareSource,
                onFirmwareSource = { app.appScope.launch { app.settings.setFirmwareSource(it) } },
                eraseMethod = settings.firmwareErase,
                onEraseMethod = { app.appScope.launch { app.settings.setFirmwareErase(it) } },
                storageReady = storageRoot != null,
                onBack = back,
            )
        }
        composable(Routes.DEBUG) { DebugScreen(messages, settings.debugEnabled, back) }
    }
}

// asks for access to Download/nowindcompanion, on the first run and again after a reinstall
@Composable
private fun StorageFolderPrompt(app: NowindApp) {
    val root by app.storage.root.collectAsState()
    var later by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { app.storage.grant(it) }
    }
    if (root != null || later) return
    AlertDialog(
        onDismissRequest = { later = true },
        title = { Text("Storage folder") },
        text = {
            Text(
                "Downloaded disks and the settings are kept in Download/$APP_FOLDER, so you can reach them over USB " +
                    "and they stay when the app is removed.\n\nIn the next screen, open the '$APP_FOLDER' folder " +
                    "(create it in Download if it is not there) and tap 'Use this folder'."
            )
        },
        confirmButton = {
            TextButton(onClick = { picker.launch(app.storage.pickerStart()) }) { Text("Choose folder") }
        },
        dismissButton = {
            TextButton(onClick = { later = true }) { Text("Later") }
        },
    )
}
