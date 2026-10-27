package com.lk21tv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LK21Provider : MainAPI() {
    override var mainUrl = "https://tv12.lk21official.cc"
    override var name = "LK21 TV Official"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest/page/" to "Series Terbaru",
        "$mainUrl/populer/page/" to "Series Terpopuler",
        "$mainUrl/country/south-korea/page/" to "Drama Korea",
        "$mainUrl/country/china/page/" to "Drama China",
        "$mainUrl/country/united-states/page/" to "Series Barat",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/scifi/page/" to "Sci-Fi & Fantasy",
        "$mainUrl/genre/thriller/page/" to "Thriller & Mystery"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (page == 1) {
            request.data.removeSuffix("page/")
        } else {
            "${request.data}$page/"
        }
        val document = app.get(targetUrl).document
        val items = document.select("article.mega-item, div.grid-archive article, div.item, div.search-item")
            .mapNotNull { it.toSearchResult() }

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
        val titleElement = this.selectFirst("h1.grid-title a, h2.entry-title a, h2 a, h3 a, .title a, figure a")
            ?: return null
        val rawTitle = titleElement.attr("title").ifBlank { titleElement.text() }.trim()
        if (rawTitle.isBlank()) return null

        val href = fixUrl(titleElement.attr("href"))
        val imgEl = this.selectFirst("figure img, .poster img, img")
        val posterUrl = imgEl?.let {
            val rawSrc = it.attr("data-src").ifBlank {
                it.attr("data-lazy-src").ifBlank { it.attr("src") }
            }
            if (rawSrc.startsWith("//")) "https:$rawSrc" else fixUrlNull(rawSrc)
        }

        val qualityText = this.selectFirst(".quality, .label-quality")?.text()
        val episodeBadge = this.selectFirst(".episode, .last-episode, span.eps")?.text()
            ?.filter { it.isDigit() }?.toIntOrNull()

        return newAnimeSearchResponse(rawTitle, href, TvType.TvSeries) {
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
        val searchUrl = "$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(searchUrl).document
        return document.select("div.search-item, article.mega-item, div.grid-archive article, div.item")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, div.content-poster h1, h1")?.text()?.trim()
            ?: return null

        val imgEl = document.selectFirst("div.content-poster img, figure.poster img, .poster img")
        val poster = imgEl?.let {
            val src = it.attr("data-src").ifBlank { it.attr("src") }
            if (src.startsWith("//")) "https:$src" else fixUrlNull(src)
        }

        val plot = document.selectFirst("div.synopsis, blockquote, div.entry-content p, .desc")?.text()?.trim()
        val year = Regex("(19|20)\\d{2}").find(title)?.value?.toIntOrNull()
        val tags = document.select("div.content a[href*='/genre/'], .genx a").map { it.text() }

        // Semak senarai episod (Series / Drama)
        val episodeElements = document.select("div.episode-list a, ul.episodelist a, div.season-list a, a[href*='episode-']")
        val episodes = mutableListOf<Episode>()

        episodeElements.forEachIndexed { idx, epEl ->
            val epHref = fixUrlNull(epEl.attr("href")) ?: return@forEachIndexed
            val epLabel = epEl.text().trim().ifBlank { "Episode ${idx + 1}" }
            val epNum = Regex("(\\d+)").find(epLabel)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (idx + 1)
            val seasonNum = Regex("season-(\\d+)", RegexOption.IGNORE_CASE).find(epHref)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            episodes.add(
                newEpisode(epHref) {
                    this.name = epLabel
                    this.season = seasonNum
                    this.episode = epNum
                }
            )
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinctBy { it.data }) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
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

        // 1. Cari iframe utama pada halaman
        document.select("iframe#player-iframe, div.player-embed iframe, div#loadProviders iframe, iframe").forEach { iframe ->
            val rawSrc = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (rawSrc.isNotBlank()) {
                val cleanUrl = if (rawSrc.startsWith("//")) "https:$rawSrc" else fixUrl(rawSrc)
                loadExtractor(cleanUrl, data, subtitleCallback, callback)
            }
        }

        // 2. Cari senarai mirror pelayan dalam ul#loadProviders / button mirror
        document.select("ul#loadProviders li a, div.player-server a, a[data-url], a[href*='player.']").forEach { server ->
            val serverUrl = server.attr("data-url").ifBlank { server.attr("href") }
            if (serverUrl.isNotBlank() && !serverUrl.startsWith("#") && !serverUrl.startsWith("javascript")) {
                val cleanUrl = if (serverUrl.startsWith("//")) "https:$serverUrl" else fixUrl(serverUrl)
                loadExtractor(cleanUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
