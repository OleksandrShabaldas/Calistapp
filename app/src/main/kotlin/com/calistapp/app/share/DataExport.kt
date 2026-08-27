package com.calistapp.app.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Write [content] to a shareable file and fire the system share sheet — the path behind "Export
 * training data". Reuses the same `shared` cache dir and FileProvider authority as the image share,
 * so no extra manifest wiring is needed. A tracker people invest months in shouldn't trap their data.
 */
suspend fun shareTextFile(
    context: Context,
    content: String,
    fileName: String,
    mime: String,
    subject: String,
) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.shareprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
