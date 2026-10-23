package com.zyra.app.network

import com.zyra.app.data.model.PulseData
import com.zyra.app.data.Contact
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface ZyraApiService {
    
    data class TokenResponse(val token: String)

    @GET("/livekit/token")
    fun getLiveKitToken(
        @Header("X-Zyra-Token") token: String,
        @Query("dispatch") dispatch: Boolean = true
    ): Call<TokenResponse>
    
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
}
