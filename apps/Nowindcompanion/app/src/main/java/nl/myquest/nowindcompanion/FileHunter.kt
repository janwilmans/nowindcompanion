package nl.myquest.nowindcompanion

import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

const val FILE_HUNTER_ROOT = "https://download.file-hunter.com/"

// download.file-hunter.com answers '405 Method Not Allowed' to some clients (like curl) without a browser-like user agent
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) NowindCompanion"

data class RemoteEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: String = "",
) {
    // only these can be hosted, other files in the archive (txt, rar, apk) are not shown
    val isDiskImage: Boolean
        get() = !isDirectory && (name.endsWith(".zip", ignoreCase = true) || name.endsWith(".dsk", ignoreCase = true))
}

fun openHttpConnection(url: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", USER_AGENT)
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    return connection
}

// parses the Abyss web server directory index: <table class="listing"> with rows of name, size and type
fun parseListing(html: String, baseUrl: String): List<RemoteEntry> {
    val document = Jsoup.parse(html, baseUrl)
    return document.select("table.listing tr").mapNotNull { row ->
        val cells = row.select("td")
        val link = cells.firstOrNull()?.selectFirst("a") ?: return@mapNotNull null
        val href = link.attr("href")
        if (href.startsWith("..")) return@mapNotNull null
        val type = cells.getOrNull(2)?.text()?.trim().orEmpty()
        val isDirectory = href.endsWith("/") || type.equals("Directory", ignoreCase = true)
        val size = cells.getOrNull(1)?.text()?.trim().orEmpty()
        RemoteEntry(
            name = link.text().trim().removeSuffix("/"),
            url = link.absUrl("href"),
            isDirectory = isDirectory,
            size = if (isDirectory || size == "-") "" else size,
        )
    }.filter { it.isDirectory || it.isDiskImage }
}

object FileHunterClient {
    // blocking, call from Dispatchers.IO
    fun list(url: String): List<RemoteEntry> {
        val connection = openHttpConnection(url)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP $code for $url")
            }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            return parseListing(html, url)
        } finally {
            connection.disconnect()
        }
    }
}
