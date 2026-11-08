package com.citeraloka.phone.domains.watch32

import com.citeraloka.phone.MediaDetail
import com.citeraloka.phone.MediaItem
import com.citeraloka.phone.MirrorItem
import com.citeraloka.phone.ResolvedStream
import com.citeraloka.phone.Watch32Scraper

/**
 * MODUL DOMAIN 2: WATCH32 (watch32.site)
 * Dipisahkan secara modular supaya sebarang kemas kini pelayan embed atau API Watch32
 * boleh diselenggara di dalam pakej ini sahaja tanpa menjejaskan domain LK21.
 */
object Watch32Domain {

    const val ID = "WATCH32"
    const val LABEL = "2. WATCH32 (watch32.site)"
    const val ORIGIN = "https://watch32.site"

    fun isWatch32Uri(url: String): Boolean = Watch32Scraper.isWatch32Uri(url)

    fun isWatch32EmbedUrl(url: String): Boolean = Watch32Scraper.isWatch32EmbedUrl(url)

    fun fetchCatalog(pathOrUrl: String, page: Int = 1): List<MediaItem> {
        return try {
            Watch32Scraper.fetchCatalog(pathOrUrl, page).map {
                it.copy(source = ID)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fetchDetail(watch32Uri: String): MediaDetail? {
        return try {
            Watch32Scraper.fetchDetail(watch32Uri).copy(source = ID)
        } catch (_: Exception) {
            null
        }
    }

    fun findMatchingDetail(
        rawTitle: String,
        episodeLabel: String = "",
        preferSeries: Boolean = false
    ): MediaDetail? {
        return try {
            Watch32Scraper.findFallbackDetail(rawTitle, episodeLabel, preferSeries)?.copy(source = ID)
        } catch (_: Exception) {
            null
        }
    }

    fun buildEpisodeMirrors(tmdbId: Int, season: Int, episode: Int): List<MirrorItem> {
        return Watch32Scraper.buildMirrors(
            tmdbId = tmdbId,
            isSeries = true,
            season = season,
            episode = episode
        )
    }

    fun resolveStream(embedUrl: String, serverName: String = "W32 • Cinezo"): ResolvedStream {
        val referer = if (embedUrl.contains("cinezo.live", ignoreCase = true)) {
            "https://arrowtv.net/"
        } else {
            "$ORIGIN/"
        }
        return ResolvedStream(
            streamUrl = embedUrl,
            isDirectHls = embedUrl.contains(".m3u8"),
            referer = referer,
            serverName = serverName,
            isDirectWebEmbed = !embedUrl.contains(".m3u8")
        )
    }
}
