package com.lk21tv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LK21ProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LK21Provider())
    }
}
