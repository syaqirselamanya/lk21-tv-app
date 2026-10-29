package com.lk21tv.app

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URL

data class MediaItem(
    val title: String,
    val url: String,
    val posterUrl: String,
    val rating: String,
    val episodeOrQuality: String,
    val genre: String = "",
    val isSeries: Boolean = false
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
    val rating: String,
    val quality: String,
    val episodes: List<EpisodeItem>,
    val mirrors: List<MirrorItem>,
    val mainEmbedUrl: String?,
    val genre: String = ""
)

data class ResolvedStream(
    val streamUrl: String,
    val isDirectHls: Boolean,
    val referer: String,
    val serverName: String
)

object LK21Scraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val DEFAULT_THUMB_BASE = "https://poster.assetsy.de/wp-content/uploads/"
    private const val DEFAULT_SEARCH_API = "https://gudangvape.com/"

    private fun fetchDoc(url: String, referer: String? = null): Document {
        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15000)
            .followRedirects(true)
            .ignoreHttpErrors(true)
        if (referer != null) {
            conn.referrer(referer)
        }
        return conn.get()
    }

    private fun fetchJson(url: String, referer: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", referer)
            setRequestProperty("Origin", referer.trimEnd('/'))
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun postFormJson(url: String, formBody: String, referer: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", referer)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        conn.outputStream.use { out ->
            out.write(formBody.toByteArray(Charsets.UTF_8))
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
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

    fun fetchCatalog(baseUrl: String, pathOrUrl: String, page: Int = 1): List<MediaItem> {
        if (pathOrUrl.contains("?s=")) {
            val query = pathOrUrl.substringAfter("?s=").substringBefore("&")
            val searchItems = fetchSearchCatalog(baseUrl, query, page)
            if (searchItems.isNotEmpty()) {
                return searchItems
            }
        }

        val fullUrl = when {
            pathOrUrl.startsWith("http") -> pathOrUrl
            page <= 1 -> fixUrl(baseUrl, pathOrUrl)
            pathOrUrl == "/" -> fixUrl(baseUrl, "/latest/page/$page")
            else -> fixUrl(baseUrl, "${pathOrUrl.trimEnd('/')}/page/$page")
        }

        val doc = fetchDoc(fullUrl, baseUrl)
        val cards = doc.select("article")
        val list = mutableListOf<MediaItem>()

        for (card in cards) {
            val item = parseCard(baseUrl, card)
            if (item != null && list.none { it.url == item.url }) {
                list.add(item)
            }
        }
        return list
    }

    private fun fetchSearchCatalog(baseUrl: String, encodedQuery: String, page: Int): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        try {
            val apiUrl = "${DEFAULT_SEARCH_API}search.php?s=$encodedQuery&page=$page"
            val jsonStr = fetchJson(apiUrl, baseUrl)
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("data") ?: root.optJSONArray("items") ?: return emptyList()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val slug = obj.optString("slug").trimStart('/')
                if (slug.isBlank()) continue

                val rawTitle = obj.optString("title", slug)
                val year = obj.optString("year", "")
                val displayTitle = if (year.isNotBlank() && !rawTitle.contains(year)) {
                    "$rawTitle ($year)"
                } else {
                    rawTitle
                }

                val posterPath = obj.optString("poster", "")
                val posterUrl = when {
                    posterPath.startsWith("http") -> posterPath
                    posterPath.isNotBlank() -> "$DEFAULT_THUMB_BASE${posterPath.trimStart('/')}"
                    else -> ""
                }

                val rating = obj.optString("rating", "").takeIf { it != "0" } ?: ""
                val quality = obj.optString("quality", "")
                val episode = obj.optString("episode", "")
                val isSeries = episode.isNotBlank() && episode != "0"
                val badge = when {
                    isSeries -> "EPS $episode"
                    quality.isNotBlank() -> quality
                    else -> "HD"
                }

                results.add(
                    MediaItem(
                        title = displayTitle,
                        url = fixUrl(baseUrl, "/$slug"),
                        posterUrl = posterUrl,
                        rating = rating,
                        episodeOrQuality = badge,
                        isSeries = isSeries
                    )
                )
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun parseCard(baseUrl: String, card: Element): MediaItem? {
        val linkEl = card.selectFirst("figure a, a[itemprop='url'], h3 a, h2 a, a[href]")
            ?: return null
        val rawHref = linkEl.attr("href").trim()
        if (rawHref.isBlank() || rawHref == "#" || rawHref.contains("/genre/") || rawHref.contains("/country/")) {
            return null
        }
        val href = fixUrl(baseUrl, rawHref)

        val imgEl = card.selectFirst("picture img, figure img, .poster img, img")
        val rawTitle = card.selectFirst("h3.poster-title, .poster-title, figcaption h3, h2, h3")?.text()
            ?.ifBlank { imgEl?.attr("alt") ?: linkEl.attr("title") }
            ?.replace("Nonton series ", "", ignoreCase = true)
            ?.replace("Nonton movie ", "", ignoreCase = true)
            ?.replace("Nonton ", "", ignoreCase = true)
            ?.replace("streaming gratis", "", ignoreCase = true)
            ?.trim() ?: return null

        if (rawTitle.isBlank()) return null

        val yearText = card.selectFirst("span.year")?.text()?.trim() ?: ""
        val fullTitle = if (yearText.isNotBlank() && !rawTitle.contains(yearText)) {
            "$rawTitle ($yearText)"
        } else {
            rawTitle
        }

        var rawPoster = ""
        val jpegSource = card.selectFirst("picture source[type='image/jpeg'], picture source")
        if (jpegSource != null) {
            rawPoster = jpegSource.attr("srcset").ifBlank { jpegSource.attr("data-srcset") }
                .substringBefore(" ")
        }
        if (rawPoster.isBlank() || rawPoster.contains("fallback")) {
            rawPoster = imgEl?.let {
                val src = it.attr("src")
                val dataSrc = it.attr("data-src")
                if (dataSrc.isNotBlank() && !dataSrc.contains("fallback")) dataSrc else src
            } ?: ""
        }
        val posterUrl = if (rawPoster.contains("fallback")) "" else fixUrl(baseUrl, rawPoster)

        val rating = card.selectFirst("span[itemprop='ratingValue'], span.rating")?.text()?.trim() ?: ""
        val epText = card.selectFirst("span.episode")?.text()?.trim() ?: ""
        val labelText = card.selectFirst("span.label")?.text()?.trim() ?: ""
        val durationText = card.selectFirst("span.duration")?.text()?.trim() ?: ""
        val genreText = card.selectFirst("div.genre, meta[itemprop='genre']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim() ?: ""

        val isSeries = epText.isNotBlank() || durationText.startsWith("S.", ignoreCase = true)
        val badge = when {
            epText.isNotBlank() -> epText
            labelText.isNotBlank() -> labelText
            durationText.isNotBlank() -> durationText
            else -> "HD"
        }

        return MediaItem(
            title = fullTitle,
            url = href,
            posterUrl = posterUrl,
            rating = rating,
            episodeOrQuality = badge,
            genre = genreText,
            isSeries = isSeries
        )
    }

    fun fetchDetail(baseUrl: String, pageUrl: String): MediaDetail {
        var currentUrl = pageUrl
        var doc = fetchDoc(currentUrl, baseUrl)

        val redirectBtn = doc.selectFirst("a#openNow")
        if (redirectBtn != null) {
            val targetSeriesUrl = redirectBtn.attr("href").trim()
            if (targetSeriesUrl.startsWith("http")) {
                currentUrl = targetSeriesUrl
                doc = fetchDoc(currentUrl, pageUrl)
            }
        }

        val currentOrigin = try {
            val u = URL(currentUrl)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) {
            baseUrl
        }

        val title = doc.selectFirst("div.movie-info h1, h1.entry-title, h1")?.text()
            ?.replace("Nonton ", "", ignoreCase = true)
            ?.replace("Sub Indo", "", ignoreCase = true)
            ?.trim() ?: "Butiran Cerita"

        val imgEl = doc.selectFirst("div.detail picture img, div.detail img, figure.poster img, .poster img, meta[property='og:image']")
        val posterUrl = imgEl?.let {
            if (it.tagName() == "meta") {
                it.attr("content")
            } else {
                fixUrl(currentOrigin, it.attr("data-src").ifBlank { it.attr("src") })
            }
        } ?: ""

        val plot = doc.selectFirst("div.synopsis, div.meta-info .synopsis, blockquote, .desc")?.text()?.trim()
            ?: doc.selectFirst("meta[name='description']")?.attr("content")
            ?: "Tiada sinopsis tersedia."

        val rating = doc.selectFirst("span.rating-number")?.text()?.trim() ?: ""
        val quality = doc.select("div.info-tag span").joinToString(" • ") { it.text().trim() }.ifBlank { "HD 1080p" }
        val genre = doc.select("div.tag-list a[href*='/genre/'], .genre a").joinToString(", ") { it.text().trim() }
            .ifBlank { doc.selectFirst("meta[itemprop='genre']")?.attr("content") ?: "" }

        // 1. Senarai Episod (jika Series)
        val episodes = mutableListOf<EpisodeItem>()
        val seasonJsonEl = doc.selectFirst("script#season-data")
        if (seasonJsonEl != null) {
            try {
                val seasonObj = JSONObject(seasonJsonEl.data().ifBlank { seasonJsonEl.html() })
                val seasonKeys = seasonObj.keys()
                while (seasonKeys.hasNext()) {
                    val sKey = seasonKeys.next()
                    val epArray = seasonObj.optJSONArray(sKey) ?: continue
                    for (i in 0 until epArray.length()) {
                        val epObj = epArray.optJSONObject(i) ?: continue
                        val slug = epObj.optString("slug").trimStart('/')
                        val sNum = epObj.optInt("s", 1)
                        val epNum = epObj.optInt("episode_no", i + 1)
                        if (slug.isNotBlank()) {
                            episodes.add(
                                EpisodeItem(
                                    label = "S$sNum : Episod $epNum",
                                    url = "$currentOrigin/$slug"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (episodes.isEmpty()) {
            doc.select("ul.episode-list a, div.episode-list a, ul.episodelist a").forEachIndexed { idx, el ->
                val epHref = fixUrl(currentOrigin, el.attr("href"))
                if (epHref.isNotBlank() && !epHref.endsWith("#")) {
                    val label = el.text().trim().ifBlank { "Episod ${idx + 1}" }
                    if (episodes.none { it.url == epHref }) {
                        episodes.add(EpisodeItem(label = label, url = epHref))
                    }
                }
            }
        }

        // 2. Senarai Pelayan Video (P2P, CAST, HYDRAX)
        val mirrors = mutableListOf<MirrorItem>()
        doc.select("ul#player-list li a, select#player-select option, ul#loadProviders li a").forEach { el ->
            val rawMirror = el.attr("data-url").ifBlank { el.attr("value").ifBlank { el.attr("href") } }
            if (rawMirror.isNotBlank() && !rawMirror.startsWith("#") && !rawMirror.startsWith("javascript")) {
                val cleanMirror = fixUrl(currentOrigin, rawMirror)
                val serverName = el.text().trim()
                    .replace("GANTI PLAYER ", "", ignoreCase = true)
                    .ifBlank { el.attr("data-server").uppercase().ifBlank { "Server ${mirrors.size + 1}" } }
                if (mirrors.none { it.embedUrl == cleanMirror }) {
                    mirrors.add(MirrorItem(name = serverName, embedUrl = cleanMirror))
                }
            }
        }

        val mainIframe = doc.selectFirst("iframe#main-player, div.main-player iframe, iframe#player-iframe")
        val rawIframeSrc = mainIframe?.let { it.attr("src").ifBlank { it.attr("data-src") } } ?: ""
        val mainEmbedUrl = if (rawIframeSrc.isNotBlank()) {
            fixUrl(currentOrigin, rawIframeSrc)
        } else {
            mirrors.firstOrNull()?.embedUrl
        }

        if (mainEmbedUrl != null && mirrors.none { it.embedUrl.substringBefore("?") == mainEmbedUrl.substringBefore("?") }) {
            mirrors.add(0, MirrorItem("UTAMA (P2P)", mainEmbedUrl))
        }

        return MediaDetail(
            title = title,
            url = currentUrl,
            posterUrl = posterUrl,
            plot = plot,
            rating = rating,
            quality = quality,
            episodes = episodes,
            mirrors = mirrors,
            mainEmbedUrl = mainEmbedUrl,
            genre = genre
        )
    }

    /**
     * Nyah-bungkus (unwrap) URL videonode.de terus kepada strim HLS (.m3u8) atau URL pemain tulen
     * supaya tidak dihalakan (redirect) ke laman web lk21.de!
     */
    fun resolveStream(embedUrl: String, serverName: String = "P2P"): ResolvedStream {
        try {
            val cleanUrl = embedUrl.substringBefore("?")
            // Contoh: https://videonode.de/iframe3/p2p/vl4biPHM2Q871_aFIsMpeA
            val regex = Regex("https?://([^/]+)/iframe\\d*/([^/]+)/([^/?#]+)")
            val match = regex.find(cleanUrl)
            if (match != null) {
                val nodeDomain = match.groupValues[1]
                val hostType = match.groupValues[2]
                val videoId = match.groupValues[3]

                val apiUrl = "https://$nodeDomain/api.php"
                val apiRes = postFormJson(apiUrl, "host=$hostType&id=$videoId", embedUrl)
                val realEmbedUrl = JSONObject(apiRes).optString("embedUrl", "")

                if (realEmbedUrl.isNotBlank()) {
                    // Jika ia adalah pelayan playcdn.de (P2P), ekstrak terus fail .m3u8 daripada /verify/{slug}!
                    if (realEmbedUrl.contains("playcdn.")) {
                        val u = URL(realEmbedUrl)
                        val hostOrigin = "${u.protocol}://${u.host}"
                        val slug = u.path.trim('/').substringAfterLast('/')
                        if (slug.isNotBlank()) {
                            val verifyJson = fetchJson("$hostOrigin/verify/$slug", realEmbedUrl)
                            val verifyObj = JSONObject(verifyJson)
                            val m3u8Url = verifyObj.optString("fileUrl", "")
                            if (m3u8Url.isNotBlank()) {
                                return ResolvedStream(
                                    streamUrl = m3u8Url,
                                    isDirectHls = true,
                                    referer = "$hostOrigin/",
                                    serverName = serverName
                                )
                            }
                        }
                    }

                    // Untuk pelayan lain (contoh CAST -> mfw09.org, HYDRAX -> abysscdn.com)
                    return ResolvedStream(
                        streamUrl = realEmbedUrl,
                        isDirectHls = false,
                        referer = "https://$nodeDomain/",
                        serverName = serverName
                    )
                }
            }
        } catch (_: Exception) {
        }

        return ResolvedStream(
            streamUrl = embedUrl,
            isDirectHls = embedUrl.contains(".m3u8"),
            referer = "https://tv12.lk21official.cc/",
            serverName = serverName
        )
    }
}
