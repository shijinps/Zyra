package com.zyra.app.data

import android.content.Context
import com.zyra.app.logic.algorithms.Levenshtein
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Smart Contact Manager with relationship learning and intelligent disambiguation
 */
object SmartContactManager {
    
    private const val SMART_CONTACTS_FILE = "zyra_smart_contacts.json"
    
    data class SmartContact(
        val contact: Contact,
        val relationships: MutableList<String> = mutableListOf(), // "mom", "boss", "friend"
        var callFrequency: Int = 0,
        var messageFrequency: Int = 0,
        var lastCallTime: Long = 0,
        var lastMessageTime: Long = 0,
        val tags: MutableList<String> = mutableListOf(), // "work", "family", "emergency"
        var isFavorite: Boolean = false
    )
    
    private val smartContacts = mutableMapOf<String, SmartContact>()
    private lateinit var contactRepository: ContactRepository
    
    /**
     * Initialize smart contact manager
     */
    fun init(context: Context, repository: ContactRepository) {
        contactRepository = repository
        load(context)
        
        // Sync with contact repository
        syncWithRepository()
    }
    
    /**
     * Sync smart contacts with contact repository
     */
    private fun syncWithRepository() {
        // This would be called asynchronously
        // For now, we'll just ensure all contacts exist in smart contacts
    }
    
    /**
     * Learn relationship between user and contact
     */
    fun learnRelationship(contactName: String, relationship: String) {
        val smart = smartContacts[contactName.lowercase()]
        if (smart != null) {
            val rel = relationship.lowercase().trim()
            if (!smart.relationships.contains(rel)) {
                smart.relationships.add(rel)
            }
        } else {
            // Try to find contact by fuzzy match
            val contact = fuzzyMatchContact(contactName)
            if (contact != null) {
                val smart = SmartContact(contact)
                smart.relationships.add(relationship.lowercase().trim())
                smartContacts[contact.name.lowercase()] = smart
            }
        }
    }
    
