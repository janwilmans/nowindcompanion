package nl.myquest.nowindcompanion

// _IMAGE("<keyword>") / CALL IMAGE on the MSX: the first disk or ROM that matches, from the library first, then the archive

// MSX users may type the file name with its extension, the archive names have none
fun imageKeyword(name: String): String =
    name.trim().replace(Regex("""\.(dsk|rom|zip)$""", RegexOption.IGNORE_CASE), "")

// what _IMAGE asks for: the name or keywords, and '<name>+2' to enable DOS2 or '<name>+1' to turn it off
data class ImageRequest(val name: String, val enableDos2: Boolean?)

private val DOS_SUFFIX = Regex("""\s*\+\s*([12])\s*$""")

fun parseImageRequest(text: String): ImageRequest {
    val suffix = DOS_SUFFIX.find(text) ?: return ImageRequest(text.trim(), null)
    return ImageRequest(text.substring(0, suffix.range.first).trim(), suffix.groupValues[1] == "2")
}

// One result, so unlike the Browse search every keyword must be in the name, anywhere ('GRADIUS 2' must not find
// a Knightmare with a 2 in it). The shortest name comes first: hacks and translations add to the name of the
// original ('Knightmare' does not end up at a Bullet Hell Hack), then alphabetical.
fun <T> bestMatch(items: List<T>, query: String, name: (T) -> String): T? {
    val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (keywords.isEmpty()) return null
    return items
        .filter { item -> keywords.all { name(item).contains(it, ignoreCase = true) } }
        .sortedWith(compareBy<T> { name(it).length }.thenBy { name(it).lowercase() })
        .firstOrNull()
}

// the disk image or ROM of the archive listings to host; ROMs that can not be started are skipped
fun findInArchive(entries: List<RemoteEntry>, keyword: String): RemoteEntry? =
    bestMatch(entries.filter { !it.isDirectory && it.startable }, keyword) { it.name }

// blocking: what a source has at its top, both disk images and ROMs for the Nowind API
fun archiveListing(source: DiskSource): List<RemoteEntry> = when (source.kind) {
    SourceKind.NowindApi -> ArchiveType.values().flatMap { type ->
        val url = nowindApiListUrl(source.url, MsxFilter.All, type)
        parseNowindApiListing(fetchText(url).text, url, rom = type == ArchiveType.Roms)
    }
    // directory listings and web pages are trees, only the files on the first page are searched
    else -> fetchText(source.url).let { parseHtmlListing(it.text, it.url) }
}
