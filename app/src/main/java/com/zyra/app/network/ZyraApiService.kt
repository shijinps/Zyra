package com.zyra.app.network

import com.zyra.app.data.model.PulseData
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ZyraApiService {
    @POST("/pulse")
    fun sendPulse(
        @Header("X-Zyra-Token") token: String,
        @Body data: PulseData
    ): Call<Any>
    
    // Future: generic execute endpoint
    // @POST("/tool/execute")
    // fun executeTool(...)
}
