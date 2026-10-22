package nl.myquest.nowindcompanion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nl.myquest.nowindcompanion.MessageList

@Composable
fun DebugScreen(messages: MessageList, debugEnabled: Boolean, onBack: () -> Unit) {
    ScreenScaffold("Debug log", onBack) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(messages) {
            if (messages.data.isNotEmpty()) listState.animateScrollToItem(messages.data.size - 1)
        }
        if (messages.data.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (debugEnabled) "No messages yet" else "Debug information is disabled in Settings",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@ScreenScaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(messages.data) { message ->
                Text(
                    message,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
