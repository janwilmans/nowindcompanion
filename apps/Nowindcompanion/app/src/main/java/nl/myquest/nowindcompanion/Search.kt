package nl.myquest.nowindcompanion

// every word of the query is a keyword. Entries with all keywords come first, then those with the first keyword,
// then those with the second (but not the first), and so on; alphabetical within each group.
// Entries without any keyword are left out.
fun searchEntries(entries: List<RemoteEntry>, query: String): List<RemoteEntry> = search(entries, query) { it.name }

fun <T> search(items: List<T>, query: String, name: (T) -> String): List<T> {
    val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (keywords.isEmpty()) return items
    return items
        .mapNotNull { entry ->
            val found = keywords.map { name(entry).contains(it, ignoreCase = true) }
            val first = found.indexOf(true)
            when {
                first < 0 -> null
                found.all { it } -> 0 to entry
                else -> first + 1 to entry
            }
        }
        .sortedWith(compareBy<Pair<Int, T>> { it.first }.thenBy { name(it.second).lowercase() })
        .map { it.second }
}
