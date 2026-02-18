package PACKAGE_NAME

import android.content.Context
import android.provider.Settings
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import android.util.Log

object LicenseClient {
    private const val TAG = "LicenseClient"
    private const val SERVER_URL = "http://159.223.82.116:3000"
    private const val PREF_NAME = "cs_premium"
    private const val PREF_KEY = "license_key"

    // Cache
    private var cachedStatus: String? = null
    private var cacheExpiry: Long = 0L
    private val actionThrottle = mutableMapOf<String, Long>()

    // Block state
    private var licenseBlocked = false
    private var blockMessage = ""

    // App context
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setLicenseKey(context: Context, key: String) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, key.trim()).apply()
        resetCache()
    }

    fun getLicenseKey(): String? {
        return appContext
            ?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            ?.getString(PREF_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                appContext?.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }

    /**
     * Core license check. Returns true if license is valid.
     */
    suspend fun checkLicense(
        pluginName: String,
        action: String = "OPEN",
        data: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()

        // Throttle: HOME max once per 60s, SEARCH max once per 10s, others max once per 5s
        val throttleKey = "$pluginName|$action"
        val throttleMs = when (action.uppercase()) {
            "HOME" -> 60_000L
            "SEARCH" -> 10_000L
            else -> 5_000L
        }
        val lastCheck = actionThrottle[throttleKey] ?: 0L
        if (now - lastCheck < throttleMs && cachedStatus == "active") {
            return true
        }
        actionThrottle[throttleKey] = now

        // Use cache for non-PLAY actions (5 minute cache)
        if (cachedStatus == "active" && now < cacheExpiry && action.uppercase() != "PLAY") {
            logActionAsync(pluginName, action, data)
            return true
        }

        val key = getLicenseKey()
        if (key.isNullOrEmpty()) {
            licenseBlocked = true
            blockMessage = "License key belum diatur. Masukkan license key di pengaturan plugin."
            Log.w(TAG, "No license key configured")
            return false
        }

        return try {
            val deviceId = getDeviceId()
            val encodedPlugin = java.net.URLEncoder.encode(pluginName, "UTF-8")
            val encodedAction = java.net.URLEncoder.encode(action, "UTF-8")
            val encodedData = java.net.URLEncoder.encode(data ?: "", "UTF-8")

            // Call /api/validate — this creates the IP session AND validates the key
            val url = "$SERVER_URL/api/validate?plugin=$encodedPlugin&action=$encodedAction&data=$encodedData"
            val response = app.post(
                url,
                data = mapOf(
                    "key" to key,
                    "device_id" to deviceId,
                    "device_name" to "Android"
                )
            ).text

            val json = tryParseJson<ValidateResponse>(response)

            if (json?.status == "active") {
                cachedStatus = "active"
                cacheExpiry = now + 300_000L  // 5 minute cache
                licenseBlocked = false
                blockMessage = ""
                true
            } else {
                cachedStatus = "error"
                licenseBlocked = true
                blockMessage = json?.message ?: "License tidak valid"
                Log.w(TAG, "License check failed for $pluginName: $blockMessage")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "License check network error: ${e.message}")
            // Grace period: if recently validated, allow temporarily
            if (cachedStatus == "active" && now < cacheExpiry + 600_000L) {
                true
            } else {
                licenseBlocked = true
                blockMessage = "Tidak dapat memverifikasi lisensi. Periksa koneksi internet."
                false
            }
        }
    }

    /**
     * Enforced license check — throws exception if invalid.
     */
    suspend fun requireLicense(
        pluginName: String,
        action: String = "OPEN",
        data: String? = null
    ) {
        if (!checkLicense(pluginName, action, data)) {
            throw RuntimeException("[PREMIUM] $blockMessage")
        }
    }

    suspend fun checkPlay(pluginName: String, title: String): Boolean {
        return checkLicense(pluginName, "PLAY", title)
    }

    suspend fun requirePlay(pluginName: String, title: String) {
        requireLicense(pluginName, "PLAY", title)
    }

    suspend fun trackDownload(pluginName: String, title: String): Boolean {
        return checkLicense(pluginName, "DOWNLOAD", title)
    }

    fun isBlocked(): Boolean = licenseBlocked
    fun getBlockMessage(): String = blockMessage

    private fun logActionAsync(pluginName: String, action: String, data: String?) {
        val key = getLicenseKey() ?: return
        try {
            val encodedData = java.net.URLEncoder.encode(data ?: "", "UTF-8")
            val encodedPlugin = java.net.URLEncoder.encode(pluginName, "UTF-8")
            Thread {
                try {
                    val url = "$SERVER_URL/api/check-ip?plugin=$encodedPlugin&action=$action&data=$encodedData"
                    java.net.URL(url).readText()
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }

    fun resetCache() {
        cachedStatus = null
        cacheExpiry = 0L
        licenseBlocked = false
        blockMessage = ""
        actionThrottle.clear()
    }

    data class ValidateResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("status") val status: String,
        @com.fasterxml.jackson.annotation.JsonProperty("message") val message: String = "",
        @com.fasterxml.jackson.annotation.JsonProperty("days_left") val daysLeft: Int = 0
    )
}
