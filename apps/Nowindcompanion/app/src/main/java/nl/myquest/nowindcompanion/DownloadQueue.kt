package nl.myquest.nowindcompanion

data class DownloadRequest(val entry: RemoteEntry, val hostWhenDone: Boolean)

// pending downloads in order, 'download and host' requests jump the queue; not thread-safe
class DownloadQueue {
    private val items = ArrayDeque<DownloadRequest>()

    val pending: List<DownloadRequest>
        get() = items.toList()

    fun contains(url: String): Boolean = items.any { it.entry.url == url }

    // returns false when the entry was already queued
    fun enqueue(entry: RemoteEntry): Boolean {
        if (contains(entry.url)) return false
        items.addLast(DownloadRequest(entry, hostWhenDone = false))
        return true
    }

    // moves the entry to the front (also when it was already queued) and hosts it when done
    fun enqueueFirst(entry: RemoteEntry) {
        remove(entry.url)
        items.addFirst(DownloadRequest(entry, hostWhenDone = true))
    }

    fun remove(url: String) {
        items.removeAll { it.entry.url == url }
    }

    fun next(): DownloadRequest? = items.removeFirstOrNull()
}
