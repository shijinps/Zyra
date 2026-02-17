package com.zyra.app.logic

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Memory Database - Stores all conversations, actions, and learned patterns
 * This enables Zyra to remember past interactions and learn from them
 */
class MemoryDatabase(context: Context) : 
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    
    companion object {
        private const val DATABASE_NAME = "zyra_memory.db"
        private const val DATABASE_VERSION = 1
        
        // Table: conversations
        private const val TABLE_CONVERSATIONS = "conversations"
        private const val COL_CONV_ID = "id"
        private const val COL_CONV_TIMESTAMP = "timestamp"
        private const val COL_CONV_USER_INPUT = "user_input"
        private const val COL_CONV_ZYRA_RESPONSE = "zyra_response"
        private const val COL_CONV_INTENT_TYPE = "intent_type"
        private const val COL_CONV_SUCCESS = "success"
        private const val COL_CONV_CONTEXT = "context"
        private const val COL_CONV_CREATED_AT = "created_at"
        
        // Table: actions
        private const val TABLE_ACTIONS = "actions"
        private const val COL_ACTION_ID = "id"
        private const val COL_ACTION_TIMESTAMP = "timestamp"
        private const val COL_ACTION_TYPE = "action_type"
        private const val COL_ACTION_DATA = "action_data"
        private const val COL_ACTION_LOCATION = "location"
        private const val COL_ACTION_SUCCESS = "success"
        private const val COL_ACTION_CREATED_AT = "created_at"
        
        // Table: searches
        private const val TABLE_SEARCHES = "searches"
        private const val COL_SEARCH_ID = "id"
        private const val COL_SEARCH_TIMESTAMP = "timestamp"
        private const val COL_SEARCH_TYPE = "search_type"
        private const val COL_SEARCH_QUERY = "query"
        private const val COL_SEARCH_RESULT = "result"
        private const val COL_SEARCH_CREATED_AT = "created_at"
        
        // Table: patterns
        private const val TABLE_PATTERNS = "patterns"
        private const val COL_PATTERN_ID = "id"
        private const val COL_PATTERN_TYPE = "pattern_type"
        private const val COL_PATTERN_DATA = "pattern_data"
        private const val COL_PATTERN_FREQUENCY = "frequency"
        private const val COL_PATTERN_LAST_OCCURRENCE = "last_occurrence"
        private const val COL_PATTERN_CREATED_AT = "created_at"
        
        // Table: preferences
        private const val TABLE_PREFERENCES = "preferences"
        private const val COL_PREF_ID = "id"
        private const val COL_PREF_KEY = "preference_key"
        private const val COL_PREF_VALUE = "preference_value"
        private const val COL_PREF_CONFIDENCE = "confidence"
        private const val COL_PREF_UPDATED_AT = "updated_at"
    }
    
    override fun onCreate(db: SQLiteDatabase) {
        // Create conversations table
        db.execSQL("""
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COL_CONV_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CONV_TIMESTAMP BIGINT NOT NULL,
                $COL_CONV_USER_INPUT TEXT NOT NULL,
                $COL_CONV_ZYRA_RESPONSE TEXT NOT NULL,
                $COL_CONV_INTENT_TYPE TEXT,
                $COL_CONV_SUCCESS INTEGER DEFAULT 1,
                $COL_CONV_CONTEXT TEXT,
                $COL_CONV_CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        // Create actions table
        db.execSQL("""
            CREATE TABLE $TABLE_ACTIONS (
                $COL_ACTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ACTION_TIMESTAMP BIGINT NOT NULL,
                $COL_ACTION_TYPE TEXT NOT NULL,
                $COL_ACTION_DATA TEXT,
                $COL_ACTION_LOCATION TEXT,
                $COL_ACTION_SUCCESS INTEGER DEFAULT 1,
                $COL_ACTION_CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        // Create searches table
        db.execSQL("""
            CREATE TABLE $TABLE_SEARCHES (
                $COL_SEARCH_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SEARCH_TIMESTAMP BIGINT NOT NULL,
                $COL_SEARCH_TYPE TEXT NOT NULL,
                $COL_SEARCH_QUERY TEXT NOT NULL,
                $COL_SEARCH_RESULT TEXT,
                $COL_SEARCH_CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        // Create patterns table
        db.execSQL("""
            CREATE TABLE $TABLE_PATTERNS (
                $COL_PATTERN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PATTERN_TYPE TEXT NOT NULL,
                $COL_PATTERN_DATA TEXT NOT NULL,
                $COL_PATTERN_FREQUENCY INTEGER DEFAULT 1,
                $COL_PATTERN_LAST_OCCURRENCE BIGINT,
                $COL_PATTERN_CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        // Create preferences table
        db.execSQL("""
            CREATE TABLE $TABLE_PREFERENCES (
                $COL_PREF_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PREF_KEY TEXT UNIQUE NOT NULL,
                $COL_PREF_VALUE TEXT NOT NULL,
                $COL_PREF_CONFIDENCE REAL DEFAULT 0.5,
                $COL_PREF_UPDATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        // Create indexes for faster queries
        db.execSQL("CREATE INDEX idx_conv_timestamp ON $TABLE_CONVERSATIONS($COL_CONV_TIMESTAMP)")
        db.execSQL("CREATE INDEX idx_action_timestamp ON $TABLE_ACTIONS($COL_ACTION_TIMESTAMP)")
        db.execSQL("CREATE INDEX idx_search_timestamp ON $TABLE_SEARCHES($COL_SEARCH_TIMESTAMP)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONVERSATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEARCHES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PATTERNS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PREFERENCES")
        onCreate(db)
    }
    
    /**
     * Save conversation to database
     */
    fun saveConversation(
        userInput: String,
        zyraResponse: String,
        intentType: String,
        success: Boolean = true,
        context: String? = null
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CONV_TIMESTAMP, System.currentTimeMillis())
            put(COL_CONV_USER_INPUT, userInput)
            put(COL_CONV_ZYRA_RESPONSE, zyraResponse)
            put(COL_CONV_INTENT_TYPE, intentType)
            put(COL_CONV_SUCCESS, if (success) 1 else 0)
            put(COL_CONV_CONTEXT, context)
        }
        return db.insert(TABLE_CONVERSATIONS, null, values)
    }
    
    /**
     * Save action to database
     */
    fun saveAction(
        actionType: String,
        actionData: String?,
        location: String? = null,
        success: Boolean = true
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ACTION_TIMESTAMP, System.currentTimeMillis())
            put(COL_ACTION_TYPE, actionType)
            put(COL_ACTION_DATA, actionData)
            put(COL_ACTION_LOCATION, location)
            put(COL_ACTION_SUCCESS, if (success) 1 else 0)
        }
        return db.insert(TABLE_ACTIONS, null, values)
    }
    
    /**
     * Save search to database
     */
    fun saveSearch(
        searchType: String,
        query: String,
        result: String?
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SEARCH_TIMESTAMP, System.currentTimeMillis())
            put(COL_SEARCH_TYPE, searchType)
            put(COL_SEARCH_QUERY, query)
            put(COL_SEARCH_RESULT, result)
        }
        return db.insert(TABLE_SEARCHES, null, values)
    }
    
    /**
     * Get recent conversations
     */
    fun getRecentConversations(limit: Int = 100): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CONV_TIMESTAMP DESC",
            limit.toString()
        )
        
        cursor.use {
            while (it.moveToNext()) {
                conversations.add(
                    Conversation(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_CONV_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_CONV_TIMESTAMP)),
                        userInput = it.getString(it.getColumnIndexOrThrow(COL_CONV_USER_INPUT)),
                        zyraResponse = it.getString(it.getColumnIndexOrThrow(COL_CONV_ZYRA_RESPONSE)),
                        intentType = it.getString(it.getColumnIndexOrThrow(COL_CONV_INTENT_TYPE)),
                        success = it.getInt(it.getColumnIndexOrThrow(COL_CONV_SUCCESS)) == 1
                    )
                )
            }
        }
        
        return conversations
    }
    
    /**
     * Search conversation history
     */
    fun searchConversations(query: String): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATIONS,
            null,
            "$COL_CONV_USER_INPUT LIKE ? OR $COL_CONV_ZYRA_RESPONSE LIKE ?",
            arrayOf("%$query%", "%$query%"),
            null,
            null,
            "$COL_CONV_TIMESTAMP DESC",
            "50"
        )
        
        cursor.use {
            while (it.moveToNext()) {
                conversations.add(
                    Conversation(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_CONV_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_CONV_TIMESTAMP)),
                        userInput = it.getString(it.getColumnIndexOrThrow(COL_CONV_USER_INPUT)),
                        zyraResponse = it.getString(it.getColumnIndexOrThrow(COL_CONV_ZYRA_RESPONSE)),
                        intentType = it.getString(it.getColumnIndexOrThrow(COL_CONV_INTENT_TYPE)),
                        success = it.getInt(it.getColumnIndexOrThrow(COL_CONV_SUCCESS)) == 1
                    )
                )
            }
        }
        
        return conversations
    }
    
    /**
     * Clear all data (for privacy)
     */
    fun clearAllData() {
        val db = writableDatabase
        db.delete(TABLE_CONVERSATIONS, null, null)
        db.delete(TABLE_ACTIONS, null, null)
        db.delete(TABLE_SEARCHES, null, null)
        db.delete(TABLE_PATTERNS, null, null)
    }
    
    data class Conversation(
        val id: Int,
        val timestamp: Long,
        val userInput: String,
        val zyraResponse: String,
        val intentType: String,
        val success: Boolean
    )
}
