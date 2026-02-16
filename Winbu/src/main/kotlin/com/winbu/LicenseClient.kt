package com.winbu

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.cloudstream3.utils.AppUtils.toJson

object LicenseClient {
    // NOTE: Replace this with your actual server URL or ensure it's configured correctly
    private const val SERVER_URL = "http://172.83.15.6:3000" 

    suspend fun checkPlay(pluginName: String, videoTitle: String): Boolean {
        try {
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
            return false
        }
    }
}
