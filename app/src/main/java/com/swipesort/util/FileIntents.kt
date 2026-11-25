package com.swipesort.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opens or shares a file with other apps through a FileProvider content:// link. */
object FileIntents {

    private fun uriFor(context: Context, path: String) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

    /** Opens the original file in the system's viewer / player / reader for its type. */
    fun open(context: Context, path: String, mime: String?) {
        try {
            val uri = uriFor(context, path)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "This file's location can't be shared", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, path: String, mime: String?) {
        try {
            val uri = uriFor(context, path)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(File(path).name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${File(path).name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't share this file", Toast.LENGTH_SHORT).show()
        }
    }
}
