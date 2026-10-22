package nl.myquest.nowindcompanion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NowindBlue80,
    onPrimary = NowindBlue20,
    primaryContainer = NowindBlue30,
    onPrimaryContainer = NowindBlue90,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    background = Neutral6,
    onBackground = NeutralVariant90,
    surface = Neutral6,
    onSurface = NeutralVariant90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
)

private val LightColorScheme = lightColorScheme(
    primary = NowindBlue40,
    onPrimary = Color.White,
    primaryContainer = NowindBlue90,
    onPrimaryContainer = NowindBlue10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
)

@Immutable
data class StatusColors(val ok: Color, val busy: Color, val error: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(StatusGreenLight, StatusAmberLight, Color.Red) }

@Composable
fun NowindCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, off by default to keep the Nowind look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val statusColors = if (darkTheme) {
        StatusColors(StatusGreenDark, StatusAmberDark, colorScheme.error)
    } else {
        StatusColors(StatusGreenLight, StatusAmberLight, colorScheme.error)
    }

    // the status bar is handled by enableEdgeToEdge() in MainActivity, content draws behind it
    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
