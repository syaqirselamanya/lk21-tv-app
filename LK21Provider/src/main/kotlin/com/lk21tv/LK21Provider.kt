package com.lk21tv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element

class LK21Provider : MainAPI() {
    override var mainUrl = "https://tv12.lk21official.cc"
    override var name = "LK21 TV Official"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest/page/" to "Movie Terbaru",
        "$mainUrl/top-movie-today/page/" to "Unggulan Hari Ini",
        "$mainUrl/populer/page/" to "Movie Populer",
        "$mainUrl/latest-series/page/" to "Series Terupdate",
        "$mainUrl/year/2026/page/" to "Movie 2026",
        "$mainUrl/country/usa/page/" to "Movie Barat",
        "$mainUrl/country/south-korea/page/" to "Korea",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/sci-fi/page/" to "Sci-Fi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page == 1) {
            request.data.removeSuffix("/page/")
        } else {
            "${request.data}$page"
        }
        val document = app.get(targetUrl).document
        val items = document.select("article")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkEl = this.selectFirst("figure a, a[itemprop='url'], h3 a, a[href]") ?: return null
        val rawHref = linkEl.attr("href").trim()
        if (rawHref.isBlank() || rawHref == "#" || rawHref.contains("/genre/") || rawHref.contains("/country/")) {
            return null
        }
        val href = fixUrl(rawHref)

        val imgEl = this.selectFirst("picture img, figure img, img")
        val rawTitle = this.selectFirst("h3.poster-title, .poster-title, figcaption h3, h2, h3")?.text()
            ?.ifBlank { imgEl?.attr("alt") ?: linkEl.attr("title") }
            ?.trim() ?: return null
        if (rawTitle.isBlank()) return null

        val jpegSource = this.selectFirst("picture source[type='image/jpeg'], picture source")
        var rawPoster = jpegSource?.let {
            it.attr("srcset").ifBlank { it.attr("data-srcset") }.substringBefore(" ")
        } ?: ""
        if (rawPoster.isBlank() || rawPoster.contains("fallback")) {
            rawPoster = imgEl?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            } ?: ""
        }
        val posterUrl = if (rawPoster.contains("fallback")) null else fixUrlNull(rawPoster)

        val qualityText = this.selectFirst("span.label")?.text()
        val episodeBadge = this.selectFirst("span.episode strong, span.episode")?.text()
            ?.filter { it.isDigit() }?.toIntOrNull()

        return newAnimeSearchResponse(rawTitle, href, TvType.Movie) {
            this.posterUrl = posterUrl
            if (qualityText != null) {
                addQuality(qualityText)
            }
            if (episodeBadge != null) {
                addSub(episodeBadge)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val apiUrl = "https://gudangvape.com/search.php?s=$encoded&page=1"
        val resText = app.get(apiUrl, referer = mainUrl).text
        val list = mutableListOf<SearchResponse>()

        try {
            val root = JSONObject(resText)
            val arr = root.optJSONArray("data") ?: root.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val slug = obj.optString("slug").trimStart('/')
                    if (slug.isBlank()) continue
                    val title = obj.optString("title", slug)
                    val posterPath = obj.optString("poster", "")
                    val posterUrl = if (posterPath.isNotBlank()) {
                        "https://poster.assetsy.de/wp-content/uploads/${posterPath.trimStart('/')}"
                    } else null
                    list.add(
                        newMovieSearchResponse(title, "$mainUrl/$slug", TvType.Movie) {
                            this.posterUrl = posterUrl
                        }
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse? {
        var currentUrl = url
        var document = app.get(currentUrl, referer = mainUrl).document

        val redirectBtn = document.selectFirst("a#openNow")
        if (redirectBtn != null) {
            val nextUrl = redirectBtn.attr("href").trim()
            if (nextUrl.startsWith("http")) {
                currentUrl = nextUrl
                document = app.get(currentUrl, referer = url).document
            }
        }

        val currentOrigin = try {
            val u = java.net.URL(currentUrl)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) {
            mainUrl
        }

        val title = document.selectFirst("div.movie-info h1, h1.entry-title, h1")?.text()?.trim()
            ?: return null

        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
        val plot = document.selectFirst("div.synopsis, div.meta-info .synopsis, blockquote")?.text()?.trim()
        val year = Regex("(19|20)\\d{2}").find(title)?.value?.toIntOrNull()
        val tags = document.select("div.tag-list a[href*='/genre/']").map { it.text() }

        val episodes = mutableListOf<Episode>()
        val seasonJsonEl = document.selectFirst("script#season-data")
        if (seasonJsonEl != null) {
            try {
                val seasonObj = JSONObject(seasonJsonEl.data().ifBlank { seasonJsonEl.html() })
                val keys = seasonObj.keys()
                while (keys.hasNext()) {
                    val sKey = keys.next()
                    val epArray = seasonObj.optJSONArray(sKey) ?: continue
                    for (i in 0 until epArray.length()) {
                        val epObj = epArray.optJSONObject(i) ?: continue
                        val slug = epObj.optString("slug").trimStart('/')
                        val sNum = epObj.optInt("s", 1)
                        val epNum = epObj.optInt("episode_no", i + 1)
                        if (slug.isNotBlank()) {
                            episodes.add(
                                newEpisode("$currentOrigin/$slug") {
                                    this.name = "Season $sNum Episode $epNum"
                                    this.season = sNum
                                    this.episode = epNum
                                }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, currentUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, currentUrl, TvType.Movie, currentUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = mainUrl).document

        document.select("iframe#main-player, div.main-player iframe, ul#player-list li a, select#player-select option").forEach { el ->
            val rawUrl = el.attr("data-url").ifBlank { el.attr("src").ifBlank { el.attr("value").ifBlank { el.attr("href") } } }
            if (rawUrl.isNotBlank() && !rawUrl.startsWith("#")) {
                val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else fixUrl(rawUrl)
                loadExtractor(cleanUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
