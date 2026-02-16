package com.kisskh

import com.lagradost.cloudstream3.app
import org.json.JSONObject

object LicenseClient {
    // NOTE: Base URL for license check (IP-based auth)
    private const val SERVER_URL = "http://localhost:3000" 

    suspend fun checkPlay(pluginName: String, videoTitle: String): Boolean {
        try {
            val response = app.post(
                "$SERVER_URL/api/check-play",
                contentType = "application/json",
                data = mapOf(
                    "plugin_name" to pluginName,
                    "video_title" to videoTitle
                )
            )

            if (response.code == 200) {
                 val jsonResponse = JSONObject(response.text)
                 return jsonResponse.optBoolean("allowed", false)
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
