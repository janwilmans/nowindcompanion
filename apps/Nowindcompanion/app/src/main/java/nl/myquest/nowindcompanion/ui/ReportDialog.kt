package nl.myquest.nowindcompanion.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nl.myquest.nowindcompanion.REPORT_EMAIL
import nl.myquest.nowindcompanion.ReportTarget
import nl.myquest.nowindcompanion.reportSubject

// collects what is needed to look into a game that does not work, to send by email or copy
@Composable
fun ReportDialog(target: ReportTarget, buildReport: (ReportTarget, String) -> String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    val report = buildReport(target, note)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report: not working") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sends what is below to $REPORT_EMAIL. Please say what goes wrong.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What goes wrong?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    copyToClipboard(context, report)
                    Toast.makeText(context, "Copied to the clipboard", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                TextButton(onClick = {
                    if (!sendReportEmail(context, reportSubject(target), report)) {
                        copyToClipboard(context, report)
                        Toast.makeText(context, "No email app found, the report is copied", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Send email") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun sendReportEmail(context: Context, subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nowind Companion report", text))
}
