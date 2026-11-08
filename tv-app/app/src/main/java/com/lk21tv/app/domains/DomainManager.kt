package com.lk21tv.app.domains

import android.content.Context
import com.lk21tv.app.MediaDetail
import com.lk21tv.app.MediaItem
import com.lk21tv.app.MirrorItem
import com.lk21tv.app.ResolvedStream
import com.lk21tv.app.domains.lk21.Lk21Domain
import com.lk21tv.app.domains.watch32.Watch32Domain
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

data class DualDomainMediaResult(
    val lk21Detail: MediaDetail?,
    val watch32Detail: MediaDetail?,
    val preferredDomain: String
) {
    fun primaryDetail(): MediaDetail? {
        return when (preferredDomain) {
            Watch32Domain.ID -> watch32Detail ?: lk21Detail
            else -> lk21Detail ?: watch32Detail
        }
    }

    fun hasBothDomains(): Boolean = lk21Detail != null && watch32Detail != null
}

/**
 * PENGURUS DOMAIN BERSEPADU (DomainManager)
 * Menguruskan pilihan tick domain pengguna:
 *  - [✓] 1. LK21
 *  - [✓] 2. WATCH32 (watch32.site)
 * Serta menyediakan resolusi cerita merentas kedua-dua domain supaya pengguna
 * boleh memilih domain mana yang ingin ditonton di dalam skrin Movie atau Series.
 */
object DomainManager {

    private const val PREFS_NAME = "citeraloka_prefs"
    private const val KEY_LK21_ENABLED = "domain_lk21_enabled"
    private const val KEY_WATCH32_ENABLED = "domain_watch32_enabled"

