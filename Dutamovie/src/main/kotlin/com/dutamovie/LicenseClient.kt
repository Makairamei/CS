package com.dutamovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URLEncoder

object LicenseClient {
    // Hardcoded as requested
    private const val SERVER_URL = "http://172.83.15.6:3000"
    
    private var cachedStatus: String? = null
    private var cacheTime: Long = 0
    private const val CACHE_MS = 60 * 1000L // 1 minute cache

    data class LicenseResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = ""
    )

    suspend fun checkLicense(pluginName: String, action: String = "OPEN", data: String = ""): Boolean {
        // Cache Check (Optimized: only skip check if status is active AND action is routine/OPEN)
        // If action is SEARCH or LOAD, we might want to log it even if cached?
        // User wants REALTIME detection. So we should HIT the server for SEARCH/LOAD events.
        // But we can still cache the *validity* result to avoid blocking if server is slow?
        // No, server logs on hit. So we must hit server.
        // Let's only cache "checking" overhead? 
        // Strategy: Always hit server for tracking? 
        // If we hit server every time, it might vary.
        // User said "realtime deteksi ... misal dari mereka pindah plugin mereka search film".
        // So we MUST send request.
        
        // HOWEVER, to prevent blocking UI on every search, maybe launch async?
        // But Strict Mode requires blocking if invalid.
        // We will hit server. The server is fast (NodeJS).
        
        // Only use cache for pure specific "validity checks" if we separate them. 
        // But here validation and logging are combined.
        // Let's disable client-side cache for Tracking Actions to ensure they are logged.
        // But keep cache for "OPEN" (Home)? No, user wants to know when they "pindah plugin".
        // So hitting server is necessary.
        
        // Compromise: We hit server.
        
        try {
            val encodedPlugin = URLEncoder.encode(pluginName, "UTF-8")
            val encodedAction = URLEncoder.encode(action, "UTF-8")
            val encodedData = URLEncoder.encode(data, "UTF-8")
            
            val response = app.get(
                "$SERVER_URL/api/check-ip?plugin=$encodedPlugin&action=$encodedAction&data=$encodedData",
                timeout = 10 // Short timeout
            )

            val json = tryParseJson<LicenseResponse>(response.text)

            if (json == null) {
                // If network fail, check if we have recent valid cache to Allow?
                // Strict mode says "Block on error".
                throw ErrorLoadingException("LICENSE ERROR: Gagal koneksi ke server lisensi. Cek Internet/Firewall.")
            }

            if (json.status != "active") {
                cachedStatus = null
                val msg = json.message
                if (msg.contains("IP belum terdaftar")) {
                    throw ErrorLoadingException("Akses Ditolak: Silakan REFRESH Repository Anda untuk aktivasi ulang.")
                }
                throw ErrorLoadingException("BLOCKED: $msg")
            }

            cachedStatus = "active"
            cacheTime = System.currentTimeMillis()
            return true

        } catch (e: Exception) {
            cachedStatus = null
            if (e is ErrorLoadingException) throw e
            e.printStackTrace()
            throw ErrorLoadingException("License Check Failed: " + e.message)
        }
    }

    // Redirect checkPlay to checkLicense with "PLAY" action
    suspend fun checkPlay(pluginName: String, videoTitle: String): Boolean {
        return checkLicense(pluginName, "PLAY", videoTitle)
    }
}
