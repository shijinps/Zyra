package com.zyra.app.logic

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Spatial Intelligence Module
 * Provides map-level intelligence for route-aware searches and location-based decisions
 */
object SpatialIntelligence {
    
    // API key loaded from BuildConfig (configured in build.gradle.kts)
    private val GOOGLE_MAPS_API_KEY = try {
        com.zyra.app.BuildConfig.GOOGLE_MAPS_API_KEY
    } catch (e: Exception) {
        "YOUR_GOOGLE_MAPS_API_KEY" // Fallback - replace in local.properties
    }
    
    private const val SPATIAL_MEMORY_FILE = "zyra_spatial_memory.json"
    
    data class RoutePoint(
        val lat: Double,
        val lng: Double,
        val name: String? = null
    )
    
    data class PlaceResult(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val detourTime: Int, // in seconds
        val detourDistance: Int, // in meters
        val placeType: String,
        val rating: Double? = null,
        val isOpen: Boolean? = null
    )
    
    data class RouteInfo(
        val origin: RoutePoint,
        val destination: RoutePoint,
        val distance: Int, // meters
        val duration: Int, // seconds
        val polyline: String,
        val waypoints: List<RoutePoint> = emptyList()
    )
    
    private var currentRoute: RouteInfo? = null
    private var currentLocation: RoutePoint? = null
    private val spatialMemory = mutableMapOf<String, MutableList<PlaceResult>>()
    
    /**
     * Update current location
     */
    fun updateCurrentLocation(lat: Double, lng: Double) {
        currentLocation = RoutePoint(lat, lng)
    }
    
    /**
     * Calculate route from current location to destination
     */
    suspend fun calculateRoute(
        destinationLat: Double,
        destinationLng: Double,
        destinationName: String = "Destination"
    ): RouteInfo? {
        val origin = currentLocation ?: return null
        
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.lat},${origin.lng}" +
                        "&destination=$destinationLat,$destinationLng" +
                        "&mode=driving" +
                        "&key=$GOOGLE_MAPS_API_KEY"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                if (json.getString("status") == "OK") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val leg = route.getJSONArray("legs").getJSONObject(0)
                        
                        val routeInfo = RouteInfo(
                            origin = origin,
                            destination = RoutePoint(destinationLat, destinationLng, destinationName),
                            distance = leg.getJSONObject("distance").getInt("value"),
                            duration = leg.getJSONObject("duration").getInt("value"),
                            polyline = route.getJSONObject("overview_polyline").getString("points")
                        )
                        
