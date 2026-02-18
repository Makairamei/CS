package com.nomat

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.excloud.BuildConfig

object LicenseClient {
    private val SERVER_URL = BuildConfig.LICENSE_SERVER_URL.trimEnd('/')
    private val lastCheckTime = mutableMapOf<String, Long>()

    suspend fun checkLicense(pluginName: String, action: String = "OPEN", data: String? = null): Boolean {
        if (action == "HOME") {
            val key = "$pluginName|$action"
            val now = System.currentTimeMillis()
            if (now - (lastCheckTime[key] ?: 0L) < 60000L) return true
            lastCheckTime[key] = now
        }

        try {
            val url = "$SERVER_URL/api/check-ip?plugin=$pluginName&action=$action&data=${data ?: ""}"
            val response = app.get(url).text
            val json = tryParseJson<CheckResponse>(response)
            return json?.status == "active"
        } catch (e: Exception) {
            // Graceful degradation: allow access on network error
            return true
        }
    }

    suspend fun checkPlay(pluginName: String, title: String): Boolean {
        return checkLicense(pluginName, "PLAY", title)
    }

    data class CheckResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String,
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = ""
    )
}