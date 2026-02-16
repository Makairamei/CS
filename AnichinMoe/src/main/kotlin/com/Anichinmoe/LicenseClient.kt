package com.Anichinmoe

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object LicenseClient {
    // Hardcoded as requested to sync with 'aw' logic
    private const val SERVER_URL = "http://172.83.15.6:3000"
    
    private var cachedStatus: String? = null
    private var cachedPlugin: String? = null
    private var cacheTime: Long = 0
    private const val CACHE_MS = 5 * 1000L // 5 seconds cache for realtime feel

    data class LicenseResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = ""
    )

    private val lastCheckTime = mutableMapOf<String, Long>()

    suspend fun checkLicense(pluginName: String, action: String = "OPEN", data: String? = null): Boolean {
        // Debounce HOME Check (Max 1 check per 60 seconds per plugin)
        if (action == "HOME") {
            val key = "$pluginName|$action"
            val now = System.currentTimeMillis()
            if (now - (lastCheckTime[key] ?: 0L) < 60000L) return true
            lastCheckTime[key] = now
        }
        
        // Cache Check (Only for OPEN, and must match Plugin Name)
        if (action == "OPEN" && cachedStatus == "active" && cachedPlugin == pluginName && System.currentTimeMillis() - cacheTime < CACHE_MS) return true

        try {
            // Truncate data to avoid URL length issues (max 500 chars)
            val safeData = data?.take(500) ?: ""
            val encodedData = if (safeData.isNotEmpty()) java.net.URLEncoder.encode(safeData, "UTF-8") else ""
            
            val response = app.get(
                "$SERVER_URL/api/check-ip?plugin=$pluginName&action=$action&data=$encodedData",
                timeout = 10
            )

            val json = tryParseJson<LicenseResponse>(response.text)

            if (json == null) {
                cachedStatus = null
                // Fail CLOSED (Strict Mode)
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

            // Update Cache
            cachedStatus = "active"
            cachedPlugin = pluginName
            cacheTime = System.currentTimeMillis()
            return true

        } catch (e: Exception) {
            cachedStatus = null
            if (e is ErrorLoadingException) throw e
            e.printStackTrace()
            // Fail CLOSED
            throw ErrorLoadingException("License Check Failed: " + e.message)
        }
    }

    // Keep checkPlay for backward compatibility if plugins usage it, 
    // but redirect to checkLicense internally or just use checkLicense logic
    suspend fun checkPlay(pluginName: String, videoTitle: String): Boolean {
        return checkLicense(pluginName, "PLAY", videoTitle)
    }
}
