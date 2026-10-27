package com.lk21tv.app

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    // Senarai kata kunci & domain iklan / judi / pop-up yang biasa muncul di LK21
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
        "yandex.ru",
        "mc.yandex.ru",
        "acint.net",
        "DTScout",
        "bidgear.com",
        "mgid.com",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "adskeeper.co.uk",
        "adsby",
        "adserver",
        "adsystem",
        "Slot",
        "gacor",
        "togel",
        "poker",
        "casino",
        "bet88",
        "zeus",
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
        "adblock",
        "prebid",
        ".gif" // Hampir semua sepanduk iklan di LK21 menggunakan fail .gif
    )

    // Domain pemain video (mirror) yang sah untuk dibenarkan dalam iframe
    private val ALLOWED_PLAYER_KEYWORDS = listOf(
        "lk21official",
        "nontondrama",
        "layarkaca",
        "dunia21",
        "hydrax",
        "abysscdn",
        "turbovip",
        "emturbovid",
        "turbovid",
        "cast",
        "p2p",
        "hownetwork",
        "player",
        "embed",
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
        "jquery",
        "fontawesome",
        "wp-content",
        "wp-includes"
    )

    fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        val url = request.url.toString().lowercase()
        val host = request.url.host?.lowercase() ?: return false

        // Sekat jika padan dengan kata kunci domain iklan
        if (BLOCKED_HOST_KEYWORDS.any { host.contains(it.lowercase()) }) {
            return true
        }

        // Sekat jika URL mempunyai laluan iklan atau banner GIF
        if (BLOCKED_PATH_KEYWORDS.any { url.contains(it.lowercase()) }) {
            // Jangan sekat jika ia adalah poster rasmi dalam folder upload poster
            if (!url.contains("poster") && !url.contains("tmdb") && !url.contains("image.tmdb")) {
                return true
            }
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

    // Skrip CSS & JS untuk membuang semua sepanduk iklan, overlay & pop-up secara paksa
    val CLEAN_PAGE_JS = """
        (function() {
            // Matikan window.open supaya tiada pop-up iklan boleh dibuka
            window.open = function() { return null; };
            
            var styleId = 'lk21-tv-adblock-style';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    /* Sembunyikan semua kontena iklan & sepanduk LK21 */
                    .banner, .banners, .iklan, .ads, .ad-box, .ad-container,
                    .header-ads, .footer-ads, .floating-ads, .popup-ads,
                    div[id*="iklan"], div[class*="iklan"],
                    div[id*="banner"], div[class*="banner"],
                    div[id*="ads-"], div[class*="ads-"],
                    a[href*="bit.ly"], a[href*="rebrand.ly"], a[href*="cutt.ly"],
                    a[href*="t.me"], a[target="_blank"] > img[src*=".gif"],
                    img[src*=".gif"], ins.adsbygoogle, iframe[src*="ads"] {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        width: 0 !important;
                        pointer-events: none !important;
                    }
                    body {
                        background-color: #0f172a !important;
                        overflow-x: hidden !important;
                    }
                `;
                document.head.appendChild(style);
            }

            // Buang lapisan overlay klik lutsinar yang biasa dipasang di atas pemain video
            var overlays = document.querySelectorAll('div[style*="z-index: 999"], div[style*="z-index:999"], a[target="_blank"]');
            overlays.forEach(function(el) {
                if (el.tagName === 'A' && !el.href.includes(window.location.hostname)) {
                    el.removeAttribute('href');
                    el.onclick = function(e) { e.preventDefault(); return false; };
                }
            });
        })();
    """.trimIndent()
}