    fun isLk21Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LK21_ENABLED, true)
    }

    fun isWatch32Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WATCH32_ENABLED, true)
    }

    fun setDomainsEnabled(context: Context, lk21Enabled: Boolean, watch32Enabled: Boolean) {
        val safeLk21 = if (!lk21Enabled && !watch32Enabled) true else lk21Enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LK21_ENABLED, safeLk21)
            .putBoolean(KEY_WATCH32_ENABLED, watch32Enabled)
            .apply()
    }

    fun getDomainStatusBadge(context: Context): String {
        val lk = isLk21Enabled(context)
        val w32 = isWatch32Enabled(context)
        return when {
            lk && w32 -> "LK21 + WATCH32"
            w32 -> "WATCH32"
            else -> "LK21"
        }
    }

    fun getDomainButtonLabel(context: Context): String {
        val lk = isLk21Enabled(context)
        val w32 = isWatch32Enabled(context)
        return when {
            lk && w32 -> "DOMAIN: LK21 + W32"
            w32 -> "DOMAIN: WATCH32"
            else -> "DOMAIN: LK21"
        }
    }

    fun getActiveStatusLabel(context: Context): String {
        val lk = isLk21Enabled(context)
        val w32 = isWatch32Enabled(context)
        return when {
            lk && w32 -> "LK21 + WATCH32"
            w32 -> "WATCH32.SITE"
            else -> "LK21"
        }
    }

    /**
     * Muat turun katalog mengikut domain yang ditick oleh pengguna (1. LK21, 2. Watch32, atau Kedua-duanya).
     */
    fun fetchCatalog(
        context: Context,
        executor: ExecutorService,
        baseUrl: String,
        path: String,
        page: Int
    ): List<MediaItem> {
        val lkEnabled = isLk21Enabled(context)
        val w32Enabled = isWatch32Enabled(context)

        if (lkEnabled && !w32Enabled) {
            return Lk21Domain.fetchCatalog(baseUrl, path, page)
        }
        if (!lkEnabled && w32Enabled) {
            return Watch32Domain.fetchCatalog(path, page)
        }

        // Kedua-dua domain ditick (LK21 + Watch32): tarik secara selari & gabungkan
        val w32Future = executor.submit(Callable {
            Watch32Domain.fetchCatalog(path, page)
        })
        val lkItems = Lk21Domain.fetchCatalog(baseUrl, path, page)
        val w32Items = try {
            w32Future.get()
        } catch (_: Exception) {
            emptyList()
        }

        if (lkItems.isEmpty()) return w32Items
        if (w32Items.isEmpty()) return lkItems

        val combined = mutableListOf<MediaItem>()
        val seenKeys = HashSet<String>()
        val maxLen = maxOf(lkItems.size, w32Items.size)

        for (i in 0 until maxLen) {
            if (i < lkItems.size) {
                val lk = lkItems[i]
                val key = "${normalizeTitleKey(lk.title)}_${lk.isSeries}"
                if (seenKeys.add(key)) {
                    combined.add(lk)
                }
            }
            if (i < w32Items.size) {
                val w32 = w32Items[i]
                val key = "${normalizeTitleKey(w32.title)}_${w32.isSeries}"
                if (seenKeys.add(key)) {
                    combined.add(w32)
                }
            }
        }
        return combined
    }

    /**
     * Dapatkan butiran daripada KEDUA-DUA domain (1. LK21 dan 2. Watch32) untuk cerita yang dipilih,
     * supaya pengguna boleh pilih/tick domain mana yang hendak ditonton!
     */
    fun fetchDualDetails(
        executor: ExecutorService,
        baseUrl: String,
        pageUrl: String,
        title: String,
        isSeries: Boolean
    ): DualDomainMediaResult {
        val isFromWatch32 = Watch32Domain.isWatch32Uri(pageUrl)
        return if (isFromWatch32) {
            val lkFuture = executor.submit(Callable {
                Lk21Domain.findMatchingDetail(baseUrl, title, preferSeries = isSeries)
            })
            val w32Detail = Watch32Domain.fetchDetail(pageUrl)
            val lkDetail = try {
                lkFuture.get()
            } catch (_: Exception) {
                null
            }
            DualDomainMediaResult(
                lk21Detail = lkDetail,
                watch32Detail = w32Detail,
                preferredDomain = Watch32Domain.ID
            )
        } else {
            val w32Future = executor.submit(Callable {
                Watch32Domain.findMatchingDetail(title, preferSeries = isSeries)
            })
            val lkDetail = Lk21Domain.fetchDetail(baseUrl, pageUrl, enrichWithTmdb = true)
            val w32Detail = try {
                w32Future.get()
            } catch (_: Exception) {
                null
            }
            DualDomainMediaResult(
                lk21Detail = lkDetail,
                watch32Detail = w32Detail,
                preferredDomain = if (lkDetail != null) Lk21Domain.ID else Watch32Domain.ID
            )
        }
    }

    fun combineMirrors(
        lkDetail: MediaDetail?,
        w32Detail: MediaDetail?,
        useLk21: Boolean,
        useWatch32: Boolean
    ): List<MirrorItem> {
        val result = mutableListOf<MirrorItem>()
        if (useLk21 && lkDetail != null) {
            lkDetail.mirrors.forEach { m ->
                val cleanName = if (m.name.startsWith("LK21")) m.name else "LK21 • ${m.name}"
                if (result.none { it.embedUrl == m.embedUrl }) {
                    result.add(MirrorItem(cleanName, m.embedUrl))
                }
            }
        }
        if (useWatch32 && w32Detail != null) {
            w32Detail.mirrors.forEach { m ->
                val cleanName = if (m.name.startsWith("W32")) m.name else "W32 • ${m.name}"
                if (result.none { it.embedUrl == m.embedUrl }) {
                    result.add(MirrorItem(cleanName, m.embedUrl))
                }
            }
        }
        return result
    }

    fun resolveStream(embedUrl: String, serverName: String): ResolvedStream {
        return if (Watch32Domain.isWatch32EmbedUrl(embedUrl)) {
            Watch32Domain.resolveStream(embedUrl, serverName)
        } else {
            Lk21Domain.resolveStream(embedUrl, serverName)
        }
    }

    private fun normalizeTitleKey(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
