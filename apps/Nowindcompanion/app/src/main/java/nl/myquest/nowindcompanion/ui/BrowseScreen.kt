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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
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
import nl.myquest.nowindcompanion.ReportTarget
import nl.myquest.nowindcompanion.SkipDisk
import nl.myquest.nowindcompanion.sizeText
import nl.myquest.nowindcompanion.sourceNameOf
import kotlinx.coroutines.launch
import nl.myquest.nowindcompanion.LibraryFile

// what the user keeps per library file (from the settings): the working mark and the image's own DOS2 setting
class ImageMarks(
    val working: Set<String>,
    val imageDos2: Map<String, Boolean>,
    val defaultDos2: Boolean,
    val onWorking: (String, Boolean) -> Unit,
    val onImageDos2: (String, Boolean?) -> Unit,
)

@Composable
fun BrowseScreen(
    library: DiskLibrary,
    sources: List<DiskSource>,
    hostedDisk: String?,
    skipDisk: SkipDisk,
    onSkipDisk: (SkipDisk) -> Unit,
    commands: Long,                 // blinks the activity light when the MSX reads or writes
    marks: ImageMarks,
    buildReport: (ReportTarget, String) -> String,     // the text of a 'not working' report, see ProblemReport.kt
    onBack: () -> Unit,
) {
    val viewModel: BrowseViewModel = viewModel()
    var reporting by remember { mutableStateOf<ReportTarget?>(null) }
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
                    0 -> ArchiveTab(viewModel, sources, source, msxFilter, stack, listing, filter, library, pending, progress, files, hostedDisk, marks) { reporting = it }
                    else -> LibraryTab(files, hostedDisk, library, marks) { reporting = it }
                }
            }
            DownloadBar(progress, pending, library)
        }
    }

    reporting?.let { ReportDialog(it, buildReport) { reporting = null } }
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
    hostedDisk: String?,
    marks: ImageMarks,
    onReport: (ReportTarget) -> Unit,
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
                val downloaded = files.associateBy { it.name }
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
                                    downloaded = downloaded[entry.fileName],
                                    hosted = entry.fileName == hostedDisk,
                                    working = entry.fileName in marks.working,
                                    queued = entry.url in queued,
                                    progress = progress?.takeIf { it.request.entry.url == entry.url },
                                    onQueue = { library.enqueue(entry) },
                                    onDownloadAndHost = { library.downloadAndHost(entry) },
                                    onReport = {
                                        onReport(
                                            ReportTarget(
                                                fileName = entry.fileName,
                                                size = entry.size.ifEmpty { null },
                                                source = sourceNameOf(entry.url),
                                            )
                                        )
                                    },
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
    downloaded: LibraryFile?,       // the file in the library, when it is downloaded
    hosted: Boolean,
    working: Boolean,
    queued: Boolean,
    progress: DownloadProgress?,
    onQueue: () -> Unit,
    onDownloadAndHost: () -> Unit,
    onReport: () -> Unit,
) {
    val status = when {
        progress != null -> "Downloading…"
        downloaded != null && working -> "Working"
        queued -> "Queued"
        else -> null
    }
    ListItem(
        headlineContent = { ExpandableName(entry.name) },
        supportingContent = {
            Column {
                Text(listOfNotNull((downloaded?.sizeText() ?: entry.size).ifEmpty { null }, status).joinToString("  ·  "))
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(progress)
                }
            }
        },
        leadingContent = {
            val (green, onGreen) = downloadedBadgeColors()
            val (blue, onBlue) = hostingBadgeColors()
            IconBadge(
                when {
                    downloaded != null -> Icons.Rounded.CheckCircle
                    entry.rom -> Icons.Rounded.Memory
                    else -> Icons.Rounded.Save
                },
                container = when {
                    hosted -> blue
                    downloaded != null -> green
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                content = when {
                    hosted -> onBlue
                    downloaded != null -> onGreen
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        },
        trailingContent = {
            Row {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${entry.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Queue for download") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                            enabled = downloaded == null && !queued && progress == null,
                            onClick = {
                                menu = false
                                onQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report as not working") },
                            leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                            onClick = {
                                menu = false
                                onReport()
                            }
                        )
                    }
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
private fun LibraryTab(
    files: List<LibraryFile>,
    hostedDisk: String?,
    library: DiskLibrary,
    marks: ImageMarks,
    onReport: (ReportTarget) -> Unit,
) {
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
                headlineContent = { ExpandableName(file.name, if (hosted) FontWeight.SemiBold else null) },
                supportingContent = {
                    val dos2 = marks.imageDos2[file.name]?.let { if (it) "DOS2 on" else "DOS2 off" }
                    val working = if (file.name in marks.working) "Working" else null
                    Text(listOfNotNull(file.sizeText(), working, dos2).joinToString("  ·  "))
                },
                leadingContent = {
                    val (colors, onColors) = if (hosted) hostingBadgeColors() else downloadedBadgeColors()
                    IconBadge(
                        if (hasRomExtension(file.name)) Icons.Rounded.Memory else Icons.Rounded.Save,
                        container = colors,
                        content = onColors,
                    )
                },
                trailingContent = {
                    Row {
                        LibraryMenu(file, hosted, library, marks) {
                            onReport(ReportTarget(fileName = file.name, size = formatSize(file.size)))
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

// a smaller font than the default so more of the name fits; a click shows all of it
@Composable
private fun ExpandableName(name: String, fontWeight: FontWeight? = null) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable { expanded = !expanded }
    )
}

// working mark, the DOS2 setting of the image and delete
@Composable
private fun LibraryMenu(file: LibraryFile, hosted: Boolean, library: DiskLibrary, marks: ImageMarks, onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${file.name}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val working = file.name in marks.working
            DropdownMenuItem(
                text = { Text("Working") },
                leadingIcon = { Checkbox(checked = working, onCheckedChange = null) },
                onClick = {
                    marks.onWorking(file.name, !working)
                    open = false
                }
            )
            HorizontalDivider()
            val own = marks.imageDos2[file.name]
            listOf(
                null to "DOS2 default (${if (marks.defaultDos2) "on" else "off"})",
                true to "DOS2 on",
                false to "DOS2 off",
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = own == value, onClick = null) },
                    onClick = {
                        marks.onImageDos2(file.name, value)
                        open = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Report as not working") },
                leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                onClick = {
                    open = false
                    onReport()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                enabled = !hosted,
                onClick = {
                    library.delete(file)
                    open = false
                }
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
