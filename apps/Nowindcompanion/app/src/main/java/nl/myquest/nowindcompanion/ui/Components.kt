package nl.myquest.nowindcompanion.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.myquest.nowindcompanion.ConnectionState
import nl.myquest.nowindcompanion.ui.theme.LocalStatusColors

// common frame for all screens except the menu: a top bar with back navigation
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun connectionColor(state: ConnectionState): Color {
    val colors = LocalStatusColors.current
    return when (state) {
        ConnectionState.Hosting -> colors.ok
        ConnectionState.Searching -> colors.busy
        ConnectionState.Stopped, ConnectionState.NoUsbHost, ConnectionState.OpenFailed -> colors.error
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun ConnectionChip(state: ConnectionState, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val color = connectionColor(state)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (onDark) Color.Black.copy(alpha = 0.45f) else color.copy(alpha = 0.12f),
        contentColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color)
            Spacer(Modifier.width(8.dp))
            Text(state.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// a titled group of rows on a rounded surface
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

// an icon in a rounded, tinted square, used as leading element of cards and rows
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier
            .size(44.dp)
            .background(container, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content)
    }
}

fun Context.appVersionName(): String =
    try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

private const val LED_IDLE = 0.25f
private val LedRed = Color(0xFFFF3B30)

// a red LED that flashes briefly whenever 'pulses' changes and fades back to a dim idle glow
@Composable
fun ActivityLight(pulses: Long, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val glow = remember { Animatable(LED_IDLE) }
    LaunchedEffect(pulses) {
        if (pulses == 0L) return@LaunchedEffect
        glow.snapTo(1f)
        delay(80)
        glow.animateTo(LED_IDLE, tween(durationMillis = 600))
    }
    Box(
        modifier
            .size(size)
            .drawBehind {
                val lit = (glow.value - LED_IDLE) / (1f - LED_IDLE)
                drawCircle(LedRed.copy(alpha = 0.5f * lit), radius = this.size.minDimension)
                drawCircle(LedRed.copy(alpha = glow.value), radius = this.size.minDimension / 2)
            }
    )
}
