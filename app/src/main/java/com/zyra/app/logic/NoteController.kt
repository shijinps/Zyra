package com.zyra.app.logic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Note Controller - Quick voice notes
 * "Take a note: Buy groceries"
 * "Read my notes"
 */
object NoteController {
    
    private const val NOTES_FILE = "zyra_notes.json"
    
    /**
     * Add a new note
     */
    fun addNote(context: Context, content: String): Note {
        val notes = getAllNotes(context).toMutableList()
        val newNote = Note(
            id = System.currentTimeMillis().toInt(),
            content = content,
            timestamp = System.currentTimeMillis()
        )
        
        notes.add(0, newNote) // Add to beginning
        saveNotes(context, notes)
        return newNote
    }
    
    /**
     * Get all notes
     */
    fun getAllNotes(context: Context): List<Note> {
        val file = File(context.filesDir, NOTES_FILE)
        if (!file.exists()) return emptyList()
        
        val json = file.readText()
        val jsonArray = JSONArray(json)
        val notes = mutableListOf<Note>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            notes.add(
                Note(
                    id = obj.getInt("id"),
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        
        return notes
    }
    
    /**
     * Delete a note
     */
    fun deleteNote(context: Context, noteId: Int): Boolean {
        val notes = getAllNotes(context).toMutableList()
        val removed = notes.removeIf { it.id == noteId }
        if (removed) {
            saveNotes(context, notes)
        }
        return removed
    }
    
    /**
     * Delete last note
     */
    fun deleteLastNote(context: Context): Boolean {
        val notes = getAllNotes(context)
        return if (notes.isNotEmpty()) {
            deleteNote(context, notes.first().id)
        } else false
    }
    
    /**
     * Clear all notes
     */
    fun clearAllNotes(context: Context): Int {
        val count = getAllNotes(context).size
        saveNotes(context, emptyList())
        return count
    }
    
    /**
     * Format notes for speech
     */
    fun formatNotesForSpeech(notes: List<Note>): String {
        return when {
            notes.isEmpty() -> "You have no notes"
            notes.size == 1 -> "You have 1 note: ${notes[0].content}"
            else -> {
                val noteList = notes.take(5).mapIndexed { index, note ->
                    "${index + 1}. ${note.content}"
                }.joinToString(". ")
                
                if (notes.size > 5) {
                    "You have ${notes.size} notes. Here are the first 5: $noteList"
                } else {
                    "You have ${notes.size} notes: $noteList"
                }
            }
        }
    }
    
    private fun saveNotes(context: Context, notes: List<Note>) {
        val jsonArray = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("content", note.content)
                put("timestamp", note.timestamp)
            }
            jsonArray.put(obj)
        }
        
        val file = File(context.filesDir, NOTES_FILE)
        file.writeText(jsonArray.toString())
    }
    
    data class Note(
        val id: Int,
        val content: String,
        val timestamp: Long
    )
}