    /**
     * Record call to contact
     */
    fun recordCall(contactName: String) {
        val smart = getOrCreateSmartContact(contactName)
        smart?.let {
            it.callFrequency++
            it.lastCallTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Record message to contact
     */
    fun recordMessage(contactName: String) {
        val smart = getOrCreateSmartContact(contactName)
        smart?.let {
            it.messageFrequency++
            it.lastMessageTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Add tag to contact
     */
    fun addTag(contactName: String, tag: String) {
        val smart = getOrCreateSmartContact(contactName)
        smart?.let {
            val normalizedTag = tag.lowercase().trim()
            if (!it.tags.contains(normalizedTag)) {
                it.tags.add(normalizedTag)
            }
        }
    }
    
    /**
     * Mark contact as favorite
     */
    fun markFavorite(contactName: String, favorite: Boolean = true) {
        val smart = getOrCreateSmartContact(contactName)
        smart?.isFavorite = favorite
    }
    
    /**
     * Resolve contact from query (handles relationships, fuzzy matching, etc.)
     */
    fun resolveContact(query: String): Contact? {
        val lowerQuery = query.lowercase().trim()
        
        // 1. Try exact relationship match ("my mom", "my boss")
        val relationship = lowerQuery.replace("my ", "").replace("the ", "")
        val byRelationship = smartContacts.values.firstOrNull {
            it.relationships.contains(relationship)
        }
        if (byRelationship != null) return byRelationship.contact
        
        // 2. Try exact name match
        val exactMatch = smartContacts[lowerQuery]
        if (exactMatch != null) return exactMatch.contact
        
        // 3. Try tag match ("call work", "message family")
        val byTag = smartContacts.values.filter {
            it.tags.any { tag -> lowerQuery.contains(tag) }
        }
        if (byTag.size == 1) return byTag[0].contact
        
        // 4. Fuzzy name matching
        return fuzzyMatchContact(query)
    }
    
    /**
     * Disambiguate when multiple contacts match
     */
    fun disambiguate(query: String): List<Contact> {
        val lowerQuery = query.lowercase().trim()
        
        // Find all potential matches
        val matches = smartContacts.values.filter { smart ->
            smart.contact.name.lowercase().contains(lowerQuery) ||
            lowerQuery.contains(smart.contact.name.lowercase()) ||
            com.zyra.app.logic.algorithms.Levenshtein.calculate(smart.contact.name.lowercase(), lowerQuery) <= 3
        }
        
        // Sort by relevance (favorites first, then by frequency)
        return matches
            .sortedWith(compareByDescending<SmartContact> { it.isFavorite }
                .thenByDescending { it.callFrequency + it.messageFrequency })
            .take(3)
            .map { it.contact }
    }
    
    /**
     * Get favorite contacts
     */
    fun getFavorites(): List<SmartContact> {
        return smartContacts.values
            .filter { it.isFavorite }
            .sortedByDescending { it.callFrequency + it.messageFrequency }
    }
    
    /**
     * Get frequently contacted
     */
    fun getFrequentContacts(limit: Int = 5): List<SmartContact> {
        return smartContacts.values
            .sortedByDescending { it.callFrequency + it.messageFrequency }
            .take(limit)
    }
    
    /**
     * Get contacts by tag
     */
    fun getContactsByTag(tag: String): List<SmartContact> {
        return smartContacts.values
            .filter { it.tags.contains(tag.lowercase()) }
    }
    
    /**
     * Get or create smart contact
     */
    private fun getOrCreateSmartContact(contactName: String): SmartContact? {
        val lowerName = contactName.lowercase()
        
        return smartContacts[lowerName] ?: run {
            val contact = fuzzyMatchContact(contactName) ?: return null
            val smart = SmartContact(contact)
            smartContacts[contact.name.lowercase()] = smart
            smart
        }
    }
    
    /**
     * Fuzzy match contact name
     */
    private fun fuzzyMatchContact(query: String): Contact? {
        // This would use the existing ContactRepository
        // For now, return null - will be integrated with actual repository
        return null
    }
    
    /**
     * Save smart contacts
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val contactsArray = JSONArray()
            
            smartContacts.values.forEach { smart ->
                val obj = JSONObject().apply {
                    put("name", smart.contact.name)
                    put("phoneNumber", smart.contact.phoneNumber)
                    put("callFrequency", smart.callFrequency)
                    put("messageFrequency", smart.messageFrequency)
                    put("lastCallTime", smart.lastCallTime)
                    put("lastMessageTime", smart.lastMessageTime)
                    put("isFavorite", smart.isFavorite)
                    
                    val relArray = JSONArray()
                    smart.relationships.forEach { relArray.put(it) }
                    put("relationships", relArray)
                    
                    val tagArray = JSONArray()
                    smart.tags.forEach { tagArray.put(it) }
                    put("tags", tagArray)
                }
                contactsArray.put(obj)
            }
            
            json.put("contacts", contactsArray)
            
            val file = File(context.filesDir, SMART_CONTACTS_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load smart contacts
     */
    private fun load(context: Context) {
        try {
            val file = File(context.filesDir, SMART_CONTACTS_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val contactsArray = json.optJSONArray("contacts") ?: return
            
            smartContacts.clear()
            
            for (i in 0 until contactsArray.length()) {
                val obj = contactsArray.getJSONObject(i)
                
                val relationships = mutableListOf<String>()
                val relArray = obj.optJSONArray("relationships")
                if (relArray != null) {
                    for (j in 0 until relArray.length()) {
                        relationships.add(relArray.getString(j))
                    }
                }
                
                val tags = mutableListOf<String>()
                val tagArray = obj.optJSONArray("tags")
                if (tagArray != null) {
                    for (j in 0 until tagArray.length()) {
                        tags.add(tagArray.getString(j))
                    }
                }
                
                val contact = Contact(
                    id = obj.optString("id", obj.getString("phoneNumber")), // Use phone as fallback ID
                    name = obj.getString("name"),
                    phoneNumber = obj.getString("phoneNumber")
                )
                
                val smart = SmartContact(
                    contact = contact,
                    relationships = relationships,
                    callFrequency = obj.getInt("callFrequency"),
                    messageFrequency = obj.getInt("messageFrequency"),
                    lastCallTime = obj.getLong("lastCallTime"),
                    lastMessageTime = obj.getLong("lastMessageTime"),
                    tags = tags,
                    isFavorite = obj.getBoolean("isFavorite")
                )
                
                smartContacts[contact.name.lowercase()] = smart
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
