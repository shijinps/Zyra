package com.zyra.app.data.model

import com.google.gson.annotations.SerializedName

data class PulseData(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("activity") val activity: String?,
    @SerializedName("session_id") val sessionId: String?
)
