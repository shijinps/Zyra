package com.zyra.app.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Location-Based Intelligence
 * Provides context-aware suggestions based on user location
 */
object LocationIntelligence {
    
    private const val LOCATION_FILE = "zyra_locations.json"
    private const val PLACE_RADIUS_METERS = 100
    
    data class LatLng(val latitude: Double, val longitude: Double)
    
    data class KnownPlace(
        val name: String,
        val location: LatLng,
        val radius: Int = PLACE_RADIUS_METERS,
        val suggestions: MutableList<String> = mutableListOf(),
        var visitCount: Int = 0,
        var lastVisit: Long = 0
    )
    
    private val knownPlaces = mutableMapOf<String, KnownPlace>()
    private var currentLocation: LatLng? = null
    private var currentPlace: String? = null
    
    /**
     * Initialize location intelligence
     */
    fun init(context: Context) {
        load(context)
    }
    
    /**
     * Learn a new place
     */
    fun learnPlace(name: String, lat: Double, lng: Double, radius: Int = PLACE_RADIUS_METERS) {
        knownPlaces[name.lowercase()] = KnownPlace(
            name = name,
            location = LatLng(lat, lng),
            radius = radius
        )
    }
    
    /**
     * Add suggestion for a place
     */
    fun addSuggestionForPlace(placeName: String, suggestion: String) {
        val place = knownPlaces[placeName.lowercase()]
        if (place != null && !place.suggestions.contains(suggestion)) {
            place.suggestions.add(suggestion)
        }
    }
    