                        currentRoute = routeInfo
                        routeInfo
                    } else null
                } else null
                
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Find places along current route
     */
    suspend fun findPlacesAlongRoute(
        placeType: String, // "atm", "bank", "gas_station", "restaurant"
        maxDetourMinutes: Int = 2,
        keyword: String? = null
    ): List<PlaceResult> {
        val route = currentRoute ?: return emptyList()
        val origin = currentLocation ?: return emptyList()
        
        return withContext(Dispatchers.IO) {
            try {
                // Decode polyline to get route points
                val routePoints = decodePolyline(route.polyline)
                
                // Search for places near route points
                val allPlaces = mutableListOf<PlaceResult>()
                
                // Sample route points (every 2km or so)
                val sampledPoints = sampleRoutePoints(routePoints, 2000)
                
                sampledPoints.forEach { point ->
                    val places = searchNearbyPlaces(point.lat, point.lng, placeType, keyword)
                    allPlaces.addAll(places)
                }
                
                // Remove duplicates and calculate detours
                val uniquePlaces = allPlaces.distinctBy { "${it.lat},${it.lng}" }
                
                // Calculate detour for each place
                val placesWithDetours = uniquePlaces.mapNotNull { place ->
                    val detour = calculateDetourTime(origin, place, route.destination)
                    if (detour != null && detour.first <= maxDetourMinutes * 60) {
                        place.copy(
                            detourTime = detour.first,
                            detourDistance = detour.second
                        )
                    } else null
                }
                
                // Sort by detour time
                placesWithDetours.sortedBy { it.detourTime }
                
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Search nearby places using Google Places API
     */
    private suspend fun searchNearbyPlaces(
        lat: Double,
        lng: Double,
        placeType: String,
        keyword: String?
    ): List<PlaceResult> {
        return withContext(Dispatchers.IO) {
            try {
                var url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                        "location=$lat,$lng" +
                        "&radius=1000" +
                        "&type=$placeType" +
                        "&key=$GOOGLE_MAPS_API_KEY"
                
                if (keyword != null) {
                    url += "&keyword=$keyword"
                }
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                val results = mutableListOf<PlaceResult>()
                
                if (json.getString("status") == "OK") {
                    val places = json.getJSONArray("results")
                    
                    for (i in 0 until places.length()) {
                        val place = places.getJSONObject(i)
                        val location = place.getJSONObject("geometry").getJSONObject("location")
                        
                        results.add(
                            PlaceResult(
                                name = place.getString("name"),
                                address = place.optString("vicinity", ""),
                                lat = location.getDouble("lat"),
                                lng = location.getDouble("lng"),
                                detourTime = 0,
                                detourDistance = 0,
                                placeType = placeType,
                                rating = place.optDouble("rating", 0.0),
                                isOpen = place.optJSONObject("opening_hours")?.optBoolean("open_now")
                            )
                        )
                    }
                }
                
                results
                
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Calculate detour time and distance
     */
    private suspend fun calculateDetourTime(
        origin: RoutePoint,
        waypoint: PlaceResult,
        destination: RoutePoint
    ): Pair<Int, Int>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=${origin.lat},${origin.lng}" +
                        "&destination=${destination.lat},${destination.lng}" +
                        "&waypoints=${waypoint.lat},${waypoint.lng}" +
                        "&mode=driving" +
                        "&key=$GOOGLE_MAPS_API_KEY"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                if (json.getString("status") == "OK") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        
                        var totalDuration = 0
                        var totalDistance = 0
                        
                        for (i in 0 until legs.length()) {
                            val leg = legs.getJSONObject(i)
                            totalDuration += leg.getJSONObject("duration").getInt("value")
                            totalDistance += leg.getJSONObject("distance").getInt("value")
                        }
                        
                        // Calculate detour (difference from direct route)
                        val directRoute = currentRoute
                        if (directRoute != null) {
                            val detourTime = totalDuration - directRoute.duration
                            val detourDistance = totalDistance - directRoute.distance
                            Pair(detourTime, detourDistance)
                        } else {
                            Pair(totalDuration, totalDistance)
                        }
                    } else null
                } else null
                
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Decode Google Maps polyline
     */
    private fun decodePolyline(encoded: String): List<RoutePoint> {
        val poly = mutableListOf<RoutePoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            poly.add(RoutePoint(lat / 1E5, lng / 1E5))
        }
        
        return poly
    }
    
    /**
     * Sample route points at regular intervals
     */
    private fun sampleRoutePoints(points: List<RoutePoint>, intervalMeters: Int): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()
        
        val sampled = mutableListOf(points.first())
        var accumulatedDistance = 0.0
        
        for (i in 1 until points.size) {
            val distance = calculateDistance(points[i - 1], points[i])
            accumulatedDistance += distance
            
            if (accumulatedDistance >= intervalMeters) {
                sampled.add(points[i])
                accumulatedDistance = 0.0
            }
        }
        
        return sampled
    }
    
    /**
     * Calculate distance between two points (Haversine formula)
     */
    private fun calculateDistance(p1: RoutePoint, p2: RoutePoint): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(p2.lat - p1.lat)
        val dLng = Math.toRadians(p2.lng - p1.lng)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.lat)) * cos(Math.toRadians(p2.lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }
    
    /**
     * Generate voice-friendly summary of places
     */
    fun generatePlacesSummary(places: List<PlaceResult>): String {
        if (places.isEmpty()) {
            return "I couldn't find any places matching your criteria along your route."
        }
        
        val top3 = places.take(3)
        val summary = StringBuilder()
        
        summary.append("I found ${places.size} options. ")
        
        top3.forEachIndexed { index, place ->
            val detourMinutes = place.detourTime / 60
            val detourSeconds = place.detourTime % 60
            
            summary.append("${index + 1}. ${place.name} in ${place.address}. ")
            
            if (detourMinutes > 0) {
                summary.append("It's a $detourMinutes minute detour. ")
            } else {
                summary.append("It's a $detourSeconds second detour. ")
            }
            
            place.rating?.let {
                if (it > 0) summary.append("Rated ${String.format("%.1f", it)} stars. ")
            }
            
            place.isOpen?.let {
                summary.append(if (it) "Currently open. " else "Currently closed. ")
            }
        }
        
        return summary.toString()
    }
    
    /**
     * Save spatial memory
     */
    fun save(context: Context) {
        try {
            val json = JSONObject()
            val memoryArray = JSONArray()
            
            spatialMemory.forEach { (key, places) ->
                val placesArray = JSONArray()
                places.forEach { place ->
                    placesArray.put(JSONObject().apply {
                        put("name", place.name)
                        put("address", place.address)
                        put("lat", place.lat)
                        put("lng", place.lng)
                        put("type", place.placeType)
                    })
                }
                
                memoryArray.put(JSONObject().apply {
                    put("key", key)
                    put("places", placesArray)
                })
            }
            
            json.put("memory", memoryArray)
            
            val file = File(context.filesDir, SPATIAL_MEMORY_FILE)
            file.writeText(json.toString())
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load spatial memory
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, SPATIAL_MEMORY_FILE)
            if (!file.exists()) return
            
            val json = JSONObject(file.readText())
            val memoryArray = json.getJSONArray("memory")
            
            for (i in 0 until memoryArray.length()) {
                val entry = memoryArray.getJSONObject(i)
                val key = entry.getString("key")
                val placesArray = entry.getJSONArray("places")
                
                val places = mutableListOf<PlaceResult>()
                for (j in 0 until placesArray.length()) {
                    val placeJson = placesArray.getJSONObject(j)
                    places.add(
                        PlaceResult(
                            name = placeJson.getString("name"),
                            address = placeJson.getString("address"),
                            lat = placeJson.getDouble("lat"),
                            lng = placeJson.getDouble("lng"),
                            detourTime = 0,
                            detourDistance = 0,
                            placeType = placeJson.getString("type")
                        )
                    )
                }
                
                spatialMemory[key] = places
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
