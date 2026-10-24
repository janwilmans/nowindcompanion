package nl.myquest.nowindcompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.myquest.nowindcompanion.DiskLibrary
import nl.myquest.nowindcompanion.DiskSource
import nl.myquest.nowindcompanion.ArchiveType
import nl.myquest.nowindcompanion.MsxFilter
import nl.myquest.nowindcompanion.hasRomExtension
import nl.myquest.nowindcompanion.SourceKind
import nl.myquest.nowindcompanion.formatSize
import nl.myquest.nowindcompanion.searchEntries
import nl.myquest.nowindcompanion.DownloadProgress
import nl.myquest.nowindcompanion.DownloadRequest
import nl.myquest.nowindcompanion.RemoteEntry
import nl.myquest.nowindcompanion.SkipDisk
import kotlinx.coroutines.launch
import nl.myquest.nowindcompanion.LibraryFile

@Composable
fun BrowseScreen(
    library: DiskLibrary,
    sources: List<DiskSource>,
    hostedDisk: String?,
    skipDisk: SkipDisk,
    onSkipDisk: (SkipDisk) -> Unit,
    commands: Long,                 // blinks the activity light when the MSX reads or writes
    onBack: () -> Unit,
) {
    val viewModel: BrowseViewModel = viewModel()
    val source by viewModel.source.collectAsState()
    val msxFilter by viewModel.msxFilter.collectAsState()
    val stack by viewModel.stack.collectAsState()
    val listing by viewModel.listing.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val pending by library.pending.collectAsState()
    val progress by library.progress.collectAsState()
    val files by library.files.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(library) {
        library.events.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(sources) {
        viewModel.setSources(sources)
    }
    // the back button first walks up the folders that were opened
    BackHandler(enabled = tab == 0 && stack.size > 1) { viewModel.up() }

    // the next boot gets no disk; pressed again it cancels that, or brings the disk back right away
    val toggleSkipDisk = {
        val (next, message) = when (skipDisk) {
            SkipDisk.Off -> SkipDisk.NextBoot to "The next boot starts without disk, reset the MSX"
            SkipDisk.NextBoot -> SkipDisk.Off to "Boot without disk cancelled"
            SkipDisk.ThisBoot -> SkipDisk.Off to "The disk is back"
        }
        onSkipDisk(next)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
        Unit
    }

    ScreenScaffold(
        "Browse",
        onBack,
        actions = {
            ActivityLight(commands, Modifier.padding(horizontal = 12.dp), size = 10.dp)
            if (skipDisk == SkipDisk.Off) {
                IconButton(onClick = toggleSkipDisk) {
                    Icon(Icons.Rounded.Eject, contentDescription = "Boot without disk")
                }
            } else {
                FilledTonalIconButton(onClick = toggleSkipDisk) {
                    Icon(
                        Icons.Rounded.Eject,
                        contentDescription = if (skipDisk == SkipDisk.NextBoot) "Cancel boot without disk" else "Bring the disk back"
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Archive") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Library (${files.size})") })
            }
            if (skipDisk != SkipDisk.Off) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (skipDisk == SkipDisk.NextBoot) "The next boot starts without disk"
                        else "Booted without disk, the next boot finds the disk again",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ArchiveTab(viewModel, sources, source, msxFilter, stack, listing, filter, library, pending, progress, files)
                    else -> LibraryTab(files, hostedDisk, library)
                }
            }
            DownloadBar(progress, pending, library)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveTab(
    viewModel: BrowseViewModel,
    sources: List<DiskSource>,
    source: DiskSource?,
    msxFilter: MsxFilter,
    stack: List<String>,
    listing: ListingState,
    filter: String,
    library: DiskLibrary,
    pending: List<DownloadRequest>,
    progress: DownloadProgress?,
    files: List<LibraryFile>,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources, key = { it.url }) { item ->
                FilterChip(
                    selected = item.url == source?.url,
                    onClick = { viewModel.select(item) },
                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
        }
        if (source == null) {
            CenteredBox {
                Text(
                    "No disk image sources are enabled.\nAdd or enable one in Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Column
        }
        if (source.kind == SourceKind.NowindApi) {
            val archiveType by viewModel.archiveType.collectAsState()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ArchiveType.values()) { item ->
                    FilterChip(
                        selected = item == archiveType,
                        onClick = { viewModel.setArchiveType(item) },
                        label = { Text(item.label) }
                    )
                }
                item { Spacer(Modifier.width(8.dp)) }
                items(MsxFilter.values()) { item ->
                    FilterChip(
                        selected = item == msxFilter,
                        onClick = { viewModel.setMsxFilter(item) },
                        label = { Text(item.label) }
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.up() }, enabled = stack.size > 1) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Up")
                }
                Text(
                    stack.lastOrNull()?.let { pathSegments(source.url, it) }.orEmpty()
                        .joinToString("  /  ").ifEmpty { source.label },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        OutlinedTextField(
            value = filter,
            onValueChange = viewModel::setFilter,
            placeholder = { Text("Filter") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setFilter("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        when (listing) {
            ListingState.Loading -> CenteredBox { CircularProgressIndicator() }
            is ListingState.Error -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Could not load the list", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listing.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }

            is ListingState.Loaded -> {
                val downloaded = files.map { it.name }.toSet()
                val queued = pending.map { it.entry.url }.toSet()
                val entries = remember(listing.entries, filter) { searchEntries(listing.entries, filter) }
                if (entries.isEmpty()) {
                    CenteredBox { Text("Nothing here", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(entries, key = { it.url }) { entry ->
                            if (entry.isDirectory) {
                                FolderRow(entry) { viewModel.open(entry) }
                            } else {
                                DiskRow(
                                    entry = entry,
                                    downloaded = entry.fileName in downloaded,
                                    queued = entry.url in queued,
                                    progress = progress?.takeIf { it.request.entry.url == entry.url },
                                    onQueue = { library.enqueue(entry) },
                                    onDownloadAndHost = { library.downloadAndHost(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(entry: RemoteEntry, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { IconBadge(Icons.Rounded.Folder) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun DiskRow(
    entry: RemoteEntry,
    downloaded: Boolean,
    queued: Boolean,
    progress: DownloadProgress?,
    onQueue: () -> Unit,
    onDownloadAndHost: () -> Unit,
) {
    val status = when {
        progress != null -> "Downloading…"
        downloaded -> "Downloaded"
        queued -> "Queued"
        else -> null
    }
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(listOfNotNull(entry.size.ifEmpty { null }, status).joinToString("  ·  "))
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(progress)
                }
            }
        },
        leadingContent = {
            IconBadge(
                when {
                    downloaded -> Icons.Rounded.CheckCircle
                    entry.rom -> Icons.Rounded.Memory
                    else -> Icons.Rounded.Save
                },
                container = if (downloaded) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                content = if (downloaded) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onQueue, enabled = !downloaded && !queued && progress == null) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Queue for download")
                }
                FilledTonalIconButton(onClick = onDownloadAndHost, enabled = progress == null) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Download and host")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun LibraryTab(files: List<LibraryFile>, hostedDisk: String?, library: DiskLibrary) {
    if (files.isEmpty()) {
        CenteredBox {
            Text(
                "Nothing downloaded yet.\nQueue some from the archive.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(files, key = { it.name }) { file ->
            val hosted = file.name == hostedDisk
            ListItem(
                headlineContent = {
                    Text(
                        file.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (hosted) FontWeight.SemiBold else null
                    )
                },
                supportingContent = {
                    Text(listOfNotNull(formatSize(file.size), if (hosted) "Hosted" else null).joinToString("  ·  "))
                },
                leadingContent = {
                    IconBadge(
                        if (hasRomExtension(file.name)) Icons.Rounded.Memory else Icons.Rounded.Save,
                        container = if (hosted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        content = if (hosted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { library.delete(file) }, enabled = !hosted) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                        FilledTonalIconButton(onClick = { library.host(file) }, enabled = !hosted) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Host")
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    }
}

// shown below the tabs while something is downloading or queued
@Composable
private fun DownloadBar(progress: DownloadProgress?, pending: List<DownloadRequest>, library: DiskLibrary) {
    if (progress == null && pending.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        progress?.let { (if (it.request.hostWhenDone) "Downloading to host: " else "Downloading: ") + it.request.entry.name }
                            ?: "Waiting to download",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pending.isNotEmpty()) {
                        Text("${pending.size} more in queue", style = MaterialTheme.typography.bodySmall)
                    }
                }
                progress?.let {
                    IconButton(onClick = { library.cancel(it.request.entry) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel download")
                    }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(end = 12.dp)) { ProgressLine(progress) }
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: DownloadProgress) {
    val fraction = progress.fraction
    val modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
    if (fraction == null) {
        LinearProgressIndicator(modifier = modifier, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = modifier, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}
