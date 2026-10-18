package com.millerchnz.ghzexplorer.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun Context.shareTextFile(filename: String, body: String) {
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, filename)
    file.writeText(body)
    val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(filename, uri)
    }
    val chooser = Intent.createChooser(send, "Export").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (this@shareTextFile !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}
