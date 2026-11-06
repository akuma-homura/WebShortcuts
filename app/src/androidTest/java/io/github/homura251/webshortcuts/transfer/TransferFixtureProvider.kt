package io.github.homura251.webshortcuts.transfer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Instrumentation APK only: exposes three synthetic fixtures, never production app storage. */
class TransferFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment
        if (name !in setOf("input.html", "export.zip", "export.html")) throw FileNotFoundException()
        return File(context!!.cacheDir, "transfer-test-fixtures/$name").also { it.parentFile!!.mkdirs() }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment == "input.html" && mode != "r") throw FileNotFoundException()
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))
    }
    override fun getType(uri: Uri) = if (uri.lastPathSegment?.endsWith("zip") == true) "application/zip" else "text/html"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(Array<Any?>(columns.size) { index -> when (columns[index]) {
            OpenableColumns.DISPLAY_NAME -> file.name
            OpenableColumns.SIZE -> file.length()
            else -> null
        } }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        if (uri.lastPathSegment != "input.html" && file(uri).delete()) 1 else 0
}
