package nl.myquest.nowindcompanion

import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

const val NOWIND_API_URL = "https://api.file-hunter.com/nowind.php"

enum class SourceKind(val label: String) {
    NowindApi("Nowind API"),            // plain text 'name<TAB>size' lines, see filehunter/nowind.php
    DirectoryListing("Directory listing"),
    WebPage("Web page"),                // a page with links to .zip / .dsk files
}

// a place to find disk images, shown as a chip in Browse
data class DiskSource(
    val url: String,
    val kind: SourceKind,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
) {
    val label: String
        get() = if (builtIn) "default" else shortUrl(url)
}

val DEFAULT_SOURCE = DiskSource(NOWIND_API_URL, SourceKind.NowindApi, enabled = true, builtIn = true)

// "https://download.file-hunter.com/Games/MSX2/DSK/" -> "download.file-hunter.com/Games/MSX2/DSK"
fun shortUrl(url: String): String =
    decodeUrlPart(url.substringAfter("://").removePrefix("www.").trimEnd('/'))

// URLDecoder treats '+' as space, which is wrong for paths like 'MSX2+'
fun decodeUrlPart(value: String): String =
    try {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    }

// user sources are stored one per line as: enabled<TAB>kind<TAB>url
fun encodeSources(sources: List<DiskSource>): String =
    sources.joinToString("\n") { "${if (it.enabled) 1 else 0}\t${it.kind.name}\t${it.url}" }

fun decodeSources(text: String?): List<DiskSource> =
    text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val kind = SourceKind.values().find { it.name == parts[1] } ?: return@mapNotNull null
        DiskSource(parts[2], kind, enabled = parts[0] == "1")
    }

fun normalizeSourceUrl(input: String): String {
    val url = input.trim()
    return if (url.contains("://")) url else "https://$url"
}

private val NOWIND_LINE = Regex("""^[^\t<>]+\t\d+$""")

data class Detection(val kind: SourceKind, val entries: Int)

// decides how a source has to be read, from the response to its url
fun classifySource(body: String, url: String): Detection {
    val lines = body.lineSequence().filter { it.isNotBlank() }.take(5).toList()
    if (lines.isNotEmpty() && lines.all { NOWIND_LINE.matches(it) }) {
        return Detection(SourceKind.NowindApi, parseNowindApiListing(body, url).size)
    }
    if (!body.contains('<')) {
        throw IOException("not a web page or Nowind API")
    }
    val title = Jsoup.parse(body).run { title() + " " + select("h1, h2").text() }
    val entries = parseHtmlListing(body, url)
    val isIndex = title.contains("Index of", ignoreCase = true) || entries.any { it.isDirectory }
    return Detection(if (isIndex) SourceKind.DirectoryListing else SourceKind.WebPage, entries.size)
}

// blocking, call from Dispatchers.IO
fun detectSource(url: String): Detection = classifySource(fetchText(url), url)

fun fetchText(url: String): String {
    val connection = openHttpConnection(url)
    try {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP $code" + if (code == 403) " (the site refuses this app)" else "")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

