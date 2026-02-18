
package com.oploverz

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class OploverzProviderPlugin: Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        val savedKey = settingsManager.getString("license_key", "")
        if (savedKey.isNotEmpty()) LicenseClient.setLicenseKey(context, savedKey)
        // All providers should be added in this manner. Please don't edit the providers list directly.
        OploverzProvider.context = context
        registerMainAPI(OploverzProvider())
        registerExtractorAPI(Qiwi())
        registerExtractorAPI(Filedon())
        registerExtractorAPI(Buzzheavier())
    }
}