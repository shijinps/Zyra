package com.zyra.app.logic.brain

object SynonymNormalizer {
    private val synonymMap = mapOf(
        "sound" to "volume",
        "audio" to "volume",
        "loudness" to "volume",
        "pic" to "photo",
        "picture" to "photo",
        "image" to "photo",
        "snap" to "photo",
        "message" to "msg",
        "text" to "msg",
        "application" to "app",
        "start" to "open",
        "launch" to "open",
        "run" to "open"
    )

    fun normalize(text: String): String {
        var normalized = text.lowercase()
        synonymMap.forEach { (synonym, canonical) ->
            // Replace whole words only
            val regex = "\\b$synonym\\b".toRegex()
            normalized = normalized.replace(regex, canonical)
        }
        return normalized
    }
}
