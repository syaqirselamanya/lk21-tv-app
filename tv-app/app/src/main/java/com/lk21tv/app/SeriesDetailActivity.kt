package com.lk21tv.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class SeriesDetailActivity : Activity() {

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageCache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var mainScrollView: ScrollView
    private lateinit var heroBackdropView: ImageView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView
    private lateinit var heroActionRow: LinearLayout

    private lateinit var seasonStripScroll: HorizontalScrollView
    private lateinit var seasonTabsRow: LinearLayout
    private lateinit var episodesHeaderTitle: TextView
    private lateinit var loadingRow: LinearLayout
    private lateinit var loadingStatusView: TextView
    private lateinit var episodesContainer: LinearLayout

    private var seriesUrl = ""
    private var baseUrl = "https://tv12.lk21official.cc"
    private var seriesTitle = ""
    private var posterUrl = ""
    private var backdropUrl = ""
    private var logoUrl = ""
    private var plot = ""
    private var rating = ""
    private var genre = ""
    private var source = "LK21"

    private var loadedDetail: MediaDetail? = null
    private var allEpisodes = listOf<EpisodeItem>()
    private var selectedSeason = 1
    private val seasonTabButtons = mutableListOf<Pair<Int, Button>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seriesUrl = intent.getStringExtra("PAGE_URL") ?: ""
        baseUrl = intent.getStringExtra("BASE_URL") ?: "https://tv12.lk21official.cc"
        seriesTitle = intent.getStringExtra("TITLE") ?: "TV Series"
        posterUrl = intent.getStringExtra("POSTER_URL") ?: ""
        backdropUrl = intent.getStringExtra("BACKDROP_URL") ?: ""
        logoUrl = intent.getStringExtra("LOGO_URL") ?: ""
        plot = intent.getStringExtra("PLOT") ?: ""
        rating = intent.getStringExtra("RATING") ?: ""
        genre = intent.getStringExtra("GENRE") ?: ""
        source = intent.getStringExtra("SOURCE") ?: "LK21"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Hero Page Siri TV Gaya Netflix (Backdrop 16:9 + ClearLogo PNG + Sinopsis + Play)
        scrollBody.addView(buildTvSeriesHeroStage())

        // 2. Kandungan Bawah: Pilihan Musim & Senarai Kad Episod Lebar
        val bottomContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(14), dp(28), dp(36))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Jalur Pilihan Musim (MUSIM 1, MUSIM 2, ...)
        seasonStripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            setPadding(0, 0, 0, dp(14))
        }
        seasonTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        seasonStripScroll.addView(seasonTabsRow)
        bottomContent.addView(seasonStripScroll)

        // Tajuk Seksyen Episod
        episodesHeaderTitle = TextView(this).apply {
            text = "EPISODES"
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(dp(2), 0, dp(2), dp(12))
        }
        bottomContent.addView(episodesHeaderTitle)

        // Penunjuk Muatan Episod
        loadingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
        }
        val spinner = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                rightMargin = dp(12)
            }
        }
        loadingStatusView = TextView(this).apply {
            text = "Loading seasons and episodes..."
            setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        loadingRow.addView(spinner)
        loadingRow.addView(loadingStatusView)
        bottomContent.addView(loadingRow)

        // Senarai Kad Episod Gaya Netflix
        episodesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bottomContent.addView(episodesContainer)

        scrollBody.addView(bottomContent)
        mainScrollView.addView(scrollBody)
        root.addView(mainScrollView)

        setContentView(root)

        bindInitialHeroPreview()
        fetchFullSeriesData()
    }

    override fun onResume() {
        super.onResume()
        if (allEpisodes.isNotEmpty()) {
            rebuildHeroActions()
            renderSeasonEpisodes(selectedSeason)
        }
    }

    private fun buildTvSeriesHeroStage(): FrameLayout {
        val dm = resources.displayMetrics
        val stageHeight = (dm.heightPixels * 0.68f).toInt().coerceIn(dp(310), dp(415))

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                stageHeight
            )
        }

        // Lapisan 1: 16:9 Widescreen Backdrop
        val backdropWidth = (dm.widthPixels * 0.76f).toInt()
        heroBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor(MainActivity.BG_SURFACE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                backdropWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }
        stage.addView(heroBackdropView)

        // Lapisan 2A: Vignette Kiri-ke-Kanan
        val leftVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#FF031117"),
                    Color.parseColor("#FA031117"),
                    Color.parseColor("#CC031117"),
                    Color.parseColor("#55031117"),
                    Color.parseColor("#12031117")
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        stage.addView(leftVignette)

        // Lapisan 2B: Vignette Bawah-ke-Atas
        val bottomVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.parseColor("#FF031117"),
                    Color.parseColor("#A6031117"),
                    Color.TRANSPARENT
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(84),
                Gravity.BOTTOM
            )
        }
        stage.addView(bottomVignette)

        val bottomDivider = View(this).apply {
            setBackgroundColor(Color.parseColor(MainActivity.BORDER_SUBTLE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
                Gravity.BOTTOM
            )
        }
        stage.addView(bottomDivider)

        // Butang BACK di Penjuru Kiri Atas
        val backBtn = createTvActionButton("BACK", isPrimary = false) {
            finish()
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(28), dp(16), 0, 0)
            }
        }
        stage.addView(backBtn)

        // Kolum Maklumat Hero Siri di Sebelah Kiri
        val infoWidth = (dm.widthPixels * 0.62f).toInt().coerceAtLeast(dp(340))
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(28), dp(58), dp(18), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                infoWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.BOTTOM
            )
        }

        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
            maxWidth = dp(290)
            maxHeight = dp(72)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(275), dp(68)).apply {
                bottomMargin = dp(8)
            }
        }
        infoCol.addView(heroLogoView)

        heroTitleView = TextView(this).apply {
            text = seriesTitle.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { seriesTitle }
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(4))
        }
        infoCol.addView(heroTitleView)

        heroMetaView = TextView(this).apply {
            text = genre.replace(",", " •").ifBlank { "HD Series" }
            setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroMetaView)

        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        infoCol.addView(heroBadgeRow)

        heroPlotView = TextView(this).apply {
            text = plot.ifBlank { "Loading series synopsis..." }
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(14))
        }
        infoCol.addView(heroPlotView)

        heroActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        infoCol.addView(heroActionRow)

        stage.addView(infoCol)
        return stage
    }

    private fun bindInitialHeroPreview() {
        val initialBg = backdropUrl.ifBlank { posterUrl }
        if (initialBg.isNotBlank()) {
            loadImageAsync(initialBg, heroBackdropView, crossfade = true)
        }
        if (logoUrl.isNotBlank()) {
            loadImageAsync(logoUrl, heroLogoView) { ok ->
                if (ok) {
                    heroLogoView.visibility = View.VISIBLE
                    heroTitleView.visibility = View.GONE
                }
            }
        }
        renderHeroBadges(seasonCount = 0, epCount = 0)
    }

    private fun renderHeroBadges(seasonCount: Int, epCount: Int) {
        heroBadgeRow.removeAllViews()
        heroBadgeRow.addView(createPillBadge("SERIES", MainActivity.ACCENT_TEAL, "#CC071F29", MainActivity.ACCENT_TEAL_DARK))
        if (seasonCount > 0) {
            val sWord = if (seasonCount == 1) "SEASON" else "SEASONS"
            heroBadgeRow.addView(createPillBadge("$seasonCount $sWord", MainActivity.TEXT_PRIMARY, MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL))
        }
        if (epCount > 0) {
            val eWord = if (epCount == 1) "EPISODE" else "EPISODES"
            heroBadgeRow.addView(createPillBadge("$epCount $eWord", MainActivity.TEXT_PRIMARY, MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL))
        }
        if (rating.isNotBlank()) {
            heroBadgeRow.addView(createPillBadge("IMDb $rating", "#FACC15", "#D904151F", MainActivity.BORDER_SUBTLE_TEAL))
        }
        if (source.isNotBlank()) {
            heroBadgeRow.addView(createPillBadge(source.uppercase(), MainActivity.TEXT_SECONDARY, "#CC061C24", MainActivity.BORDER_SUBTLE_TEAL))
        }
    }

    private fun createPillBadge(label: String, textHex: String, bgHex: String, borderHex: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            background = createRoundedBg(bgHex, borderHex, 1, 4)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
        }
    }

    private fun fetchFullSeriesData() {
        executor.execute {
            try {
                val lk = try {
                    LK21Scraper.fetchDetail(baseUrl, seriesUrl)
                } catch (_: Exception) {
                    null
                } ?: throw IllegalStateException("No episodes found.")

                val rawDetail = Watch32Scraper.enrichSeriesDetail(lk)

                mainHandler.post {
                    loadedDetail = rawDetail
                    allEpisodes = rawDetail.episodes
                    seriesTitle = rawDetail.title
                    if (rawDetail.posterUrl.isNotBlank()) posterUrl = rawDetail.posterUrl
                    if (rawDetail.backdropUrl.isNotBlank()) backdropUrl = rawDetail.backdropUrl
                    if (rawDetail.logoUrl.isNotBlank()) logoUrl = rawDetail.logoUrl
                    if (rawDetail.plot.isNotBlank()) plot = rawDetail.plot
                    if (rawDetail.rating.isNotBlank()) rating = rawDetail.rating
                    if (rawDetail.genre.isNotBlank()) genre = rawDetail.genre
                    source = rawDetail.source

                    loadingRow.visibility = View.GONE

                    val cleanHeroTitle = seriesTitle.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { seriesTitle }
                    heroTitleView.text = cleanHeroTitle
                    heroPlotView.text = plot

                    val seasons = allEpisodes.map { it.seasonNumber.coerceAtLeast(1) }.distinct().sorted()
                    val metaParts = listOfNotNull(
                        genre.replace(",", " •").takeIf { it.isNotBlank() },
                        if (seasons.isNotEmpty()) "${seasons.size} ${if (seasons.size == 1) "Season" else "Seasons"}" else null,
                        if (allEpisodes.isNotEmpty()) "${allEpisodes.size} ${if (allEpisodes.size == 1) "Episode" else "Episodes"}" else null
                    )
                    heroMetaView.text = metaParts.joinToString("  •  ")
                    renderHeroBadges(seasons.size, allEpisodes.size)

                    val bgToLoad = backdropUrl.ifBlank { posterUrl }
                    if (bgToLoad.isNotBlank()) {
                        loadImageAsync(bgToLoad, heroBackdropView, crossfade = true)
                    }
                    if (logoUrl.isNotBlank()) {
                        loadImageAsync(logoUrl, heroLogoView) { ok ->
                            if (ok) {
                                heroLogoView.visibility = View.VISIBLE
                                heroTitleView.visibility = View.GONE
                            }
                        }
                    }

                    rebuildHeroActions()
                    buildSeasonSelector(seasons)
                    selectedSeason = seasons.firstOrNull() ?: 1
                    renderSeasonEpisodes(selectedSeason)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loadingStatusView.text = "Failed to load episodes: ${e.message}"
                }
            }
        }
    }

    private fun rebuildHeroActions() {
        heroActionRow.removeAllViews()
        if (allEpisodes.isEmpty()) return

        val firstEp = allEpisodes.first()
        val cleanSeriesPrefix = seriesTitle.substringBefore(" • ").trim()
        val savedHistory = ContinueWatchingManager.getList(this).firstOrNull { h ->
            h.pageUrl == seriesUrl ||
                allEpisodes.any { it.url == h.pageUrl } ||
                h.title.startsWith(cleanSeriesPrefix, ignoreCase = true)
        }

        if (savedHistory != null) {
            val resumeLabel = if (savedHistory.episodeLabel.isNotBlank()) {
                "RESUME ${savedHistory.episodeLabel.substringBefore(" • ").uppercase()} (${formatTime(savedHistory.currentSec)})"
            } else {
                "RESUME (${formatTime(savedHistory.currentSec)})"
            }
            val resumeBtn = createTvActionButton(resumeLabel, isPrimary = true) {
                launchPlayer(
                    epUrl = savedHistory.pageUrl,
                    epLabel = savedHistory.episodeLabel,
                    embedUrl = savedHistory.embedUrl,
                    serverName = savedHistory.serverName,
                    resumeSec = savedHistory.currentSec
                )
            }
            val ep1Btn = createTvActionButton("PLAY S${firstEp.seasonNumber}:E${firstEp.episodeNumber}", isPrimary = false) {
                playEpisode(firstEp)
            }
            heroActionRow.addView(resumeBtn)
            heroActionRow.addView(ep1Btn)
            resumeBtn.requestFocus()
        } else {
            val playEp1Btn = createTvActionButton("PLAY S${firstEp.seasonNumber}:E${firstEp.episodeNumber} NOW", isPrimary = true) {
                playEpisode(firstEp)
            }
            heroActionRow.addView(playEp1Btn)
            playEp1Btn.requestFocus()
        }
    }

    private fun buildSeasonSelector(seasons: List<Int>) {
        seasonTabsRow.removeAllViews()
        seasonTabButtons.clear()

        if (seasons.size <= 1) {
            seasonStripScroll.visibility = View.GONE
            return
        }

        seasonStripScroll.visibility = View.VISIBLE
        seasons.forEach { sNum ->
            val btn = Button(this).apply {
                text = "SEASON $sNum"
                isAllCaps = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                isFocusable = true
                isFocusableInTouchMode = false
                setPadding(dp(18), dp(8), dp(18), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dp(10)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    updateSeasonTabAppearance(v as Button, sNum == selectedSeason, hasFocus)
                }
                setOnClickListener {
                    selectedSeason = sNum
                    refreshAllSeasonTabAppearances()
                    renderSeasonEpisodes(sNum)
                }
            }
            seasonTabButtons.add(sNum to btn)
            seasonTabsRow.addView(btn)
        }
        refreshAllSeasonTabAppearances()
    }

    private fun refreshAllSeasonTabAppearances() {
        seasonTabButtons.forEach { (sNum, btn) ->
            updateSeasonTabAppearance(btn, sNum == selectedSeason, btn.hasFocus())
        }
    }

    private fun updateSeasonTabAppearance(btn: Button, isSelected: Boolean, hasFocus: Boolean) {
        when {
            hasFocus -> {
                btn.setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                btn.background = createRoundedBg(MainActivity.ACCENT_TEAL, "#FFFFFF", 2, 6)
                btn.scaleX = 1.05f
                btn.scaleY = 1.05f
            }
            isSelected -> {
                btn.setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                btn.background = createRoundedBg(MainActivity.ACCENT_TEAL, MainActivity.ACCENT_TEAL, 1, 6)
                btn.scaleX = 1.0f
                btn.scaleY = 1.0f
            }
            else -> {
                btn.setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                btn.background = createRoundedBg(MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 6)
                btn.scaleX = 1.0f
                btn.scaleY = 1.0f
            }
        }
    }

    private fun renderSeasonEpisodes(seasonNum: Int) {
        episodesContainer.removeAllViews()
        val filtered = allEpisodes.filter { it.seasonNumber == seasonNum }.ifEmpty { allEpisodes }
        episodesHeaderTitle.text = "SEASON $seasonNum  •  ${filtered.size} ${if (filtered.size == 1) "EPISODE" else "EPISODES"}"

        val historyMap = ContinueWatchingManager.getList(this).associateBy { it.pageUrl }

        filtered.forEach { ep ->
            val historyItem = historyMap[ep.url]

            val playPill = TextView(this).apply {
                text = if (historyItem != null) "RESUME" else "PLAY"
                setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                background = createRoundedBg(MainActivity.ACCENT_TEAL_DARK, MainActivity.ACCENT_TEAL, 1, 6)
                setPadding(dp(18), dp(10), dp(18), dp(10))
            }

            val rowCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = false
                background = createRoundedBg(MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 10)
                setPadding(dp(12), dp(10), dp(18), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.background = createRoundedBg("#103B4C", MainActivity.ACCENT_TEAL, 2, 10)
                        v.scaleX = 1.015f
                        v.scaleY = 1.015f
                        playPill.setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                        playPill.background = createRoundedBg(MainActivity.ACCENT_TEAL, "#FFFFFF", 1, 6)
                    } else {
                        v.background = createRoundedBg(MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 10)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                        playPill.setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                        playPill.background = createRoundedBg(MainActivity.ACCENT_TEAL_DARK, MainActivity.ACCENT_TEAL, 1, 6)
                    }
                }
                setOnClickListener {
                    if (historyItem != null) {
                        launchPlayer(
                            epUrl = ep.url,
                            epLabel = ep.label,
                            embedUrl = historyItem.embedUrl,
                            serverName = historyItem.serverName,
                            resumeSec = historyItem.currentSec
                        )
                    } else {
                        playEpisode(ep)
                    }
                }
            }

            // 1. Gambar Babak 16:9 Episod di Sebelah Kiri
            val thumbWidth = dp(186)
            val thumbHeight = dp(104)
            val thumbFrame = FrameLayout(this).apply {
                background = createRoundedBg(MainActivity.BG_SURFACE_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 6)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(thumbWidth, thumbHeight).apply {
                    rightMargin = dp(18)
                }
            }

            val thumbImg = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val thumbUrl = ep.stillUrl.ifBlank { backdropUrl.ifBlank { posterUrl } }
            loadImageAsync(thumbUrl, thumbImg)
            thumbFrame.addView(thumbImg)

            val epNumBadge = TextView(this).apply {
                text = String.format("EP %02d", ep.episodeNumber)
                setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg(MainActivity.ACCENT_TEAL, MainActivity.ACCENT_TEAL, 0, 4)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dp(6), dp(6), 0, 0)
                }
            }
            thumbFrame.addView(epNumBadge)

            if (historyItem != null) {
                val pct = historyItem.progressPercent.coerceIn(6, 100)
                val progBg = View(this).apply {
                    setBackgroundColor(Color.parseColor("#B3031117"))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(4),
                        Gravity.BOTTOM
                    )
                }
                val progFill = View(this).apply {
                    setBackgroundColor(Color.parseColor(MainActivity.ACCENT_TEAL))
                    layoutParams = FrameLayout.LayoutParams(
                        (thumbWidth * (pct / 100f)).toInt(),
                        dp(4),
                        Gravity.BOTTOM
                    )
                }
                thumbFrame.addView(progBg)
                thumbFrame.addView(progFill)
            }

            // 2. Kolum Tajuk Episod, Durasi & Sinopsis di Tengah
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(16)
                }
            }

            val displayEpTitle = if (ep.title.isNotBlank() && !ep.title.startsWith("Episode", ignoreCase = true) && !ep.title.startsWith("Episod", ignoreCase = true)) {
                "${ep.episodeNumber}. ${ep.title}"
            } else {
                "Episode ${ep.episodeNumber}"
            }

            val epTitleView = TextView(this).apply {
                text = displayEpTitle
                setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val subMetaParts = listOfNotNull(
                "Season ${ep.seasonNumber}",
                ep.runtime.takeIf { it.isNotBlank() },
                ep.airDate.takeIf { it.isNotBlank() }
            )
            val epMetaView = TextView(this).apply {
                text = subMetaParts.joinToString("  •  ")
                setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(2), 0, dp(5))
            }

            val epPlotView = TextView(this).apply {
                text = ep.overview.ifBlank { plot.ifBlank { "Select to stream ${ep.label} in HD." } }
                setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.14f)
            }

            textCol.addView(epTitleView)
            textCol.addView(epMetaView)
            textCol.addView(epPlotView)

            rowCard.addView(thumbFrame)
            rowCard.addView(textCol)
            rowCard.addView(playPill)

            episodesContainer.addView(rowCard)
        }
    }

    private fun playEpisode(ep: EpisodeItem) {
        launchPlayer(
            epUrl = ep.url,
            epLabel = ep.label,
            embedUrl = "",
            serverName = "P2P Direct"
        )
    }

    private fun launchPlayer(
        epUrl: String,
        epLabel: String,
        embedUrl: String,
        serverName: String,
        resumeSec: Int = 0,
        mirrors: List<MirrorItem> = emptyList()
    ) {
        val cleanSeriesName = seriesTitle.substringBefore(" • ").trim()
        val fullPlayerTitle = if (epLabel.isNotBlank()) "$cleanSeriesName • $epLabel" else cleanSeriesName

        val epLabels = ArrayList(allEpisodes.map { it.label })
        val epUrls = ArrayList(allEpisodes.map { it.url })
        val mirrorNames = ArrayList(mirrors.map { it.name })
        val mirrorUrls = ArrayList(mirrors.map { it.embedUrl })

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("PAGE_URL", epUrl)
            putExtra("EMBED_URL", embedUrl)
            putExtra("TITLE", fullPlayerTitle)
            putExtra("BASE_URL", baseUrl)
            putExtra("SERVER_NAME", serverName)
            putExtra("POSTER_URL", posterUrl)
            putExtra("GENRE", genre)
            putExtra("EPISODE_LABEL", epLabel)
            putExtra("RESUME_SEC", resumeSec)
            putStringArrayListExtra("EPISODE_LABELS", epLabels)
            putStringArrayListExtra("EPISODE_URLS", epUrls)
            putStringArrayListExtra("MIRROR_NAMES", mirrorNames)
            putStringArrayListExtra("MIRROR_URLS", mirrorUrls)
        }
        startActivity(intent)
    }

    private fun createTvActionButton(label: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        val normalBg = if (isPrimary) MainActivity.ACCENT_TEAL_DARK else "#D9071F29"
        val normalBorder = if (isPrimary) MainActivity.ACCENT_TEAL else MainActivity.BORDER_SUBTLE_TEAL
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(18), dp(9), dp(18), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(10)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                    v.background = createRoundedBg(MainActivity.ACCENT_TEAL, "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadImageAsync(
        url: String,
        imageView: ImageView,
        crossfade: Boolean = false,
        onLoaded: ((Boolean) -> Unit)? = null
    ) {
        if (url.isBlank()) {
            onLoaded?.invoke(false)
            return
        }
        imageView.tag = url
        val cached = imageCache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            if (crossfade) {
                imageView.alpha = 0.45f
                imageView.animate().alpha(1f).setDuration(180).start()
            }
            onLoaded?.invoke(true)
            return
        }

        executor.execute {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (!url.contains("tmdb.org")) {
                        setRequestProperty("Referer", baseUrl)
                    }
                }
                val isPng = url.lowercase().endsWith(".png")
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = if (isPng) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                }
                conn.inputStream.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream, null, opts)
                    if (bmp != null) {
                        imageCache.put(url, bmp)
                        mainHandler.post {
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(bmp)
                                if (crossfade) {
                                    imageView.alpha = 0.35f
                                    imageView.animate().alpha(1f).setDuration(200).start()
                                }
                                onLoaded?.invoke(true)
                            }
                        }
                    } else {
                        mainHandler.post { onLoaded?.invoke(false) }
                    }
                }
            } catch (_: Exception) {
                mainHandler.post { onLoaded?.invoke(false) }
            }
        }
    }

    private fun createRoundedBg(fillHex: String, strokeHex: String, strokeWidthDp: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
