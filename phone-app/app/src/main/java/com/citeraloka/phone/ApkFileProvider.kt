package com.citeraloka.phone

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class ApkFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: throw FileNotFoundException("Context null")
        val fileName = uri.lastPathSegment ?: "update.apk"
        val file = File(context.cacheDir, fileName)

        if (!file.exists()) {
            throw FileNotFoundException("Fail APK tidak ditemui: ${file.absolutePath}")
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        return "application/vnd.android.package-archive"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val context = context ?: return MatrixCursor(emptyArray())
        val fileName = uri.lastPathSegment ?: "CiteraLoka.apk"
        val file = File(context.cacheDir, fileName)

        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = cursor.newRow()

        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(col, file.name)
                OpenableColumns.SIZE -> row.add(col, if (file.exists()) file.length() else 0L)
                else -> row.add(col, null)
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
