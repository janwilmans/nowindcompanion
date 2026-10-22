package nl.myquest.nowindcompanion

import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

// some sites (download.file-hunter.com) answer '405 Method Not Allowed' to clients without a browser-like user agent
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) NowindCompanion"

data class RemoteEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: String = "",
    // downloads from the Nowind API start with a line of metadata, see DiskLibrary
    val fromNowindApi: Boolean = false,
) {
    val isDiskImage: Boolean
        get() = !isDirectory && (fromNowindApi || hasDiskExtension(name))

    // the name of the downloaded file in the disk library
    val fileName: String
        get() = safeFileName(if (fromNowindApi) "$name.dsk" else name)
}

private fun hasDiskExtension(name: String) =
    name.endsWith(".zip", ignoreCase = true) || name.endsWith(".dsk", ignoreCase = true)

fun safeFileName(name: String): String = name.replace(Regex("""[\\/:*?"<>|]"""), "_")

fun openHttpConnection(url: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", USER_AGENT)
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    return connection
}

// "273.61 KB", "12K", "1.2 MB" or a byte count like "737280"; a bare short number is more likely a year
private val SIZE = Regex("""(?i)^(\d+(\.\d+)?\s*([KMG]i?B?|B|bytes)|\d{5,})$""")

// Reads both web server directory indexes and ordinary pages that link to disk images:
// links to sub folders of the page (ending in '/') become directories, links to .zip/.dsk files become files.
fun parseHtmlListing(html: String, baseUrl: String): List<RemoteEntry> {
    val document = Jsoup.parse(html, baseUrl)
    val path = baseUrl.substringBefore('?')
    val folder = if (path.endsWith("/")) path else path.substringBeforeLast('/') + "/"
    val seen = mutableSetOf<String>()
    return document.select("a[href]").mapNotNull { link ->
        val href = link.attr("href")
        if (href.startsWith("#") || href.startsWith("?") || href.startsWith("mailto:")) return@mapNotNull null
        val url = link.absUrl("href").substringBefore('#')
        val urlPath = url.substringBefore('?')
        val isDirectory = urlPath.endsWith("/") && urlPath.startsWith(folder) && urlPath.length > folder.length
        if (!isDirectory && !hasDiskExtension(urlPath)) return@mapNotNull null
        if (!seen.add(url)) return@mapNotNull null
        // the link text is often 'Download', the url always has the real name
        val name = decodeUrlPart(urlPath.trimEnd('/').substringAfterLast('/'))
        val size = if (isDirectory) "" else link.closest("tr")?.select("td")
            ?.map { it.text().trim() }
            ?.firstOrNull { SIZE.matches(it) }
            .orEmpty()
        RemoteEntry(name, url, isDirectory, size)
    }
}

// The Nowind API lists one 'name<TAB>size in bytes' per line; a file is downloaded by its index in that same listing.
fun parseNowindApiListing(text: String, listUrl: String): List<RemoteEntry> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size == 1 && lines[0].startsWith("No files found")) return emptyList()
    return lines.mapIndexedNotNull { index, line ->
        val name = line.substringBefore('\t').trim()
        val bytes = line.substringAfter('\t', "").trim().toLongOrNull() ?: return@mapIndexedNotNull null
        RemoteEntry(name, "$listUrl&download=$index", isDirectory = false, size = formatSize(bytes), fromNowindApi = true)
    }
}

// the MSX generations the Nowind API can filter on
enum class MsxFilter(val label: String, val param: String?) {
    All("All", null),
    Msx1("MSX1", "1"),
    Msx2("MSX2", "2"),
    Msx2Plus("MSX2+", "2+"),    // the API needs a literal '+', '%2B' is ignored and lists everything
    TurboR("Turbo R", "turbo-r"),
}

fun nowindApiListUrl(baseUrl: String, filter: MsxFilter): String =
    "$baseUrl?type=DSK" + (filter.param?.let { "&msx=$it" } ?: "")

fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f KB".format(bytes / 1024.0)
}

data class NowindApiHeader(val size: Long?, val disks: Int)

// the first line of a Nowind API download, e.g. 'size:1474560,disks:2,name:3 Wishes (1988)(MesLibres)(jp).dsk'
// (the name can contain commas, so only size and disks are taken from it)
fun parseNowindApiHeader(line: String): NowindApiHeader {
    val size = Regex("""(?:^|,)size:(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()
    val disks = Regex("""(?:^|,)disks:(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    return NowindApiHeader(size, disks.coerceAtLeast(1))
}

private val DISK_OF = Regex("""^(.*?)(\s*)\(Disk\s*\d+\s*of\s*\d+\)\s*$""", RegexOption.IGNORE_CASE)

// A multi disk game is downloaded from the Nowind API as all disks concatenated; these are the file names for the
// separate disks, named like the API lists them: 'Game (1988)(Disk 1 of 2).dsk'
fun diskSetFileNames(entryName: String, disks: Int): List<String> {
    val match = DISK_OF.find(entryName)
    val base = match?.groupValues?.get(1) ?: entryName
    val separator = match?.groupValues?.get(2) ?: " "
    return (1..disks).map { safeFileName("$base$separator(Disk $it of $disks).dsk") }
}
