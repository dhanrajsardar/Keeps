package com.keepsnotes.moments

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

/** Grants read-only access to imported app-private media for the Android viewer apps. */
class MediaProvider : ContentProvider() {
    override fun onCreate() = true

    private fun resolve(uri: Uri): File {
        val root = File(requireNotNull(context).filesDir, "media").canonicalFile
        val candidate = File(root, uri.lastPathSegment ?: "").canonicalFile
        require(candidate.parentFile == root && candidate.isFile) { "Unknown media file" }
        return candidate
    }

    override fun getType(uri: Uri): String {
        val extension = resolve(uri).extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = resolve(uri)
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(columns.map { when (it) { OpenableColumns.DISPLAY_NAME -> file.name.substringAfter('_', file.name); OpenableColumns.SIZE -> file.length(); else -> null } })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Media is read only" }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
}
