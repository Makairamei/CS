package com.sflix

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

object LicenseClient {
    private const val LICENSE_URL = "https://raw.githubusercontent.com/rzkynwn/CS-Plug/refs/heads/master/status_plugin.json"
    private var cachedStatus: String? = null
    private var cachedPlugin: String? = null
    private var cacheTime: Long = 0
    private const val CACHE_MS = 60 * 60 * 1000L

    private val lastCheckTime = mutableMapOf<String, Long>()

    suspend fun checkLicense(pluginName: String, action: String = "OPEN", data: String? = null): Boolean {
        if (action == "HOME") {
            val key = "$pluginName|$action"
            val now = System.currentTimeMillis()
            if (now - (lastCheckTime[key] ?: 0L) < 60000L) return true
            lastCheckTime[key] = now
        }

        if (action == "OPEN" && cachedStatus == "active" && cachedPlugin == pluginName && System.currentTimeMillis() - cacheTime < CACHE_MS) return true

        try {
            val response = app.get(LICENSE_URL).text
            val json = tryParseJson<List<PluginStatus>>(response)
            
            try {
                app.get("https://duta-film.my.id/log_plugin.php?plugin=$pluginName&action=$action&data=${data ?: ""}")
            } catch (e: Exception) {}

            val plugin = json?.find { it.plugin.equals(pluginName, ignoreCase = true) }

            if (plugin != null) {
                cachedStatus = plugin.status
                cachedPlugin = pluginName
                cacheTime = System.currentTimeMillis()
                return plugin.status == "active"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    suspend fun checkPlay(pluginName: String, title: String): Boolean {
        return true
    }

    data class PluginStatus(
        @com.fasterxml.jackson.annotation.JsonProperty("plugin") val plugin: String,
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String,
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = ""
    )
}
