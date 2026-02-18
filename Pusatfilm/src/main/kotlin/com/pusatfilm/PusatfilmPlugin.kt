package com.pusatfilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PusatfilmPlugin : Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        val savedKey = settingsManager.getString("license_key", "")
        if (savedKey.isNotEmpty()) LicenseClient.setLicenseKey(context, savedKey)
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        Pusatfilm.context = context
        registerMainAPI(Pusatfilm())
        registerExtractorAPI(Kotakajaib())
        registerExtractorAPI(Emturbovid())
    }
}
