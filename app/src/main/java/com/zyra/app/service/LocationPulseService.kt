package com.zyra.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.zyra.app.R
import com.zyra.app.data.model.PulseData
import com.zyra.app.network.ZyraApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LocationPulseService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var apiService: ZyraApiService
    private val sessionId = UUID.randomUUID().toString()

    companion object {
        private const val CHANNEL_ID = "ZyraPulseChannel"
        private const val NOTIFICATION_ID = 999
        private const val BASE_URL = "http://192.168.1.5:8000" // TODO: Update with Real VPS IP/URL
        private const val API_KEY = "zyra-secret-key-123"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ZyraApiService::class.java)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    sendPulse(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ZyraPulse", "Permission not granted")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300000) // 5 minutes
            .setMinUpdateIntervalMillis(60000)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendPulse(location: Location) {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        val pulse = PulseData(
            latitude = location.latitude,
            longitude = location.longitude,
            batteryLevel = batteryLevel,
            timestamp = timestamp,
            activity = "Background",
            sessionId = sessionId
        )

        val call = apiService.sendPulse(API_KEY, pulse)
        call.enqueue(object : retrofit2.Callback<Any> {
            override fun onResponse(call: retrofit2.Call<Any>, response: retrofit2.Response<Any>) {
               Log.d("ZyraPulse", "Pulse sent: ${response.code()}")
            }

            override fun onFailure(call: retrofit2.Call<Any>, t: Throwable) {
                Log.e("ZyraPulse", "Pulse failed: ${t.message}")
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Zyra Pulse Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zyra is Active")
            .setContentText("Monitoring location for safety & proactivity")
            .setSmallIcon(R.drawable.ic_launcher) // Ensure this icon exists
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
