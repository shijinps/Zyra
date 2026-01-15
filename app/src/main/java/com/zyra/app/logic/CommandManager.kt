package com.zyra.app.logic

import android.content.Context
import android.content.SharedPreferences

class CommandManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zyra_commands", Context.MODE_PRIVATE)

    fun addCommand(phrase: String, phoneNumber: String, contactName: String) {
        prefs.edit()
            .putString(phrase.lowercase(), "$phoneNumber|$contactName")
            .apply()
    }

    fun removeCommand(phrase: String) {
        prefs.edit().remove(phrase.lowercase()).apply()
    }

    fun getCommandTarget(phrase: String): Pair<String, String>? {
        val data = prefs.getString(phrase.lowercase(), null) ?: return null
        val (number, name) = data.split("|")
        return number to name
    }

    fun getAllCommands(): Map<String, String> {
        val all = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                val (_, name) = value.split("|")
                all[key] = name
            }
        }
        return all
    }
}
