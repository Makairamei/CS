package com.oppadrama

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class OppadramaPlugin : Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        val savedKey = settingsManager.getString("license_key", "")
        if (savedKey.isNotEmpty()) LicenseClient.setLicenseKey(context, savedKey)
        Oppadrama.context = context
        registerMainAPI(Oppadrama())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(BuzzServer())
    }
}
