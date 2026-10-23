package nl.myquest.nowindcompanion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

const val APP_FOLDER = "nowindcompanion"
private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

// Download/nowindcompanion, visible over USB file transfer and kept when the app is removed.
// Since Android 11 a reinstalled app can not read what it wrote there before, so the folder is reached through
// the Storage Access Framework: the user grants access once with the system folder picker (again after a reinstall)
class StorageFolder(private val context: Context) {
    private val _root = MutableStateFlow(grantedRoot())
    val root: StateFlow<DocumentFile?> = _root

    // the app only ever holds one folder grant, so the grant itself remembers which folder was picked
    private fun grantedRoot(): DocumentFile? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
            ?.let { DocumentFile.fromTreeUri(context, it.uri) }
            ?.takeIf { it.isDirectory }

    // where the folder picker starts; creating the folder up front saves the user a 'new folder' step
    // (best effort, plain file access to Download is not allowed on every Android version)
    fun pickerStart(): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_FOLDER)
        folder.mkdirs()
        val path = if (folder.isDirectory) "${Environment.DIRECTORY_DOWNLOADS}/$APP_FOLDER" else Environment.DIRECTORY_DOWNLOADS
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$path")
    }

    fun grant(tree: Uri) {
        val resolver = context.contentResolver
        resolver.persistedUriPermissions.filter { it.uri != tree }.forEach {
            resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        _root.value = grantedRoot()
    }

    fun open(file: DocumentFile): InputStream =
        context.contentResolver.openInputStream(file.uri) ?: throw IOException("could not open ${file.name}")

    // truncates the file, and syncs it before returning so a power loss does not leave half a file
    fun <T> write(file: DocumentFile, block: (FileOutputStream) -> T): T {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "wt")
            ?: throw IOException("could not write ${file.name}")
        return descriptor.use {
            FileOutputStream(it.fileDescriptor).use { output ->
                block(output).also { output.fd.sync() }
            }
        }
    }
}

// a sub folder by file type, like 'disks'
fun DocumentFile.folder(name: String): DocumentFile =
    findFile(name)?.takeIf { it.isDirectory }
        ?: createDirectory(name)
        ?: throw IOException("could not create folder $name")

// createFile picks a new name like 'x (1)' when the name is taken, so make room first
fun DocumentFile.replaceFile(name: String): DocumentFile {
    findFile(name)?.delete()
    return createFile("application/octet-stream", name) ?: throw IOException("could not create $name")
}
