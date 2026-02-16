package com.animasu

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.lagradost.nicehttp.RequestBodyTypes

object LicenseClient {
    // NOTE: Replace this with your actual server URL
    private const val SERVER_URL = "http://172.83.15.6:3000" 

    suspend fun checkPlay(pluginName: String, videoTitle: String): Boolean {
        try {
            // Using generic app.post from Cloudstream
            // We rely on IP authentication on the server side
                        val response = app.post(
                "$SERVER_URL/api/check-play",
                requestBody = mapOf(
                    "plugin_name" to pluginName,
                    "video_title" to videoTitle
                ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())
            )

            if (response.code == 200) {
                 val jsonResponse = JSONObject(response.text)
                 return jsonResponse.optBoolean("allowed", false)
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail open or closed? Strict mode = Fail closed (return false)
            return false
        }
    }
}
