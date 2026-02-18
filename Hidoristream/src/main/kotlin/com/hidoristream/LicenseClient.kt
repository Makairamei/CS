package com.hidoristream

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import android.util.Log

object LicenseClient {
    private const val TAG = "LicenseClient"
    private const val SERVER_URL = "http://192.168.18.5:3000"
    
    // Cache: store validation results to reduce network calls
    private var cachedStatus: String? = null
    private var cacheExpiry: Long = 0L
    private val actionThrottle = mutableMapOf<String, Long>()
    
    // Block access when license is invalid
    private var licenseBlocked = false
    private var blockMessage = ""

    /**
     * Core license check. Returns true if license is valid.
     * Throws RuntimeException if license is invalid (blocks plugin usage).
     * 
     * Actions tracked: HOME, OPEN, SEARCH, LOAD, PLAY, SWITCH, DOWNLOAD
     */
    suspend fun checkLicense(
        pluginName: String,
        action: String = "OPEN",
        data: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        
        // Throttle: HOME checks max once per 60s, other actions max once per 5s
        val throttleKey = "$pluginName|$action"
        val throttleMs = when (action.uppercase()) {
            "HOME" -> 60_000L
            "SEARCH" -> 10_000L
            else -> 5_000L
        }
        
        val lastCheck = actionThrottle[throttleKey] ?: 0L
        if (now - lastCheck < throttleMs && cachedStatus == "active") {
            return true
        }
        actionThrottle[throttleKey] = now
        
        // Use cache if still valid (5 minute cache)
        if (cachedStatus == "active" && now < cacheExpiry && action.uppercase() != "PLAY") {
            // Still log the action server-side for tracking
            logActionAsync(pluginName, action, data)
            return true
        }
        
        return try {
            val encodedData = java.net.URLEncoder.encode(data ?: "", "UTF-8")
            val encodedPlugin = java.net.URLEncoder.encode(pluginName, "UTF-8")
            val url = "$SERVER_URL/api/check-ip?plugin=$encodedPlugin&action=$action&data=$encodedData"
            
            val response = app.get(url).text
            val json = tryParseJson<CheckResponse>(response)
            
            if (json?.status == "active") {
                cachedStatus = "active"
                cacheExpiry = now + 300_000L  // Cache for 5 minutes
                licenseBlocked = false
                blockMessage = ""
                true
            } else {
                cachedStatus = "error"
                licenseBlocked = true
                blockMessage = json?.message ?: "License tidak valid"
                Log.w(TAG, "License check failed for $pluginName: $blockMessage")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "License check network error: ${e.message}")
            // If we have a cached valid status, allow access temporarily
            if (cachedStatus == "active" && now < cacheExpiry + 600_000L) {
                true
            } else {
                // No valid cache, block access
                licenseBlocked = true
                blockMessage = "Tidak dapat memverifikasi lisensi. Periksa koneksi internet."
                false
            }
        }
    }

    /**
     * Enforced license check - throws exception if invalid.
     * Use this for critical operations that MUST be gated.
     */
    suspend fun requireLicense(
        pluginName: String,
        action: String = "OPEN",
        data: String? = null
    ) {
        if (!checkLicense(pluginName, action, data)) {
            throw RuntimeException("[PREMIUM] $blockMessage")
        }
    }

    /**
     * Check + track video playback
     */
    suspend fun checkPlay(pluginName: String, title: String): Boolean {
        return checkLicense(pluginName, "PLAY", title)
    }

    /**
     * Enforce license for video playback - throws if invalid
     */
    suspend fun requirePlay(pluginName: String, title: String) {
        requireLicense(pluginName, "PLAY", title)
    }

    /**
     * Track download action
     */
    suspend fun trackDownload(pluginName: String, title: String): Boolean {
        return checkLicense(pluginName, "DOWNLOAD", title)
    }

    /**
     * Track plugin switch (user navigating between plugins)
     */
    suspend fun trackSwitch(pluginName: String): Boolean {
        return checkLicense(pluginName, "SWITCH")
    }

    /**
     * Check if license is currently blocked
     */
    fun isBlocked(): Boolean = licenseBlocked

    /**
     * Get the current block message
     */
    fun getBlockMessage(): String = blockMessage
    
    /**
     * Fire-and-forget action logging (doesn't block on response)
     */
    private fun logActionAsync(pluginName: String, action: String, data: String?) {
        try {
            val encodedData = java.net.URLEncoder.encode(data ?: "", "UTF-8")
            val encodedPlugin = java.net.URLEncoder.encode(pluginName, "UTF-8")
            // Use a lightweight fire-and-forget approach
            Thread {
                try {
                    val url = "$SERVER_URL/api/check-ip?plugin=$encodedPlugin&action=$action&data=$encodedData"
                    java.net.URL(url).readText()
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }

    /**
     * Reset cache (useful when user re-validates license)
     */
    fun resetCache() {
        cachedStatus = null
        cacheExpiry = 0L
        licenseBlocked = false
        blockMessage = ""
        actionThrottle.clear()
    }

    data class CheckResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String,
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = ""
    )
}