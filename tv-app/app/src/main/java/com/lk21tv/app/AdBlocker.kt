package com.lk21tv.app

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    private val BLOCKED_HOST_KEYWORDS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "onclickads.net",
        "hilltopads.net",
        "clickadu.com",
        "admaven.com",
        "galaksion.com",
        "histats.com",
        "sstatic1.histats.com",
        "yandex.ru",
        "mc.yandex.ru",
        "acint.net",
        "bidgear.com",
        "mgid.com",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "adskeeper.co.uk",
        "donasi.assetsy.de",
        "yellowishgather.com",
        "social.santai.lol",
        "adsby",
        "adserver",
        "adsystem",
        "slot",
        "gacor",
        "togel",
        "poker",
        "casino",
        "bet88",
        "maxwin",
        "sbobet"
    )

    private val BLOCKED_PATH_KEYWORDS = listOf(
        "/ads/",
        "/ad/",
        "/banner",
        "/iklan",
        "/popunder",
        "/popup",
        "pop.js",
        "ads.js",
        "seiklasnya.js",
        "seiklasnya.css"
    )

    fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        val url = request.url.toString().lowercase()
        val host = request.url.host?.lowercase() ?: return false

        if (BLOCKED_HOST_KEYWORDS.any { host.contains(it.lowercase()) }) {
            return true
        }

        if (BLOCKED_PATH_KEYWORDS.any { url.contains(it.lowercase()) }) {
            return true
        }

        if (url.endsWith(".gif") && !host.contains("poster.assetsy.de")) {
            return true
        }

        return false
    }

    // Dalam mod pemain video, sekat sebarang cubaan redirect ke laman iklan atau laman utama lk21.de
    fun isAllowedPlayerNavigation(url: String): Boolean {
        val lowerUrl = url.lowercase().trim()
        if (lowerUrl.startsWith("about:blank") || lowerUrl.startsWith("blob:") || lowerUrl.startsWith("data:")) {
            return true
        }
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") {
            return false
        }
        val host = parsed.host?.lowercase() ?: return false

        // Sekat redirect paksa ke laman web utama lk21 apabila dalam pemain video
        if (host == "lk21.de" || host.endsWith(".lk21.de") || host.contains("lk21official")) {
            return false
        }

        if (BLOCKED_HOST_KEYWORDS.any { host.contains(it.lowercase()) }) {
            return false
        }

        if (BLOCKED_PATH_KEYWORDS.any { lowerUrl.contains(it.lowercase()) }) {
            return false
        }

        return true
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    val CLEAN_EMBED_JS = """
        (function() {
            window.open = function() { return null; };
            var adOverlay = document.getElementById('uyeouyeo');
            if (adOverlay) adOverlay.remove();
            var adContainer = document.getElementById('adContainer');
            if (adContainer) adContainer.remove();
            var styleId = 'lk21-clean-embed-style';
            if (!document.getElementById(styleId)) {
                var s = document.createElement('style');
                s.id = styleId;
                s.innerHTML = `
                    #uyeouyeo, #adContainer, #videoAd, #adsLink, .banner, .iklan, .ads, a[target="_blank"] {
                        display: none !important;
                        visibility: hidden !important;
                        pointer-events: none !important;
                    }
                    body, html {
                        background: #000 !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        overflow: hidden !important;
                    }
                `;
                document.head.appendChild(s);
            }
        })();
    """.trimIndent()
}
