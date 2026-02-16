package com.hexated

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.lagradost.nicehttp.RequestBodyTypes
import com.lagradost.cloudstream3.utils.AppUtils.toJson

object LicenseClient {
    // HARDCODED FOR DEBUGGING - To ensure BuildConfig isn't failing
    private const val SERVER_URL = "http://172.83.15.6:3000"

    suspend fun checkLicense(pluginName: String): Boolean {
        try {
            val response = app.get("$SERVER_URL/api/check-ip?plugin=$pluginName")
            if (response.code == 200) {
                 val jsonResponse = JSONObject(response.text)
                 return jsonResponse.optBoolean("valid", false)
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

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
            // Log failure code
            println("License Check Failed: Code ${response.code}")
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            // Log exception
            println("License Check Exception: ${e.message}")
            return false
        }
    }
}
