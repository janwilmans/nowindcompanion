package nl.myquest.nowindcompanion.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.myquest.nowindcompanion.FILE_HUNTER_ROOT
import nl.myquest.nowindcompanion.FileHunterClient
import nl.myquest.nowindcompanion.RemoteEntry

sealed interface ListingState {
    object Loading : ListingState
    data class Error(val message: String) : ListingState
    data class Loaded(val entries: List<RemoteEntry>) : ListingState
}

data class Shortcut(val label: String, val url: String)

val browseShortcuts = listOf(
    Shortcut("MSX1 disks", FILE_HUNTER_ROOT + "Games/MSX1/DSK/"),
    Shortcut("MSX2 disks", FILE_HUNTER_ROOT + "Games/MSX2/DSK/"),
    Shortcut("MSX2+ disks", FILE_HUNTER_ROOT + "Games/MSX2+/DSK/"),
    Shortcut("Turbo R disks", FILE_HUNTER_ROOT + "Games/MSX%20Turbo-R/DSK/"),
    Shortcut("Everything", FILE_HUNTER_ROOT),
)

// "https://download.file-hunter.com/Games/MSX2/DSK/" -> ["Games", "MSX2", "DSK"]
fun pathSegments(url: String): List<String> =
    url.removePrefix(FILE_HUNTER_ROOT).split('/').filter { it.isNotEmpty() }.map { Uri.decode(it) }

class BrowseViewModel : ViewModel() {
    // the folders opened so far, the last one is shown
    private val _stack = MutableStateFlow(listOf(browseShortcuts[1].url))
    val stack: StateFlow<List<String>> = _stack

    private val _listing = MutableStateFlow<ListingState>(ListingState.Loading)
    val listing: StateFlow<ListingState> = _listing

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter

    // the MSX2 DSK folder alone is ~800KB of html, so keep what was loaded
    private val cache = mutableMapOf<String, List<RemoteEntry>>()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun open(entry: RemoteEntry) {
        if (!entry.isDirectory) return
        _stack.value = _stack.value + entry.url
        _filter.value = ""
        load()
    }

    fun jumpTo(url: String) {
        _stack.value = listOf(url)
        _filter.value = ""
        load()
    }

    // returns false when already at the top
    fun up(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        _filter.value = ""
        load()
        return true
    }

    fun setFilter(text: String) {
        _filter.value = text
    }

    fun retry() {
        cache.remove(_stack.value.last())
        load()
    }

    private fun load() {
        val url = _stack.value.last()
        loadJob?.cancel()
        cache[url]?.let {
            _listing.value = ListingState.Loaded(it)
            return
        }
        _listing.value = ListingState.Loading
        loadJob = viewModelScope.launch {
            _listing.value = try {
                val entries = withContext(Dispatchers.IO) { FileHunterClient.list(url) }
                    .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                cache[url] = entries
                ListingState.Loaded(entries)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ListingState.Error(e.message ?: e.toString())
            }
        }
    }
}
