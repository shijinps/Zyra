package com.zyra.app.logic

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

object AssetModelManager {
    private const val TAG = "AssetModelManager"
    private const val PREFS_NAME = "zyra_asset_model_prefs"

    /**
     * Extracts an asset to filesDir with guaranteed overwrite when the SHA-256 content hash changes.
     */
    fun extractAssetIfChanged(context: Context, assetName: String): File? {
        val targetFile = File(context.filesDir, assetName)
        return try {
            val assetBytes = context.assets.open(assetName).use { it.readBytes() }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(assetBytes)
                .joinToString("") { "%02x".format(it) }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hashKey = "hash_$assetName"
            val savedHash = prefs.getString(hashKey, null)

            if (!targetFile.exists() || savedHash != sha256) {
                targetFile.writeBytes(assetBytes)
                prefs.edit().putString(hashKey, sha256).apply()
                Log.i(TAG, "Extracted and verified asset $assetName (${assetBytes.size} bytes, sha256=$sha256) to ${targetFile.absolutePath}")
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset: $assetName", e)
            null
        }
    }

    /**
     * Synchronizes wake word models (zyra.ppn and porcupine_params.pv if present).
     * Returns the File pointer for zyra.ppn.
     */
    fun syncWakeWordModels(context: Context): File? {
        // 1. Sync zyra.ppn
        val ppnFile = extractAssetIfChanged(context, "zyra.ppn")

        // 2. Sync porcupine_params.pv if present in app assets
        try {
            val rootAssets = context.assets.list("") ?: emptyArray()
            if ("porcupine_params.pv" in rootAssets) {
                extractAssetIfChanged(context, "porcupine_params.pv")
            }
        } catch (e: Exception) {
            Log.d(TAG, "porcupine_params.pv not present in root assets, using SDK bundled parameters")
        }

        return ppnFile
    }
}
