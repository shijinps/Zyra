package com.zyra.app.logic

import android.content.Context

/**
 * ZyraConfig - Single Source of Truth for Zyra Backend configuration.
 * When the server domain changes, change DEFAULT_BASE_URL here and the
 * entire Android app automatically updates everywhere!
 */
object ZyraConfig {
    const val DEFAULT_BASE_URL = "https://apizyra.shijin.in"
    const val DEFAULT_LIVEKIT_URL = "wss://zyra-sbn0wnp2.livekit.cloud"
    const val DEFAULT_SECRET_KEY = "zyra-secret-key-123"

    private const val PREFS_NAME = "zyra_config_prefs"
    private const val KEY_BASE_URL = "custom_base_url"

    fun getBaseUrl(context: Context? = null): String {
        if (context == null) return DEFAULT_BASE_URL
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.trimEnd('/') ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, newUrl: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, newUrl.trimEnd('/')).apply()
    }

    fun getVoiceGatewayUrl(context: Context? = null): String {
        val base = getBaseUrl(context)
        val wsBase = when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://")
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://")
            else -> "wss://$base"
        }
        return "$wsBase/voice"
    }

    fun getHealthUrl(context: Context? = null): String = "${getBaseUrl(context)}/health"
    fun getLiveKitTokenUrl(context: Context? = null): String = "${getBaseUrl(context)}/livekit/token"
}
