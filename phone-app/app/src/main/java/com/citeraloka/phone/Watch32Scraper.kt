package com.citeraloka.phone

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class HeroSpotlightData(
    val title: String,
    val backdropUrl: String,
    val logoUrl: String,
    val posterUrl: String,
    val metaLine: String,
    val statusBadge: String,
    val qualityBadge: String,
    val rating: String,
    val sourceBadge: String,
    val plot: String
)

object Watch32Scraper {

    const val SOURCE_WATCH32 = "WATCH32"
    const val WATCH32_ORIGIN = "https://watch32.site"
    private const val TMDB_API_BASE = "https://api.themoviedb.org/3"
    private const val TMDB_API_KEY = "e1a8efff4415028c5c266b3fcd50db6e"
    private const val IMG_POSTER = "https://image.tmdb.org/t/p/w500"
    private const val IMG_BACKDROP = "https://image.tmdb.org/t/p/w1280"
    private const val IMG_LOGO = "https://image.tmdb.org/t/p/w500"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val heroCache = ConcurrentHashMap<String, HeroSpotlightData>()

    private val GENRE_MAP = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics"
    )

    private val WATCH32_EMBED_HOSTS = listOf(
        "cinezo.live",
        "cinesrc.st",
        "vidlink.pro",
        "videasy.net",
        "peestream.in",
        "flaxmovies.xyz",
        "moviesapi.to",
        "moviesapi.club",
        "autoembed.cc",
        "vidsrc.to",
        "vidsrc.me",
        "vidsrc.sh",
        "vidsrc-embed.ru",
        "vixsrc.to",
        "vidzee.wtf",
        "multiembed.mov"
    )

    fun isWatch32Uri(url: String): Boolean = url.startsWith("watch32://")

    fun isWatch32EmbedUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return WATCH32_EMBED_HOSTS.any { lower.contains(it) }
    }

    private fun fetchJson(url: String): JSONObject? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Referer", "$WATCH32_ORIGIN/")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }

    fun fetchCatalog(pathOrUrl: String, page: Int = 1): List<MediaItem> {
        if (pathOrUrl.contains("?s=")) {
            val rawEncoded = pathOrUrl.substringAfter("?s=").substringBefore("&")
            val decoded = try {
                URLDecoder.decode(rawEncoded, "UTF-8")
            } catch (_: Exception) {
                rawEncoded
            }
            return searchCatalog(decoded, page)
        }

        val endpoint = when {
            pathOrUrl == "/latest" ->
                "$TMDB_API_BASE/trending/all/day?api_key=$TMDB_API_KEY&page=$page"
            pathOrUrl == "/" ->
                "$TMDB_API_BASE/trending/all/week?api_key=$TMDB_API_KEY&page=$page"
            pathOrUrl == "/top-movie-today" ->
                "$TMDB_API_BASE/trending/movie/day?api_key=$TMDB_API_KEY&page=$page"
            pathOrUrl == "/populer" ->
                "$TMDB_API_BASE/movie/popular?api_key=$TMDB_API_KEY&page=$page"
            pathOrUrl == "/latest-series" ->
                "$TMDB_API_BASE/trending/tv/week?api_key=$TMDB_API_KEY&page=$page"
            pathOrUrl.startsWith("/year/") -> {
                val yr = pathOrUrl.substringAfter("/year/").substringBefore("/").ifBlank { "2026" }
                "$TMDB_API_BASE/discover/movie?api_key=$TMDB_API_KEY&primary_release_year=$yr&sort_by=popularity.desc&page=$page"
            }
            pathOrUrl.contains("south-korea") ->
                "$TMDB_API_BASE/discover/tv?api_key=$TMDB_API_KEY&with_origin_country=KR&sort_by=popularity.desc&page=$page"
            pathOrUrl.contains("/country/usa") ->
                "$TMDB_API_BASE/discover/movie?api_key=$TMDB_API_KEY&with_origin_country=US&sort_by=popularity.desc&page=$page"
            pathOrUrl.contains("/genre/action") ->
                "$TMDB_API_BASE/discover/movie?api_key=$TMDB_API_KEY&with_genres=28&sort_by=popularity.desc&page=$page"
            pathOrUrl.contains("/genre/horror") ->
                "$TMDB_API_BASE/discover/movie?api_key=$TMDB_API_KEY&with_genres=27&sort_by=popularity.desc&page=$page"
            pathOrUrl.contains("/genre/sci-fi") ->
                "$TMDB_API_BASE/discover/movie?api_key=$TMDB_API_KEY&with_genres=878&sort_by=popularity.desc&page=$page"
            else ->
                "$TMDB_API_BASE/trending/all/week?api_key=$TMDB_API_KEY&page=$page"
        }

        val defaultType = when {
            endpoint.contains("/tv") -> "tv"
            endpoint.contains("/movie") -> "movie"
            else -> ""
        }

        val json = fetchJson(endpoint) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return parseResultArray(results, defaultType)
    }

    fun searchCatalog(query: String, page: Int = 1): List<MediaItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val url = "$TMDB_API_BASE/search/multi?api_key=$TMDB_API_KEY&query=$encoded&page=$page&include_adult=false"
        val json = fetchJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return parseResultArray(results, "")
    }

    private fun parseResultArray(results: JSONArray, fallbackType: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val mediaType = obj.optString("media_type", fallbackType)
            if (mediaType != "movie" && mediaType != "tv") continue
            val isTv = mediaType == "tv"

            val id = obj.optInt("id", 0)
            if (id <= 0) continue

            val rawTitle = if (isTv) {
                obj.optString("name").ifBlank { obj.optString("original_name") }
            } else {
                obj.optString("title").ifBlank { obj.optString("original_title") }
            }.trim()
            if (rawTitle.isBlank()) continue

            val rawDate = if (isTv) obj.optString("first_air_date") else obj.optString("release_date")
            val year = rawDate.take(4)
            val displayTitle = if (year.length == 4 && !rawTitle.contains(year)) {
                "$rawTitle ($year)"
            } else {
                rawTitle
            }

            val posterPath = obj.optString("poster_path", "")
            val backdropPath = obj.optString("backdrop_path", "")
            val posterUrl = when {
                posterPath.isNotBlank() && posterPath != "null" -> "$IMG_POSTER$posterPath"
                backdropPath.isNotBlank() && backdropPath != "null" -> "$IMG_BACKDROP$backdropPath"
                else -> ""
            }
            val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") {
                "$IMG_BACKDROP$backdropPath"
            } else {
                posterUrl
            }

            val voteAvg = obj.optDouble("vote_average", 0.0)
            val rating = if (voteAvg > 0.1) String.format(Locale.US, "%.1f", voteAvg) else ""

            val genreIds = obj.optJSONArray("genre_ids")
            val genreNames = mutableListOf<String>()
            if (genreIds != null) {
                for (g in 0 until genreIds.length()) {
                    GENRE_MAP[genreIds.optInt(g)]?.let { genreNames.add(it) }
                }
            }
            val genreStr = genreNames.take(3).joinToString(", ")

            val overview = obj.optString("overview", "").trim()
            val formattedDate = formatNiceDate(rawDate)

            items.add(
                MediaItem(
                    title = displayTitle,
                    url = if (isTv) "watch32://tv/$id" else "watch32://movie/$id",
                    posterUrl = posterUrl,
                    rating = rating,
                    episodeOrQuality = if (isTv) "W32 • SERIES" else "W32 • HD",
                    genre = genreStr,
                    isSeries = isTv,
                    source = SOURCE_WATCH32,
                    backdropUrl = backdropUrl,
                    logoUrl = "",
                    plot = overview,
                    releaseInfo = formattedDate,
                    statusBadge = if (isTv) "SERIES" else "RELEASED",
                    tmdbId = id
                )
            )
        }
        return items
    }

    fun buildMirrors(
        tmdbId: Int,
        isSeries: Boolean,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String = ""
    ): List<MirrorItem> {
        val list = mutableListOf<MirrorItem>()
        if (isSeries) {
            list.add(MirrorItem("W32 • Cinezo (No Ads)", "https://player.cinezo.live/embed/tv/$tmdbId/$season/$episode"))
            list.add(MirrorItem("W32 • CineSrc (No Ads)", "https://cinesrc.st/embed/tv/$tmdbId?s=$season&e=$episode"))
            list.add(MirrorItem("W32 • VidLink HD", "https://vidlink.pro/tv/$tmdbId/$season/$episode?autoplay=true&primaryColor=00D4B2"))
            list.add(MirrorItem("W32 • VidEasy 4K", "https://player.videasy.net/tv/$tmdbId/$season/$episode?color=00D4B2"))
            list.add(MirrorItem("W32 • PeeStream (No Ads)", "https://peestream.in/embed/?tmdbId=$tmdbId&type=show&season=$season&episode=$episode"))
            list.add(MirrorItem("W32 • FlaxMovies (No Ads)", "https://flaxmovies.xyz/embed/tv/$tmdbId/$season/$episode"))
            list.add(MirrorItem("W32 • MoviesAPI (No Ads)", "https://moviesapi.to/tv/$tmdbId-$season-$episode"))
            list.add(MirrorItem("W32 • AutoEmbed", "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"))
            list.add(MirrorItem("W32 • VidSrc", "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"))
            list.add(MirrorItem("W32 • VixSrc", "https://vixsrc.to/tv/$tmdbId/$season/$episode"))
            if (imdbId.isNotBlank()) {
                list.add(MirrorItem("W32 • VidSrc Embed", "https://vidsrc.sh/embed/tv/$imdbId/$season/$episode"))
            }
        } else {
            list.add(MirrorItem("W32 • Cinezo (No Ads)", "https://player.cinezo.live/embed/movie/$tmdbId"))
            list.add(MirrorItem("W32 • CineSrc (No Ads)", "https://cinesrc.st/embed/movie/$tmdbId"))
            list.add(MirrorItem("W32 • VidLink HD", "https://vidlink.pro/movie/$tmdbId?autoplay=true&primaryColor=00D4B2"))
            list.add(MirrorItem("W32 • VidEasy 4K", "https://player.videasy.net/movie/$tmdbId?color=00D4B2"))
            list.add(MirrorItem("W32 • PeeStream (No Ads)", "https://peestream.in/embed/?tmdbId=$tmdbId&type=movie"))
            list.add(MirrorItem("W32 • FlaxMovies (No Ads)", "https://flaxmovies.xyz/embed/movie/$tmdbId"))
            list.add(MirrorItem("W32 • MoviesAPI (No Ads)", "https://moviesapi.to/movie/$tmdbId"))
            list.add(MirrorItem("W32 • AutoEmbed", "https://player.autoembed.cc/embed/movie/$tmdbId"))
            list.add(MirrorItem("W32 • VidSrc", "https://vidsrc.to/embed/movie/$tmdbId"))
            list.add(MirrorItem("W32 • VixSrc", "https://vixsrc.to/movie/$tmdbId"))
            if (imdbId.isNotBlank()) {
                list.add(MirrorItem("W32 • VidSrc Embed", "https://vidsrc.sh/embed/movie/$imdbId"))
            }
        }
        return list
    }

    /**
     * Muat turun butiran penuh untuk watch32://movie/{id}, watch32://tv/{id}, atau watch32://tv/{id}/{s}/{e}
     */
    fun fetchDetail(watch32Uri: String): MediaDetail {
        val clean = watch32Uri.removePrefix("watch32://").trim('/')
        val parts = clean.split("/")
        val type = parts.getOrNull(0) ?: "movie"
        val isTv = type == "tv"
        val tmdbId = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val targetSeason = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val targetEpisode = parts.getOrNull(3)?.toIntOrNull() ?: 1
        val hasExplicitEpisode = parts.size >= 4

        val detailUrl = "$TMDB_API_BASE/${if (isTv) "tv" else "movie"}/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=images,external_ids&include_image_language=en,null"
        val json = fetchJson(detailUrl) ?: throw IllegalStateException("Gagal berhubung ke pelayan Watch32")

        val rawTitle = if (isTv) {
            json.optString("name").ifBlank { json.optString("original_name") }
        } else {
            json.optString("title").ifBlank { json.optString("original_title") }
        }
        val rawDate = if (isTv) json.optString("first_air_date") else json.optString("release_date")
        val year = rawDate.take(4)
        val displayTitle = if (year.length == 4 && !rawTitle.contains(year)) "$rawTitle ($year)" else rawTitle

        val posterPath = json.optString("poster_path", "")
        val backdropPath = json.optString("backdrop_path", "")
        val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "$IMG_POSTER$posterPath" else ""
        val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") "$IMG_BACKDROP$backdropPath" else posterUrl
        val logoUrl = extractBestLogoUrl(json.optJSONObject("images"))

        val plot = json.optString("overview", "").ifBlank { "Tiada sinopsis tersedia." }
        val voteAvg = json.optDouble("vote_average", 0.0)
        val rating = if (voteAvg > 0.1) String.format(Locale.US, "%.1f", voteAvg) else ""
        val imdbId = json.optJSONObject("external_ids")?.optString("imdb_id", "") ?: json.optString("imdb_id", "")

        val genresArr = json.optJSONArray("genres")
        val genreList = mutableListOf<String>()
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                genresArr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { genreList.add(it) }
            }
        }
        val genreStr = genreList.joinToString(", ")

        val runtimeText = if (isTv) {
            val sCount = json.optInt("number_of_seasons", 1)
            "WATCH32 • $sCount MUSIM • HD 1080p"
        } else {
            val mins = json.optInt("runtime", 0)
            val dur = if (mins > 0) formatRuntime(mins) else "HD 1080p"
            "WATCH32 • $dur • BEBAS IKLAN"
        }

        val episodes = mutableListOf<EpisodeItem>()
        if (isTv) {
            val seasonsArr = json.optJSONArray("seasons")
            val validSeasonNums = mutableListOf<Int>()
            if (seasonsArr != null) {
                for (i in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.optJSONObject(i) ?: continue
                    val sNum = sObj.optInt("season_number", 0)
                    val epCount = sObj.optInt("episode_count", 0)
                    if (sNum >= 1 && epCount > 0) {
                        validSeasonNums.add(sNum)
                    }
                }
            }
            if (validSeasonNums.isEmpty()) validSeasonNums.add(1)

            // Muat turun senarai episod bagi setiap musim (hadkan kepada 8 musim terkini supaya pantas)
            val seasonsToLoad = if (validSeasonNums.size > 8) validSeasonNums.takeLast(8) else validSeasonNums
            for (sNum in seasonsToLoad) {
                val seasonJson = fetchJson("$TMDB_API_BASE/tv/$tmdbId/season/$sNum?api_key=$TMDB_API_KEY")
                val epsArr = seasonJson?.optJSONArray("episodes")
                if (epsArr != null && epsArr.length() > 0) {
                    for (eIdx in 0 until epsArr.length()) {
                        val epObj = epsArr.optJSONObject(eIdx) ?: continue
                        val eNum = epObj.optInt("episode_number", eIdx + 1)
                        val epName = epObj.optString("name", "").trim()
                        val epOverview = epObj.optString("overview", "").trim()
                        val stillPath = epObj.optString("still_path", "")
                        val stillUrl = if (stillPath.isNotBlank() && stillPath != "null") {
                            "$IMG_POSTER$stillPath"
                        } else {
                            backdropUrl
                        }
                        val epRuntimeMins = epObj.optInt("runtime", 0)
                        val epRuntime = if (epRuntimeMins > 0) "${epRuntimeMins}m" else ""
                        val epAirDate = formatNiceDate(epObj.optString("air_date", ""))
                        val cleanEpTitle = if (epName.isNotBlank() && !epName.equals("Episode $eNum", ignoreCase = true)) {
                            epName
                        } else {
                            "Episod $eNum"
                        }
                        val epLabel = if (epName.isNotBlank() && !epName.equals("Episode $eNum", ignoreCase = true)) {
                            "S$sNum E$eNum • $epName"
                        } else {
                            "S$sNum : Episod $eNum"
                        }
                        episodes.add(
                            EpisodeItem(
                                label = epLabel,
                                url = "watch32://tv/$tmdbId/$sNum/$eNum",
                                seasonNumber = sNum,
                                episodeNumber = eNum,
                                title = cleanEpTitle,
                                overview = epOverview,
                                stillUrl = stillUrl,
                                runtime = epRuntime,
                                airDate = epAirDate
                            )
                        )
                    }
                }
            }
        }

        val mirrors = buildMirrors(
            tmdbId = tmdbId,
            isSeries = isTv,
            season = targetSeason,
            episode = targetEpisode,
            imdbId = imdbId
        )

        val mainEmbed = if (!isTv || hasExplicitEpisode) {
            mirrors.firstOrNull()?.embedUrl
        } else {
            null
        }

        return MediaDetail(
            title = if (hasExplicitEpisode) "$displayTitle • S$targetSeason E$targetEpisode" else displayTitle,
            url = watch32Uri,
            posterUrl = posterUrl,
            plot = plot,
            rating = rating,
            quality = runtimeText,
            episodes = episodes,
            mirrors = mirrors,
            mainEmbedUrl = mainEmbed,
            genre = genreStr,
            source = SOURCE_WATCH32,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl
        )
    }

    /**
     * Auto-Fallback: Cari padanan cerita di Watch32 apabila pelayan LK21 disekat / gagal / tiada strim
     */
    fun findFallbackDetail(rawTitle: String, episodeLabel: String = "", preferSeries: Boolean = false): MediaDetail? {
        val (cleanTitle, yearHint) = extractCleanTitleAndYear(rawTitle)
        if (cleanTitle.isBlank()) return null

        val match = searchBestTmdbMatch(cleanTitle, yearHint, preferSeries || episodeLabel.isNotBlank())
            ?: return null

        val (tmdbId, isTv) = match
        return if (isTv) {
            val (sNum, eNum) = parseSeasonEpisode(episodeLabel)
            val uri = if (episodeLabel.isNotBlank()) {
                "watch32://tv/$tmdbId/$sNum/$eNum"
            } else {
                "watch32://tv/$tmdbId"
            }
            try {
                fetchDetail(uri)
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                fetchDetail("watch32://movie/$tmdbId")
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Sediakan senarai mirror sandaran Watch32 untuk disuntik ke dalam cerita LK21
     * supaya pengguna boleh tukar terus ke server Watch32 dalam pemain video!
     */
    fun findBackupMirrorsForLk21(
        rawTitle: String,
        episodeLabel: String = "",
        isSeries: Boolean = false
    ): List<MirrorItem> {
        val (cleanTitle, yearHint) = extractCleanTitleAndYear(rawTitle)
        if (cleanTitle.isBlank()) return emptyList()
        val match = searchBestTmdbMatch(cleanTitle, yearHint, isSeries || episodeLabel.isNotBlank())
            ?: return emptyList()
        val (tmdbId, isTv) = match
        val (sNum, eNum) = parseSeasonEpisode(episodeLabel)
        return buildMirrors(tmdbId, isTv, sNum, eNum).take(5)
    }

    /**
     * Lengkapkan butiran Siri TV LK21 dengan 16:9 Backdrop, Official ClearLogo PNG,
     * serta gambar babak (still_path), tajuk sebenar, durasi, dan sinopsis setiap episod daripada TMDB!
     */
    fun enrichSeriesDetail(detail: MediaDetail): MediaDetail {
        if (detail.source == SOURCE_WATCH32) return detail
        val (cleanTitle, yearHint) = extractCleanTitleAndYear(detail.title)
        if (cleanTitle.isBlank()) return detail

        val match = searchBestTmdbMatch(cleanTitle, yearHint, preferSeries = true) ?: return detail
        val (tmdbId, isTv) = match
        if (!isTv || tmdbId <= 0) return detail

        val tvJson = fetchJson("$TMDB_API_BASE/tv/$tmdbId?api_key=$TMDB_API_KEY&append_to_response=images&include_image_language=en,null")
            ?: return detail

        val backdropPath = tvJson.optString("backdrop_path", "")
        val posterPath = tvJson.optString("poster_path", "")
        val enrichedBackdrop = when {
            backdropPath.isNotBlank() && backdropPath != "null" -> "$IMG_BACKDROP$backdropPath"
            detail.backdropUrl.isNotBlank() -> detail.backdropUrl
            else -> detail.posterUrl
        }
        val enrichedPoster = detail.posterUrl.ifBlank {
            if (posterPath.isNotBlank() && posterPath != "null") "$IMG_POSTER$posterPath" else enrichedBackdrop
        }
        val enrichedLogo = extractBestLogoUrl(tvJson.optJSONObject("images"))
        val tmdbPlot = tvJson.optString("overview", "").trim()
        val enrichedPlot = if (detail.plot.isBlank() || detail.plot == "Tiada sinopsis tersedia.") {
            tmdbPlot.ifBlank { detail.plot }
        } else {
            detail.plot
        }

        // Kumpulkan data episod TMDB mengikut musim yang wujud dalam detail.episodes
        val uniqueSeasons = detail.episodes.map { it.seasonNumber.coerceAtLeast(1) }.distinct().take(8)
        val tmdbEpisodeMap = HashMap<String, EpisodeItem>()
        for (sNum in uniqueSeasons) {
            val sJson = fetchJson("$TMDB_API_BASE/tv/$tmdbId/season/$sNum?api_key=$TMDB_API_KEY") ?: continue
            val epsArr = sJson.optJSONArray("episodes") ?: continue
            for (i in 0 until epsArr.length()) {
                val epObj = epsArr.optJSONObject(i) ?: continue
                val eNum = epObj.optInt("episode_number", i + 1)
                val epName = epObj.optString("name", "").trim()
                val epOverview = epObj.optString("overview", "").trim()
                val stillPath = epObj.optString("still_path", "")
                val stillUrl = if (stillPath.isNotBlank() && stillPath != "null") "$IMG_POSTER$stillPath" else enrichedBackdrop
                val epRuntimeMins = epObj.optInt("runtime", 0)
                val epRuntime = if (epRuntimeMins > 0) "${epRuntimeMins}m" else ""
                val epAirDate = formatNiceDate(epObj.optString("air_date", ""))
                tmdbEpisodeMap["${sNum}_$eNum"] = EpisodeItem(
                    label = "",
                    url = "",
                    seasonNumber = sNum,
                    episodeNumber = eNum,
                    title = epName,
                    overview = epOverview,
                    stillUrl = stillUrl,
                    runtime = epRuntime,
                    airDate = epAirDate
                )
            }
        }

        val enrichedEpisodes = detail.episodes.map { ep ->
            val meta = tmdbEpisodeMap["${ep.seasonNumber}_${ep.episodeNumber}"]
            if (meta != null) {
                val realTitle = if (meta.title.isNotBlank() && !meta.title.equals("Episode ${ep.episodeNumber}", ignoreCase = true)) {
                    meta.title
                } else {
                    ep.title.ifBlank { "Episod ${ep.episodeNumber}" }
                }
                val newLabel = if (meta.title.isNotBlank() && !meta.title.equals("Episode ${ep.episodeNumber}", ignoreCase = true)) {
                    "S${ep.seasonNumber} E${ep.episodeNumber} • ${meta.title}"
                } else {
                    ep.label
                }
                ep.copy(
                    label = newLabel,
                    title = realTitle,
                    overview = meta.overview,
                    stillUrl = meta.stillUrl,
                    runtime = meta.runtime,
                    airDate = meta.airDate
                )
            } else {
                ep.copy(stillUrl = ep.stillUrl.ifBlank { enrichedBackdrop })
            }
        }

        return detail.copy(
            posterUrl = enrichedPoster,
            backdropUrl = enrichedBackdrop,
            logoUrl = enrichedLogo,
            plot = enrichedPlot,
            episodes = enrichedEpisodes
        )
    }

    /**
     * Lengkapkan maklumat Pinned Live Hero Spotlight (16:9 Backdrop + Official ClearLogo PNG + Durasi + Sinopsis)
     * Berfungsi untuk kedua-dua item WATCH32 dan item LK21 dengan cache memori pantas!
     */
    fun enrichHeroSpotlight(item: MediaItem): HeroSpotlightData {
        val cacheKey = item.url.ifBlank { item.title }
        heroCache[cacheKey]?.let { return it }

        var resolvedTmdbId = item.tmdbId
        var resolvedIsTv = item.isSeries

        if (resolvedTmdbId <= 0) {
            val (cleanTitle, yearHint) = extractCleanTitleAndYear(item.title)
            val match = searchBestTmdbMatch(cleanTitle, yearHint, item.isSeries)
            if (match != null) {
                resolvedTmdbId = match.first
                resolvedIsTv = match.second
            }
        }

        if (resolvedTmdbId > 0) {
            val detailUrl = "$TMDB_API_BASE/${if (resolvedIsTv) "tv" else "movie"}/$resolvedTmdbId?api_key=$TMDB_API_KEY&append_to_response=images&include_image_language=en,null"
            val json = fetchJson(detailUrl)
            if (json != null) {
                val backdropPath = json.optString("backdrop_path", "")
                val posterPath = json.optString("poster_path", "")
                val backdropUrl = when {
                    backdropPath.isNotBlank() && backdropPath != "null" -> "$IMG_BACKDROP$backdropPath"
                    item.backdropUrl.isNotBlank() -> item.backdropUrl
                    else -> item.posterUrl
                }
                val posterUrl = when {
                    item.posterUrl.isNotBlank() -> item.posterUrl
                    posterPath.isNotBlank() && posterPath != "null" -> "$IMG_POSTER$posterPath"
                    else -> backdropUrl
                }
                val logoUrl = extractBestLogoUrl(json.optJSONObject("images"))

                val genresArr = json.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArr != null) {
                    for (i in 0 until genresArr.length()) {
                        genresArr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { genres.add(it) }
                    }
                }
                val genrePart = genres.take(3).joinToString(" • ").ifBlank {
                    item.genre.replace(",", " •")
                }

                val durationPart = if (resolvedIsTv) {
                    val sCount = json.optInt("number_of_seasons", 0)
                    if (sCount > 0) "$sCount Season${if (sCount > 1) "s" else ""}" else ""
                } else {
                    val mins = json.optInt("runtime", 0)
                    if (mins > 0) formatRuntime(mins) else ""
                }

                val rawDate = if (resolvedIsTv) json.optString("first_air_date") else json.optString("release_date")
                val datePart = formatNiceDate(rawDate)

                val metaParts = listOf(genrePart, durationPart, datePart).filter { it.isNotBlank() }
                val metaLine = metaParts.joinToString("  •  ").ifBlank { "HD Streaming  •  Bebas Iklan" }

                val rawStatus = json.optString("status", "").uppercase(Locale.ROOT)
                val statusBadge = when {
                    rawStatus.contains("RETURN") -> "ONGOING"
                    rawStatus.contains("RELEASED") -> "RELEASED"
                    rawStatus.contains("ENDED") -> "ENDED"
                    resolvedIsTv -> "SERIES"
                    else -> "RELEASED"
                }

                val voteAvg = json.optDouble("vote_average", 0.0)
                val rating = item.rating.ifBlank {
                    if (voteAvg > 0.1) String.format(Locale.US, "%.1f", voteAvg) else ""
                }

                val plot = json.optString("overview", "").trim()
                    .ifBlank { item.plot }
                    .ifBlank { "Tonton penstriman definisi tinggi bebas iklan menerusi enjin pintar CiteraLoka." }

                val enriched = HeroSpotlightData(
                    title = item.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { item.title },
                    backdropUrl = backdropUrl,
                    logoUrl = logoUrl,
                    posterUrl = posterUrl,
                    metaLine = metaLine,
                    statusBadge = statusBadge,
                    qualityBadge = item.episodeOrQuality.replace("W32 • ", "").ifBlank { "HD" },
                    rating = rating,
                    sourceBadge = item.source,
                    plot = plot
                )
                heroCache[cacheKey] = enriched
                return enriched
            }
        }

        val fallback = HeroSpotlightData(
            title = item.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { item.title },
            backdropUrl = item.backdropUrl.ifBlank { item.posterUrl },
            logoUrl = item.logoUrl,
            posterUrl = item.posterUrl,
            metaLine = listOf(
                item.genre.replace(",", " •").trim(),
                item.releaseInfo
            ).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Strim Definisi Tinggi  •  Bebas Iklan" },
            statusBadge = item.statusBadge.ifBlank { if (item.isSeries) "SERIES" else "RELEASED" },
            qualityBadge = item.episodeOrQuality.replace("W32 • ", "").ifBlank { "HD" },
            rating = item.rating,
            sourceBadge = item.source,
            plot = item.plot.ifBlank { "Tekan untuk menonton terus dalam kualiti HD tanpa gangguan iklan." }
        )
        heroCache[cacheKey] = fallback
        return fallback
    }

    private fun extractBestLogoUrl(imagesObj: JSONObject?): String {
        val logos = imagesObj?.optJSONArray("logos") ?: return ""
        var fallbackPng = ""
        for (i in 0 until logos.length()) {
            val lObj = logos.optJSONObject(i) ?: continue
            val path = lObj.optString("file_path", "")
            // Hanya ambil format .png atau .jpg (elakkan .svg supaya BitmapFactory Android dapat decode terus)
            if (!path.endsWith(".png", ignoreCase = true) && !path.endsWith(".jpg", ignoreCase = true)) {
                continue
            }
            if (fallbackPng.isBlank()) fallbackPng = "$IMG_LOGO$path"
            val lang = lObj.optString("iso_639_1", "")
            if (lang == "en" || lang.isBlank() || lang == "null") {
                return "$IMG_LOGO$path"
            }
        }
        return fallbackPng
    }

    private fun searchBestTmdbMatch(cleanTitle: String, yearHint: String, preferSeries: Boolean): Pair<Int, Boolean>? {
        if (cleanTitle.isBlank()) return null
        val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
        val json = fetchJson("$TMDB_API_BASE/search/multi?api_key=$TMDB_API_KEY&query=$encoded&page=1&include_adult=false")
            ?: return null
        val results = json.optJSONArray("results") ?: return null

        var bestId = 0
        var bestIsTv = preferSeries
        var bestScore = -1

        for (i in 0 until minOf(results.length(), 10)) {
            val obj = results.optJSONObject(i) ?: continue
            val mType = obj.optString("media_type", "")
            if (mType != "movie" && mType != "tv") continue
            val isTv = mType == "tv"
            val id = obj.optInt("id", 0)
            if (id <= 0) continue

            val candidateTitle = if (isTv) {
                obj.optString("name").ifBlank { obj.optString("original_name") }
            } else {
                obj.optString("title").ifBlank { obj.optString("original_title") }
            }
            val date = if (isTv) obj.optString("first_air_date") else obj.optString("release_date")
            val yr = date.take(4)

            var score = 0
            if (candidateTitle.equals(cleanTitle, ignoreCase = true)) score += 50
            else if (candidateTitle.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(candidateTitle, ignoreCase = true)) score += 25

            if (yearHint.length == 4 && yr == yearHint) score += 35
            if (isTv == preferSeries) score += 20

            if (score > bestScore) {
                bestScore = score
                bestId = id
                bestIsTv = isTv
            }
        }

        return if (bestId > 0) (bestId to bestIsTv) else null
    }

    fun extractCleanTitleAndYear(raw: String): Pair<String, String> {
        val beforeBullet = raw.substringBefore(" • ")
        val yearMatch = Regex("\\((\\d{4})\\)").find(beforeBullet)
        val year = yearMatch?.groupValues?.getOrNull(1) ?: ""
        val cleaned = beforeBullet
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("(?i)season\\s*\\d+"), "")
            .replace(Regex("(?i)sub\\s*indo"), "")
            .replace(Regex("(?i)episode\\s*\\d+"), "")
            .trim()
        return cleaned to year
    }

    private fun parseSeasonEpisode(label: String): Pair<Int, Int> {
        if (label.isBlank()) return 1 to 1
        val sMatch = Regex("(?i)S(\\d+)").find(label)
        val eMatch = Regex("(?i)(?:E|Episod|Episode|EPS)\\s*(\\d+)").find(label)
        val s = sMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val e = eMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        return s.coerceAtLeast(1) to e.coerceAtLeast(1)
    }

    private fun formatRuntime(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun formatNiceDate(isoDate: String): String {
        // Contoh: "2026-10-02" -> "Oct 2, 2026"
        val parts = isoDate.trim().split("-")
        if (parts.size < 3) return isoDate.take(4)
        val year = parts[0]
        val monthIdx = parts[1].toIntOrNull() ?: return year
        val day = parts[2].toIntOrNull() ?: return year
        val months = arrayOf(
            "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val mName = months.getOrNull(monthIdx) ?: return year
        return "$mName $day, $year"
    }
}
