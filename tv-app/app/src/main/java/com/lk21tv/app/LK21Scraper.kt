package com.lk21tv.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class MediaItem(
    val title: String,
    val url: String,
    val posterUrl: String,
    val rating: String,
    val episodeOrQuality: String
)

data class EpisodeItem(
    val label: String,
    val url: String
)

data class MirrorItem(
    val name: String,
    val embedUrl: String
)

data class MediaDetail(
    val title: String,
    val url: String,
    val posterUrl: String,
    val plot: String,
    val episodes: List<EpisodeItem>,
    val mirrors: List<MirrorItem>,
    val mainEmbedUrl: String?
)

object LK21Scraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchDoc(url: String, referer: String? = null): Document {
        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15000)
            .followRedirects(true)
        if (referer != null) {
            conn.referrer(referer)
        }
        return conn.get()
    }

    private fun fixUrl(baseUrl: String, raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "${baseUrl.trimEnd('/')}$clean"
            else -> "${baseUrl.trimEnd('/')}/$clean"
        }
    }

    fun fetchCatalog(baseUrl: String, pathOrUrl: String): List<MediaItem> {
        val fullUrl = if (pathOrUrl.startsWith("http")) pathOrUrl else fixUrl(baseUrl, pathOrUrl)
        val doc = fetchDoc(fullUrl, baseUrl)

        val cards = doc.select("article.mega-item, div.grid-archive article, div.search-item, div.item")
        val list = mutableListOf<MediaItem>()

        for (card in cards) {
            val item = parseCard(baseUrl, card)
            if (item != null && list.none { it.url == item.url }) {
                list.add(item)
            }
        }
        return list
    }

    private fun parseCard(baseUrl: String, card: Element): MediaItem? {
        val linkEl = card.selectFirst("h1.grid-title a, h2.entry-title a, h2 a, h3 a, .title a, figure a, a[href]")
            ?: return null
        val href = fixUrl(baseUrl, linkEl.attr("href"))
        if (href.isBlank() || href.contains("/genre/") || href.contains("/country/")) return null

        val rawTitle = linkEl.attr("title")
            .ifBlank { card.selectFirst("h1, h2, h3, .title")?.text() ?: linkEl.text() }
            .replace("Nonton ", "", ignoreCase = true)
            .replace("Film ", "", ignoreCase = true)
            .replace("Series ", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .replace("Subtitle Indonesia", "", ignoreCase = true)
            .trim()

        if (rawTitle.isBlank()) return null

        val imgEl = card.selectFirst("figure img, .poster img, img")
        val rawPoster = imgEl?.let {
            it.attr("data-src").ifBlank {
                it.attr("data-lazy-src").ifBlank {
                    it.attr("srcset").substringBefore(" ").ifBlank { it.attr("src") }
                }
            }
        } ?: ""
        val posterUrl = fixUrl(baseUrl, rawPoster)

        val rating = card.selectFirst(".rating, .imdb")?.text()?.trim() ?: ""
        val badge = card.selectFirst(".episode, .last-episode, .quality, span.eps")?.text()?.trim() ?: "HD"

        return MediaItem(
            title = rawTitle,
            url = href,
            posterUrl = posterUrl,
            rating = rating,
            episodeOrQuality = badge
        )
    }

    fun fetchDetail(baseUrl: String, pageUrl: String): MediaDetail {
        val doc = fetchDoc(pageUrl, baseUrl)

        val title = doc.selectFirst("h1.entry-title, div.content-poster h1, h1")?.text()
            ?.replace("Nonton ", "", ignoreCase = true)
            ?.replace("Sub Indo", "", ignoreCase = true)
            ?.trim() ?: "Butiran Cerita"

        val imgEl = doc.selectFirst("div.content-poster img, figure.poster img, .poster img")
        val posterUrl = imgEl?.let {
            fixUrl(baseUrl, it.attr("data-src").ifBlank { it.attr("src") })
        } ?: ""

        val plot = doc.selectFirst("div.synopsis, blockquote, div.entry-content p, .desc")?.text()?.trim()
            ?: "Tiada sinopsis tersedia."

        // Kumpul senarai episod (jika drama/series)
        val episodes = mutableListOf<EpisodeItem>()
        val epLinks = doc.select("div.episode-list a, ul.episodelist a, div.season-list a, a[href*='episode-'], a[href*='season-']")
        epLinks.forEachIndexed { index, el ->
            val epHref = fixUrl(baseUrl, el.attr("href"))
            if (epHref.isNotBlank() && !epHref.endsWith("#")) {
                val label = el.text().trim().ifBlank { "Episod ${index + 1}" }
                if (episodes.none { it.url == epHref }) {
                    episodes.add(EpisodeItem(label = label, url = epHref))
                }
            }
        }

        // Kumpul senarai pelayan (mirror iframe)
        val mirrors = mutableListOf<MirrorItem>()
        doc.select("ul#loadProviders li a, div.player-server a, a[data-url], select.mirror option").forEach { el ->
            val rawMirror = el.attr("data-url").ifBlank { el.attr("value").ifBlank { el.attr("href") } }
            if (rawMirror.isNotBlank() && !rawMirror.startsWith("#") && !rawMirror.startsWith("javascript")) {
                val cleanMirror = fixUrl(baseUrl, rawMirror)
                val serverName = el.text().trim().ifBlank { "Server ${mirrors.size + 1}" }
                if (mirrors.none { it.embedUrl == cleanMirror }) {
                    mirrors.add(MirrorItem(name = serverName, embedUrl = cleanMirror))
                }
            }
        }

        // Cari iframe utama pada halaman
        val mainIframe = doc.selectFirst("iframe#player-iframe, div.player-embed iframe, div#loadProviders iframe, div.player iframe, iframe[src*='player'], iframe[src*='embed']")
        val rawIframeSrc = mainIframe?.let { it.attr("src").ifBlank { it.attr("data-src") } } ?: ""
        val mainEmbedUrl = if (rawIframeSrc.isNotBlank()) fixUrl(baseUrl, rawIframeSrc) else mirrors.firstOrNull()?.embedUrl

        return MediaDetail(
            title = title,
            url = pageUrl,
            posterUrl = posterUrl,
            plot = plot,
            episodes = episodes,
            mirrors = mirrors,
            mainEmbedUrl = mainEmbedUrl
        )
    }
}
