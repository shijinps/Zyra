package com.zyra.app.logic

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.DecimalFormat

/**
 * Device Diagnostics - JARVIS-style system monitoring
 * "Run diagnostics" like Iron Man's suit diagnostics
 */
object DeviceDiagnostics {
    
    /**
     * Run complete system diagnostics
     */
    fun runDiagnostics(context: Context): DiagnosticReport {
        return DiagnosticReport(
            battery = getBatteryInfo(context),
            storage = getStorageInfo(),
            memory = getMemoryInfo(context),
            system = getSystemInfo()
        )
    }
    
    /**
     * Get battery information
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        
        val status = when {
            level >= 80 -> BatteryStatus.EXCELLENT
            level >= 50 -> BatteryStatus.GOOD
            level >= 20 -> BatteryStatus.LOW
            else -> BatteryStatus.CRITICAL
        }
        
        return BatteryInfo(
            level = level,
            isCharging = isCharging,
            temperature = 0.0, // Not available via public API
            voltage = 0.0, // Not available via public API
            status = status
        )
    }
    
    /**
     * Get storage information
     */
    fun getStorageInfo(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val bytesTotal = stat.blockSizeLong * stat.blockCountLong
        val bytesUsed = bytesTotal - bytesAvailable
        
        val gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0)
        val gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0)
        val gbUsed = bytesUsed / (1024.0 * 1024.0 * 1024.0)
        val percentUsed = (bytesUsed.toDouble() / bytesTotal.toDouble() * 100).toInt()
        
        val status = when {
            percentUsed < 70 -> StorageStatus.HEALTHY
            percentUsed < 90 -> StorageStatus.WARNING
            else -> StorageStatus.CRITICAL
        }
        
        return StorageInfo(
            totalGB = gbTotal,
            usedGB = gbUsed,
            availableGB = gbAvailable,
            percentUsed = percentUsed,
            status = status
        )
    }
    
    /**
     * Get memory (RAM) information
     */
    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalMB = memInfo.totalMem / (1024.0 * 1024.0)
        val availableMB = memInfo.availMem / (1024.0 * 1024.0)
        val usedMB = totalMB - availableMB
        val percentUsed = (usedMB / totalMB * 100).toInt()
        
        val status = when {
            percentUsed < 70 -> MemoryStatus.OPTIMAL
            percentUsed < 85 -> MemoryStatus.MODERATE
            else -> MemoryStatus.HIGH
        }
        
        return MemoryInfo(
            totalMB = totalMB,
            usedMB = usedMB,
            availableMB = availableMB,
            percentUsed = percentUsed,
            status = status,
            isLowMemory = memInfo.lowMemory
        )
    }
    
    /**
     * Get system information
     */
    fun getSystemInfo(): SystemInfo {
        return SystemInfo(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            buildNumber = Build.DISPLAY
        )
    }
    
    /**
     * Format diagnostics for speech (JARVIS-style)
     */
    fun formatDiagnosticsForSpeech(report: DiagnosticReport): String {
        val battery = report.battery
        val storage = report.storage
        val memory = report.memory
        
        val batteryText = when {
            battery.isCharging -> "Battery is at ${battery.level}% and charging"
            battery.status == BatteryStatus.CRITICAL -> "Warning: Battery critically low at ${battery.level}%"
            battery.status == BatteryStatus.LOW -> "Battery is low at ${battery.level}%"
            else -> "Battery is at ${battery.level}%"
        }
        
        val storageText = when (storage.status) {
            StorageStatus.CRITICAL -> "Warning: Storage is ${storage.percentUsed}% full. Only ${formatGB(storage.availableGB)} remaining"
            StorageStatus.WARNING -> "Storage is ${storage.percentUsed}% full"
            else -> "Storage is healthy with ${formatGB(storage.availableGB)} available"
        }
        
        val memoryText = when (memory.status) {
            MemoryStatus.HIGH -> "Memory usage is high at ${memory.percentUsed}%"
            MemoryStatus.MODERATE -> "Memory usage is moderate at ${memory.percentUsed}%"
            else -> "Memory is optimal"
        }
        
        return "Diagnostics complete. $batteryText. $storageText. $memoryText. All systems operational."
    }
    
    private fun formatGB(gb: Double): String {
        val df = DecimalFormat("#.#")
        return "${df.format(gb)} GB"
    }
    
    data class DiagnosticReport(
        val battery: BatteryInfo,
        val storage: StorageInfo,
        val memory: MemoryInfo,
        val system: SystemInfo
    )
    
    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val temperature: Double,
        val voltage: Double,
        val status: BatteryStatus
    )
    
    data class StorageInfo(
        val totalGB: Double,
        val usedGB: Double,
        val availableGB: Double,
        val percentUsed: Int,
        val status: StorageStatus
    )
    
    data class MemoryInfo(
        val totalMB: Double,
        val usedMB: Double,
        val availableMB: Double,
        val percentUsed: Int,
        val status: MemoryStatus,
        val isLowMemory: Boolean
    )
    
    data class SystemInfo(
        val deviceModel: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val buildNumber: String
    )
    
    enum class BatteryStatus {
        EXCELLENT, GOOD, LOW, CRITICAL
    }
    
    enum class StorageStatus {
        HEALTHY, WARNING, CRITICAL
    }
    
    enum class MemoryStatus {
        OPTIMAL, MODERATE, HIGH
    }
}
