package com.zyra.app.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetExtractor {
    fun extractAsset(context: Context, filename: String): String {
        val file = File(context.filesDir, filename)
        if (file.exists()) {
            return file.absolutePath
        }

        try {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            // Asset might not exist (e.g. user hasn't added it yet)
            // Return path anyway so File(path).exists() check fails gracefully later
        }
        return file.absolutePath
    }
}
