package com.nomat

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NomatPlugin : Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        val savedKey = settingsManager.getString("license_key", "")
        if (savedKey.isNotEmpty()) LicenseClient.setLicenseKey(context, savedKey)

        Nomat.context = context
        registerMainAPI(Nomat())
        registerExtractorAPI(Hydrax())
    }
}
