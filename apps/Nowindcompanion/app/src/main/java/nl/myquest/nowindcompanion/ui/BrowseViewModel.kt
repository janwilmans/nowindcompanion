package nl.myquest.nowindcompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.myquest.nowindcompanion.DiskSource
import nl.myquest.nowindcompanion.MsxFilter
import nl.myquest.nowindcompanion.RemoteEntry
import nl.myquest.nowindcompanion.SourceKind
import nl.myquest.nowindcompanion.decodeUrlPart
import nl.myquest.nowindcompanion.fetchText
import nl.myquest.nowindcompanion.nowindApiListUrl
import nl.myquest.nowindcompanion.parseHtmlListing
import nl.myquest.nowindcompanion.parseNowindApiListing

sealed interface ListingState {
    object Loading : ListingState
    data class Error(val message: String) : ListingState
    data class Loaded(val entries: List<RemoteEntry>) : ListingState
}

// the folders below the source url: "https://site/Games/MSX2/DSK/" in source "https://site/Games/" -> ["MSX2", "DSK"]
fun pathSegments(sourceUrl: String, url: String): List<String> =
    url.removePrefix(sourceUrl).split('/').filter { it.isNotEmpty() }.map { decodeUrlPart(it) }

class BrowseViewModel : ViewModel() {
    private val _source = MutableStateFlow<DiskSource?>(null)
    val source: StateFlow<DiskSource?> = _source

    // for web pages and directory listings: the folders opened so far, the last one is shown
    private val _stack = MutableStateFlow<List<String>>(emptyList())
    val stack: StateFlow<List<String>> = _stack

    // for the Nowind API
    private val _msxFilter = MutableStateFlow(MsxFilter.All)
    val msxFilter: StateFlow<MsxFilter> = _msxFilter

    private val _listing = MutableStateFlow<ListingState>(ListingState.Loaded(emptyList()))
    val listing: StateFlow<ListingState> = _listing

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter

    // listings can be large (the MSX2 DSK folder of file-hunter is ~800KB of html), so keep what was loaded
    private val cache = mutableMapOf<String, List<RemoteEntry>>()
    private var loadJob: Job? = null

    // called with the enabled sources whenever they change; keeps the selected source when it is still enabled
    fun setSources(sources: List<DiskSource>) {
        val current = _source.value
        val kept = sources.find { it.url == current?.url }
        if (kept != null && kept.kind == current?.kind) return
        select(kept ?: sources.firstOrNull())
    }

    fun select(source: DiskSource?) {
        _source.value = source
        _stack.value = source?.let { listOf(it.url) }.orEmpty()
        _filter.value = ""
        load()
    }

    fun setMsxFilter(value: MsxFilter) {
        _msxFilter.value = value
        _filter.value = ""
        load()
    }

    fun open(entry: RemoteEntry) {
        if (!entry.isDirectory) return
        _stack.value = _stack.value + entry.url
        _filter.value = ""
        load()
    }

    // returns false when already at the top of the source
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
        requestUrl()?.let { cache.remove(it) }
        load()
    }

    private fun requestUrl(): String? {
        val source = _source.value ?: return null
        return if (source.kind == SourceKind.NowindApi) nowindApiListUrl(source.url, _msxFilter.value) else _stack.value.lastOrNull()
    }

    private fun load() {
        loadJob?.cancel()
        val source = _source.value
        val url = requestUrl()
        if (source == null || url == null) {
            _listing.value = ListingState.Loaded(emptyList())
            return
        }
        cache[url]?.let {
            _listing.value = ListingState.Loaded(it)
            return
        }
        _listing.value = ListingState.Loading
        loadJob = viewModelScope.launch {
            _listing.value = try {
                val entries = withContext(Dispatchers.IO) {
                    val text = fetchText(url)
                    if (source.kind == SourceKind.NowindApi) parseNowindApiListing(text, url) else parseHtmlListing(text, url)
                }.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
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
