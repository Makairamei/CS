package com.hexated

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

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

    suspend fun checkLicense(pluginName: String): Boolean {
        // Cache Check
        if (cachedStatus == "active" && System.currentTimeMillis() - cacheTime < CACHE_MS) return true

        try {
            val response = app.get(
                "$SERVER_URL/api/check-ip?plugin=$pluginName",
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

            cachedStatus = "active"
            cacheTime = System.currentTimeMillis()
            return true

        } catch (e: Exception) {
            cachedStatus = null
            if (e is ErrorLoadingException) throw e
            e.printStackTrace()
            // Fail CLOSED
            throw ErrorLoadingException("License Check Failed: ${e.message}")
        }
    }
}
