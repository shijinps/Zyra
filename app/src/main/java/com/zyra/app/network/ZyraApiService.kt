package com.zyra.app.network

import com.zyra.app.data.model.PulseData
import com.zyra.app.data.model.QuantumMoodResponse
import com.zyra.app.data.Contact
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ZyraApiService {
    
    data class SyncContactsRequest(val contacts: List<Contact>)
    
    @POST("/sync/contacts")
    fun syncContacts(
        @Header("X-Zyra-Token") token: String,
        @Body data: SyncContactsRequest
    ): Call<Any>

    @POST("/pulse")
    fun sendPulse(
        @Header("X-Zyra-Token") token: String,
        @Body data: PulseData
    ): Call<Any>

    @retrofit2.http.GET("/quantum/mood")
    fun getQuantumMood(
        @Header("X-Zyra-Token") token: String
    ): Call<QuantumMoodResponse>

    @POST("/quantum/decision")
    fun getQuantumDecision(
        @Header("X-Zyra-Token") token: String
    ): Call<Any>
}
