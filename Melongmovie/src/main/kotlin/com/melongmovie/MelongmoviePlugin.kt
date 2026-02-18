package com.melongmovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MelongmoviePlugin : Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        val savedKey = settingsManager.getString("license_key", "")
        if (savedKey.isNotEmpty()) LicenseClient.setLicenseKey(context, savedKey)
        Melongmovie.context = context
        registerMainAPI(Melongmovie())
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Melongfilmstrp2p())
        registerExtractorAPI(Dintezuvio())
    }
}
