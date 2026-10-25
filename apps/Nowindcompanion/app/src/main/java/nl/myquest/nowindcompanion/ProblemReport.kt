package nl.myquest.nowindcompanion

import android.os.Build

const val REPORT_EMAIL = "nowindcompanion@nullptr.nl"

// the game a problem is reported for; fileName is the file as downloaded (.zip, .dsk or .rom).
// source is known for archive entries, library files get it from what was stored when they were downloaded
data class ReportTarget(
    val fileName: String,
    val size: String? = null,
    val source: String? = null,
)

fun reportSubject(target: ReportTarget) = "Not working: ${target.fileName}"

// "https://download.example.com/Games/MSX2/game.zip" -> "download.example.com/Games/MSX2", without the file
fun sourceNameOf(entryUrl: String): String = shortUrl(entryUrl.substringBefore('?').substringBeforeLast('/'))

// the text of the email, also what is copied to the clipboard
fun buildProblemReport(
    target: ReportTarget,
    note: String,
    settings: Settings,
    hostedDisk: String?,
    debugLog: List<String>,
    firmware: FirmwareCache,
): String {
    val sb = StringBuilder()

    fun section(title: String, vararg lines: Pair<String, String?>) {
        sb.append("\n").append(title).append("\n")
        for ((label, value) in lines) if (value != null) sb.append("  ").append(label).append(": ").append(value).append("\n")
    }

    sb.append("Nowind Companion problem report: this game does not work\n")
    if (note.isNotBlank()) sb.append("\nWhat goes wrong\n").append(note.trim()).append("\n")
    section(
        "Game",
        "File" to target.fileName,
        "Size" to target.size,
        "Hosted now" to if (target.fileName == hostedDisk) "yes" else "no",
    )
    section(
        "Source",
        "Name" to ((target.source ?: settings.origins[target.fileName]) ?: "unknown, the file was not downloaded by this app"),
    )
    val ownDos2 = settings.imageDos2[target.fileName]
    section(
        "Settings",
        "DOS2" to if (settings.dos2For(target.fileName)) "on" else "off",
        "DOS2 setting of this game" to (ownDos2?.let { if (it) "on" else "off" } ?: "default (${if (settings.enableDos2) "on" else "off"})"),
        "Marked as working" to if (target.fileName in settings.working) "yes" else "no",
        "Keep active in background" to if (settings.keepActive) "on" else "off",
        "Debug information" to if (settings.debugEnabled) "on" else "off",
    )
    section(
        "App",
        "Version" to BuildConfig.VERSION_NAME,
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
    )
    val firmwareText = firmware.toReport()
    sb.append("\nFirmware\n")
    sb.append(if (firmwareText.isEmpty()) "  not available, use Check version and Verify in the firmware menu first\n" else firmwareText + "\n")
    sb.append("\nDebug log\n")
    sb.append(
        when {
            debugLog.isNotEmpty() -> debugLog.joinToString("\n") { "  $it" } + "\n"
            settings.debugEnabled -> "  empty\n"
            else -> "  not available, debug information is off in Settings\n"
        }
    )
    return sb.toString()
}
