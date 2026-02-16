package com.kissasian

import com.lagradost.cloudstream3.app
import org.json.JSONObject

object LicenseClient {
    // NOTE: Replace this with your actual server URL or ensure it's configured correctly
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
