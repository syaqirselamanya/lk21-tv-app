package com.citeraloka.phone.domains

import android.content.Context
import com.citeraloka.phone.MediaDetail
import com.citeraloka.phone.MediaItem
import com.citeraloka.phone.MirrorItem
import com.citeraloka.phone.ResolvedStream
import com.citeraloka.phone.domains.lk21.Lk21Domain
import com.citeraloka.phone.domains.watch32.Watch32Domain
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

data class DualDomainMediaResult(
    val lk21Detail: MediaDetail?,
    val watch32Detail: MediaDetail?,
    val preferredDomain: String
) {
    val primaryDetail: MediaDetail?
        get() = when (preferredDomain) {
            Watch32Domain.ID -> watch32Detail ?: lk21Detail
            else -> lk21Detail ?: watch32Detail
        }

    fun primaryDetail(): MediaDetail? = primaryDetail

    fun hasBothDomains(): Boolean = lk21Detail != null && watch32Detail != null
}

object DomainManager {

    private const val PREFS_NAME = "citeraloka_prefs"
    private const val KEY_LK21_ENABLED = "domain_lk21_enabled"
    private const val KEY_WATCH32_ENABLED = "domain_watch32_enabled"
    private val bgPool = java.util.concurrent.Executors.newFixedThreadPool(4)

    fun isLk21Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LK21_ENABLED, true)
    }

    fun isWatch32Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WATCH32_ENABLED, true)
    }

    fun setLk21Enabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LK21_ENABLED, enabled)
            .apply()
    }

    fun setWatch32Enabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WATCH32_ENABLED, enabled)
            .apply()
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
            lk && w32 -> "BOTH"
            w32 -> "W32"
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

    fun fetchCatalog(
        context: Context,
        baseUrl: String,
        path: String,
        page: Int
    ): List<MediaItem> = fetchCatalog(context, bgPool, baseUrl, path, page)

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

    fun fetchDualDetails(
        context: Context,
        lk21BaseUrl: String,
        primaryUrl: String,
        fallbackTitle: String,
        isSeriesHint: Boolean
    ): DualDomainMediaResult = fetchDualDetails(
        executor = bgPool,
        baseUrl = lk21BaseUrl,
        pageUrl = primaryUrl,
        title = fallbackTitle,
        isSeries = isSeriesHint
    )

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
