package com.lk21tv.app.domains.lk21

import com.lk21tv.app.LK21Scraper
import com.lk21tv.app.MediaDetail
import com.lk21tv.app.MediaItem
import com.lk21tv.app.ResolvedStream
import com.lk21tv.app.Watch32Scraper
import java.net.URLEncoder
import java.util.Locale

/**
 * MODUL DOMAIN 1: LK21 (lk21official.cc)
 * Dipisahkan secara modular supaya sebarang kemas kini pada domain LK21
 * boleh dilakukan terus di dalam pakej ini tanpa mengganggu domain lain.
 */
object Lk21Domain {

    const val ID = "LK21"
    const val LABEL = "1. LK21"
    const val DEFAULT_BASE_URL = "https://tv12.lk21official.cc"

    fun isLk21Uri(url: String): Boolean {
        return url.isNotBlank() && !Watch32Scraper.isWatch32Uri(url)
    }

    fun fetchCatalog(baseUrl: String, pathOrUrl: String, page: Int = 1): List<MediaItem> {
        return try {
            LK21Scraper.fetchCatalog(baseUrl, pathOrUrl, page).map {
                it.copy(source = ID)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchDetail(baseUrl: String, pageUrl: String, enrichWithTmdb: Boolean = true): MediaDetail? {
        return try {
            val raw = LK21Scraper.fetchDetail(baseUrl, pageUrl)
            if (raw.mainEmbedUrl.isNullOrBlank() && raw.mirrors.isEmpty() && raw.episodes.isEmpty()) {
                return null
            }
            val tagged = raw.copy(source = ID)
            if (!enrichWithTmdb) {
                tagged
            } else if (tagged.episodes.isNotEmpty()) {
                Watch32Scraper.enrichSeriesDetail(tagged).copy(source = ID)
            } else {
                Watch32Scraper.enrichMovieDetail(tagged).copy(source = ID)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cari padanan tajuk yang sama di dalam domain LK21 (untuk pilihan multi-domain
     * apabila pengguna memilih cerita yang asalnya daripada Watch32).
     */
    fun findMatchingDetail(baseUrl: String, rawTitle: String, preferSeries: Boolean = false): MediaDetail? {
        return try {
            val cleanTitle = rawTitle
                .substringBefore(" • ")
                .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
                .trim()
            if (cleanTitle.isBlank()) return null

            val yearMatch = Regex("\\((\\d{4})\\)").find(rawTitle)?.groupValues?.getOrNull(1) ?: ""
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
            val candidates = LK21Scraper.fetchCatalog(baseUrl, "/search?s=$encoded", 1)
            if (candidates.isEmpty()) return null

            val targetKey = normalizeKey(cleanTitle)
            val best = candidates.maxByOrNull { item ->
                var score = 0
                val itemClean = item.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim()
                val itemKey = normalizeKey(itemClean)
                if (itemKey == targetKey) score += 100
                else if (itemKey.contains(targetKey) || targetKey.contains(itemKey)) score += 55
                if (yearMatch.isNotBlank() && item.title.contains("($yearMatch)")) score += 35
                if (item.isSeries == preferSeries) score += 40
                score
            } ?: return null

            val bestKey = normalizeKey(best.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), ""))
            if (bestKey != targetKey && !bestKey.contains(targetKey) && !targetKey.contains(bestKey)) {
                return null
            }

            fetchDetail(baseUrl, best.url, enrichWithTmdb = true)
        } catch (_: Exception) {
            null
        }
    }

    fun resolveStream(embedUrl: String, serverName: String = "P2P Direct"): ResolvedStream {
        return LK21Scraper.resolveStream(embedUrl, serverName)
    }

    private fun normalizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }
}