    /**
     * Update current location
     */
    fun updateLocation(lat: Double, lng: Double) {
        currentLocation = LatLng(lat, lng)
        currentPlace = identifyLocation(lat, lng)
        
        // Update visit count
        currentPlace?.let { placeName ->
            knownPlaces[placeName.lowercase()]?.let { place ->
                place.visitCount++
                place.lastVisit = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Identify location based on coordinates
     */
    fun identifyLocation(lat: Double, lng: Double): String? {
        return knownPlaces.entries.firstOrNull { (_, place) ->
            calculateDistance(lat, lng, place.location.latitude, place.location.longitude) < place.radius
        }?.key
    }
    
    /**
     * Get current place name
     */
    fun getCurrentPlace(): String? {
        return currentPlace
    }
    
    /**
     * Get contextual suggestions for current location
     */
    fun getContextualSuggestions(): List<String> {
        val place = currentPlace?.let { knownPlaces[it.lowercase()] } ?: return emptyList()
        return place.suggestions
    }
    
    /**
     * Get contextual suggestions for specific location
     */
    fun getSuggestionsForPlace(placeName: String): List<String> {
        return knownPlaces[placeName.lowercase()]?.suggestions ?: emptyList()
    }
    
    /**
     * Check if user is at a specific place
     */
    fun isAtPlace(placeName: String): Boolean {
        return currentPlace?.equals(placeName, ignoreCase = true) == true
    }
    
    /**
     * Calculate distance between two coordinates (in meters)
     */
    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Get distance to a known place
     */
    fun getDistanceToPlace(placeName: String): Double? {
        val place = knownPlaces[placeName.lowercase()] ?: return null
        val current = currentLocation ?: return null
        
        return calculateDistance(
            current.latitude, current.longitude,
            place.location.latitude, place.location.longitude
        )
    }
    
    /**
     * Get nearby places
     */
    fun getNearbyPlaces(radiusMeters: Int = 1000): List<Pair<String, Double>> {
        val current = currentLocation ?: return emptyList()
        
        return knownPlaces.map { (name, place) ->
            val distance = calculateDistance(
                current.latitude, current.longitude,
                place.location.latitude, place.location.longitude
            )
            name to distance
        }.filter { it.second <= radiusMeters }
            .sortedBy { it.second }
    }
    
    /**
     * Get most visited places
     */
    fun getMostVisitedPlaces(limit: Int = 5): List<KnownPlace> {
        return knownPlaces.values
            .sortedByDescending { it.visitCount }
            .take(limit)
    }
    
    /**
     * Check if user is leaving a place (geofencing)
     */
    fun isLeavingPlace(placeName: String, newLat: Double, newLng: Double): Boolean {
        val place = knownPlaces[placeName.lowercase()] ?: return false
        val wasInside = currentPlace?.equals(placeName, ignoreCase = true) == true
        val distance = calculateDistance(newLat, newLng, place.location.latitude, place.location.longitude)
        val isNowOutside = distance > place.radius
        
        return wasInside && isNowOutside
    }
    
    /**
     * Check if user is entering a place (geofencing)
     */
    fun isEnteringPlace(placeName: String, newLat: Double, newLng: Double): Boolean {
        val place = knownPlaces[placeName.lowercase()] ?: return false
        val wasOutside = currentPlace?.equals(placeName, ignoreCase = true) != true
        val distance = calculateDistance(newLat, newLng, place.location.latitude, place.location.longitude)
        val isNowInside = distance <= place.radius
        
        return wasOutside && isNowInside
    }
    
    /**
     * Save locations
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val placesArray = JSONArray()
            
            knownPlaces.values.forEach { place ->
                val obj = JSONObject().apply {
                    put("name", place.name)
                    put("latitude", place.location.latitude)
                    put("longitude", place.location.longitude)
                    put("radius", place.radius)
                    put("visitCount", place.visitCount)
                    put("lastVisit", place.lastVisit)
                    
                    val suggestionsArray = JSONArray()
                    place.suggestions.forEach { suggestionsArray.put(it) }
                    put("suggestions", suggestionsArray)
                }
                placesArray.put(obj)
            }
            
            json.put("places", placesArray)
            
            currentLocation?.let {
                json.put("lastLatitude", it.latitude)
                json.put("lastLongitude", it.longitude)
            }
            
            currentPlace?.let {
                json.put("currentPlace", it)
            }
            
            val file = File(context.filesDir, LOCATION_FILE)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load locations
     */
    private fun load(context: Context) {
        try {
            val file = File(context.filesDir, LOCATION_FILE)
            if (!file.exists()) {
                // Initialize with default places
                initializeDefaultPlaces()
                return
            }
            
            val json = JSONObject(file.readText())
            val placesArray = json.optJSONArray("places") ?: return
            
            knownPlaces.clear()
            
            for (i in 0 until placesArray.length()) {
                val obj = placesArray.getJSONObject(i)
                
                val suggestions = mutableListOf<String>()
                val suggestionsArray = obj.optJSONArray("suggestions")
                if (suggestionsArray != null) {
                    for (j in 0 until suggestionsArray.length()) {
                        suggestions.add(suggestionsArray.getString(j))
                    }
                }
                
                val place = KnownPlace(
                    name = obj.getString("name"),
                    location = LatLng(
                        obj.getDouble("latitude"),
                        obj.getDouble("longitude")
                    ),
                    radius = obj.getInt("radius"),
                    suggestions = suggestions,
                    visitCount = obj.getInt("visitCount"),
                    lastVisit = obj.getLong("lastVisit")
                )
                
                knownPlaces[place.name.lowercase()] = place
            }
            
            if (json.has("lastLatitude") && json.has("lastLongitude")) {
                currentLocation = LatLng(
                    json.getDouble("lastLatitude"),
                    json.getDouble("lastLongitude")
                )
            }
            
            currentPlace = json.optString("currentPlace", null)
            
        } catch (e: Exception) {
            e.printStackTrace()
            initializeDefaultPlaces()
        }
    }
    
    /**
     * Initialize with common default places
     */
    private fun initializeDefaultPlaces() {
        // These would be learned over time, but we can provide templates
        addSuggestionForPlace("Home", "Turn on WiFi?")
        addSuggestionForPlace("Home", "Check calendar for tomorrow?")
        
        addSuggestionForPlace("Office", "Set phone to silent?")
        addSuggestionForPlace("Office", "Check emails?")
        
        addSuggestionForPlace("Gym", "Start workout playlist?")
        addSuggestionForPlace("Gym", "Track workout?")
    }
}
