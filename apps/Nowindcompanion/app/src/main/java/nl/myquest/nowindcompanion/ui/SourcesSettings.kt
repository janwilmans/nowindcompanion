package nl.myquest.nowindcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.myquest.nowindcompanion.Detection
import nl.myquest.nowindcompanion.DiskSource
import nl.myquest.nowindcompanion.SourceKind
import nl.myquest.nowindcompanion.detectSource
import nl.myquest.nowindcompanion.normalizeSourceUrl
import nl.myquest.nowindcompanion.shortUrl

private val exampleSources = listOf(
    "https://download.file-hunter.com/Games/MSX2/DSK/",
    "https://www.msxarchive.nl/pub/msx/mep-mirror/Games/",
    "http://floppy.disk.free.fr/Sources/games_msx.htm",
)

private fun SourceKind.icon() = when (this) {
    SourceKind.NowindApi -> Icons.Rounded.Api
    SourceKind.DirectoryListing -> Icons.Rounded.FolderOpen
    SourceKind.WebPage -> Icons.Rounded.Language
}

// the rows for the 'Disk image sources' section in Settings
@Composable
fun ColumnScope.SourceRows(
    sources: List<DiskSource>,
    onEnabled: (DiskSource, Boolean) -> Unit,
    onRemove: (DiskSource) -> Unit,
    onAdd: (DiskSource) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    for (source in sources) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(source.kind.icon())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (source.builtIn) source.label else shortUrl(source.url).substringBefore('/'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    shortUrl(source.url).let { if (source.builtIn) it else it.substringAfter('/', "") }
                        .ifEmpty { "/" } + "  ·  " + source.kind.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!source.builtIn) {
                IconButton(onClick = { onRemove(source) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove ${source.label}")
                }
            }
            Switch(checked = source.enabled, onCheckedChange = { onEnabled(source, it) })
        }
    }
    TextButton(onClick = { adding = true }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add source")
    }
    if (adding) {
        AddSourceDialog(
            existing = sources.map { it.url },
            onDismiss = { adding = false },
            onAdd = {
                onAdd(it)
                adding = false
            }
        )
    }
}

// asks for a url and checks it before adding, so the user sees whether disk images can be found there
@Composable
private fun AddSourceDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (DiskSource) -> Unit) {
    var text by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var detection by remember { mutableStateOf<Detection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val url = normalizeSourceUrl(text)

    fun check() {
        if (text.isBlank()) return
        checking = true
        error = null
        scope.launch {
            try {
                detection = withContext(Dispatchers.IO) { detectSource(url) }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                checking = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add disk image source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A Nowind API, a web server directory or a web page that links to .zip or .dsk files. " +
                            "The kind is detected automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        detection = null
                        error = null
                    },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isBlank()) {
                    Text("Examples", style = MaterialTheme.typography.labelMedium)
                    for (example in exampleSources.filter { it !in existing }) {
                        SuggestionChip(
                            onClick = { text = example },
                            label = { Text(shortUrl(example), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                when {
                    checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking…")
                    }

                    detection != null -> Text(
                        "Found a ${detection!!.kind.label.lowercase()} with ${detection!!.entries} entries.",
                        color = MaterialTheme.colorScheme.primary
                    )

                    error != null -> Text(
                        "Could not read it: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(0.dp))
            }
        },
        confirmButton = {
            val found = detection
            when {
                found != null -> TextButton(onClick = { onAdd(DiskSource(url, found.kind)) }) { Text("Add") }
                // some sites refuse automated requests now and then; keep the url, Browse reads it as a web page
                error != null -> TextButton(onClick = { onAdd(DiskSource(url, SourceKind.WebPage)) }) { Text("Add anyway") }
                else -> TextButton(onClick = ::check, enabled = text.isNotBlank() && !checking) { Text("Check") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
