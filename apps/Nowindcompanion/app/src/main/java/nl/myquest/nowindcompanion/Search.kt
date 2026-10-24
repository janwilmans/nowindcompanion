package nl.myquest.nowindcompanion

// every word of the query is a keyword. Entries with all keywords come first, then those with the first keyword,
// then those with the second (but not the first), and so on; alphabetical within each group.
// Entries without any keyword are left out.
fun searchEntries(entries: List<RemoteEntry>, query: String): List<RemoteEntry> {
    val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (keywords.isEmpty()) return entries
    return entries
        .mapNotNull { entry ->
            val found = keywords.map { entry.name.contains(it, ignoreCase = true) }
            val first = found.indexOf(true)
            when {
                first < 0 -> null
                found.all { it } -> 0 to entry
                else -> first + 1 to entry
            }
        }
        .sortedWith(compareBy<Pair<Int, RemoteEntry>> { it.first }.thenBy { it.second.name.lowercase() })
        .map { it.second }
}
