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

    // Domain pemain video (mirror) & CDN rasmi LK21 yang dibenarkan
    private val ALLOWED_PLAYER_KEYWORDS = listOf(
        "lk21",
        "nontondrama",
        "layarkaca",
        "layanfilem",
        "dunia21",
        "videonode",
        "assetsy.de",
        "dadadidi",
        "gudangvape",
        "hydrax",
        "abysscdn",
        "iamcdn",
        "turbovip",
        "emturbovid",
        "turbovid",
        "cast",
        "p2p",
        "hownetwork",
        "player",
        "embed",
        "iframe",
        "filemoon",
        "dood",
        "streamtape",
        "mixdrop",
        "mp4upload",
        "vidhide",
        "filelions",
        "f16px",
        "cdn",
        "cloudflare",
        "googleapis",
        "gstatic",
        "cdnjs"
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

        // Sekat sepanduk iklan .gif kecuali poster rasmi
        if (url.endsWith(".gif") && !host.contains("poster.assetsy.de")) {
            return true
        }

        return false
    }

    fun isAllowedNavigation(url: String, currentBaseHost: String): Boolean {
        val parsed = Uri.parse(url)
        val host = parsed.host?.lowercase() ?: return false
        val baseClean = Uri.parse(currentBaseHost).host?.lowercase() ?: ""

        if (baseClean.isNotEmpty() && host.contains(baseClean.removePrefix("tv12.").removePrefix("www."))) {
            return true
        }

        if (BLOCKED_HOST_KEYWORDS.any { host.contains(it.lowercase()) }) {
            return false
        }

        return ALLOWED_PLAYER_KEYWORDS.any { host.contains(it.lowercase()) || url.lowercase().contains(it.lowercase()) }
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    // Skrip CSS & JS untuk membuang #adContainer (iklan video LK21), sepanduk & pop-up
    val CLEAN_PAGE_JS = """
        (function() {
            window.open = function() { return null; };
            
            var styleId = 'lk21-tv-adblock-style';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    #adContainer, #videoAd, #adsLink, #skipAds,
                    .banner, .banners, .iklan, .ads, .ad-box, .ad-container,
                    .header-ads, .footer-ads, .floating-ads, .popup-ads,
                    div[id*="iklan"], div[class*="iklan"],
                    div[id*="banner"], div[class*="banner"],
                    div[id*="ads-"], div[class*="ads-"],
                    a[href*="bit.ly"], a[href*="rebrand.ly"], a[href*="cutt.ly"],
                    img[src*=".gif"], ins.adsbygoogle, iframe[src*="ads"] {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        width: 0 !important;
                        pointer-events: none !important;
                    }
                    #main-player {
                        display: block !important;
                        visibility: visible !important;
                        z-index: 50 !important;
                    }
                `;
                document.head.appendChild(style);
            }

            var adBox = document.getElementById('adContainer');
            if (adBox) adBox.remove();
        })();
    """.trimIndent()
}
