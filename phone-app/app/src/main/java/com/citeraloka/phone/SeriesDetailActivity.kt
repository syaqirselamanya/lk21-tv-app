package com.citeraloka.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import com.citeraloka.phone.domains.DomainManager
import com.citeraloka.phone.domains.DualDomainMediaResult
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class SeriesDetailActivity : Activity() {

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var mainScrollView: ScrollView
    private lateinit var heroBackdropView: ImageView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView
    private lateinit var heroActionCol: LinearLayout

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

    private var lk21SeriesDetail: MediaDetail? = null
    private var watch32SeriesDetail: MediaDetail? = null
    private var activeDomainSource = "LK21"

    private var allEpisodes = listOf<EpisodeItem>()
    private var selectedSeason = 1
    private val seasonTabButtons = mutableListOf<Pair<Int, Button>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor(MainActivity.BG_DEEP_TEAL)
            window.navigationBarColor = Color.parseColor(MainActivity.BG_DEEP_TEAL)
        }

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

        // 1. Hero Page Siri TV Portrait Gaya Netflix Mobile
        scrollBody.addView(buildPortraitSeriesHeroStage())

        // 2. Pilihan Musim & Kad Episod Portrait
        val bottomContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(30))
        }

        seasonStripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            setPadding(0, 0, 0, dp(12))
        }
        seasonTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        seasonStripScroll.addView(seasonTabsRow)
        bottomContent.addView(seasonStripScroll)

        episodesHeaderTitle = TextView(this).apply {
            text = "EPISODES"
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setPadding(dp(2), 0, dp(2), dp(10))
        }
        bottomContent.addView(episodesHeaderTitle)

        loadingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(14))
        }
        val spinner = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                rightMargin = dp(10)
            }
        }
        loadingStatusView = TextView(this).apply {
            text = "Loading seasons and episodes..."
            setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
        loadingRow.addView(spinner)
        loadingRow.addView(loadingStatusView)
        bottomContent.addView(loadingRow)

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

    private fun buildPortraitSeriesHeroStage(): FrameLayout {
        val dm = resources.displayMetrics
        val stageHeight = (dm.heightPixels * 0.60f).toInt().coerceIn(dp(420), dp(545))

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                stageHeight
            )
        }

        heroBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor(MainActivity.BG_SURFACE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        stage.addView(heroBackdropView)

        val topVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC031117"), Color.TRANSPARENT)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(88),
                Gravity.TOP
            )
        }
        stage.addView(topVignette)

        val bottomVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.parseColor("#FF031117"),
                    Color.parseColor("#EB031117"),
                    Color.parseColor("#AD031117"),
                    Color.TRANSPARENT
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (stageHeight * 0.78f).toInt(),
                Gravity.BOTTOM
            )
        }
        stage.addView(bottomVignette)

        val backBtn = Button(this).apply {
            text = "BACK"
            isAllCaps = true
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg("#D9071F29", MainActivity.BORDER_SUBTLE_TEAL, 1, 8)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(14), dp(14), 0, 0)
            }
            setOnClickListener { finish() }
        }
        stage.addView(backBtn)

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setPadding(dp(16), dp(54), dp(16), dp(18))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
            )
        }

        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = dp(260)
            maxHeight = dp(68)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(250), dp(64)).apply {
                bottomMargin = dp(8)
            }
        }
        infoCol.addView(heroLogoView)

        heroTitleView = TextView(this).apply {
            text = seriesTitle.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { seriesTitle }
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(4))
        }
        infoCol.addView(heroTitleView)

        heroMetaView = TextView(this).apply {
            text = genre.replace(",", " •").ifBlank { "HD Series" }
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroMetaView)

        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroBadgeRow)

        heroPlotView = TextView(this).apply {
            text = plot.ifBlank { "Loading series synopsis..." }
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), 0, dp(4), dp(14))
        }
        infoCol.addView(heroPlotView)

        heroActionCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        infoCol.addView(heroActionCol)

        val bottomDivider = View(this).apply {
            setBackgroundColor(Color.parseColor(MainActivity.BORDER_SUBTLE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
                Gravity.BOTTOM
            )
        }

        stage.addView(infoCol)
        stage.addView(bottomDivider)
        return stage
    }

    private fun bindInitialHeroPreview() {
        val bg = backdropUrl.ifBlank { posterUrl }
        if (bg.isNotBlank()) {
            loadImageAsync(bg, heroBackdropView, crossfade = true)
        }
        if (logoUrl.isNotBlank()) {
            loadImageAsync(logoUrl, heroLogoView) { ok ->
                if (ok) {
                    heroLogoView.visibility = View.VISIBLE
                    heroTitleView.visibility = View.GONE
                }
            }
        }
        renderHeroBadges(0, 0)
    }

    private fun renderHeroBadges(seasonCount: Int, epCount: Int) {
        heroBadgeRow.removeAllViews()
        heroBadgeRow.addView(createPillBadge("SERIES", MainActivity.ACCENT_TEAL, "#CC071F29", MainActivity.ACCENT_TEAL_DARK))
        if (seasonCount > 0) {
            val sWord = if (seasonCount == 1) "SEASON" else "SEASONS"
            heroBadgeRow.addView(createPillBadge("$seasonCount $sWord", MainActivity.TEXT_PRIMARY, MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL))
        }
        if (epCount > 0) {
            heroBadgeRow.addView(createPillBadge("$epCount EP", MainActivity.TEXT_PRIMARY, MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL))
        }
        if (rating.isNotBlank()) {
            heroBadgeRow.addView(createPillBadge("IMDb $rating", "#FACC15", "#D904151F", MainActivity.BORDER_SUBTLE_TEAL))
        }
    }

    private fun createPillBadge(label: String, textHex: String, bgHex: String, borderHex: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            background = createRoundedBg(bgHex, borderHex, 1, 4)
            setPadding(dp(7), dp(2), dp(7), dp(2))
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
                val dualResult = DomainManager.fetchDualDetails(
                    context = this@SeriesDetailActivity,
                    lk21BaseUrl = baseUrl,
                    primaryUrl = seriesUrl,
                    fallbackTitle = seriesTitle,
                    isSeriesHint = true
                )

                val lkDetail = dualResult.lk21Detail?.let { Watch32Scraper.enrichSeriesDetail(it) }
                val w32Detail = dualResult.watch32Detail

                lk21SeriesDetail = lkDetail
                watch32SeriesDetail = w32Detail

                val rawDetail = when {
                    source == "WATCH32" && w32Detail != null && w32Detail.episodes.isNotEmpty() -> {
                        activeDomainSource = "WATCH32"
                        w32Detail
                    }
                    lkDetail != null && lkDetail.episodes.isNotEmpty() -> {
                        activeDomainSource = "LK21"
                        lkDetail
                    }
                    w32Detail != null && w32Detail.episodes.isNotEmpty() -> {
                        activeDomainSource = "WATCH32"
                        w32Detail
                    }
                    else -> throw IllegalStateException("No episodes found.")
                }

                mainHandler.post {
                    applyActiveSeriesDetail(rawDetail)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loadingStatusView.text = "Failed to load episodes: ${e.message}"
                }
            }
        }
    }

    private fun applyActiveSeriesDetail(rawDetail: MediaDetail) {
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

        heroTitleView.text = seriesTitle.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { seriesTitle }
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

    private fun rebuildHeroActions() {
        heroActionCol.removeAllViews()
        if (allEpisodes.isEmpty()) return

        // Domain Selector Row (1. LK21 / 2. WATCH32)
        val domainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val lkAvail = lk21SeriesDetail != null && lk21SeriesDetail!!.episodes.isNotEmpty()
        val w32Avail = watch32SeriesDetail != null && watch32SeriesDetail!!.episodes.isNotEmpty()

        val lkPill = Button(this).apply {
            val isSel = activeDomainSource == "LK21" && lkAvail
            text = if (!lkAvail) "1. LK21 (Tiada)" else if (isSel) "[✓] 1. LK21" else "[  ] 1. LK21"
            isAllCaps = false
            isEnabled = lkAvail
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(Color.parseColor(if (isSel) MainActivity.TEXT_PRIMARY else "#8AB8C2"))
            minHeight = 0
            minimumHeight = 0
            background = if (isSel) {
                createRoundedBg("#2600D4B2", MainActivity.ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                val det = lk21SeriesDetail ?: return@setOnClickListener
                if (activeDomainSource != "LK21") {
                    activeDomainSource = "LK21"
                    applyActiveSeriesDetail(det)
                }
            }
        }

        val w32Pill = Button(this).apply {
            val isSel = activeDomainSource == "WATCH32" && w32Avail
            text = if (!w32Avail) "2. WATCH32 (Tiada)" else if (isSel) "[✓] 2. WATCH32" else "[  ] 2. WATCH32"
            isAllCaps = false
            isEnabled = w32Avail
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(Color.parseColor(if (isSel) MainActivity.TEXT_PRIMARY else "#8AB8C2"))
            minHeight = 0
            minimumHeight = 0
            background = if (isSel) {
                createRoundedBg("#2600D4B2", MainActivity.ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val det = watch32SeriesDetail ?: return@setOnClickListener
                if (activeDomainSource != "WATCH32") {
                    activeDomainSource = "WATCH32"
                    applyActiveSeriesDetail(det)
                }
            }
        }

        domainRow.addView(lkPill)
        domainRow.addView(w32Pill)
        heroActionCol.addView(domainRow)

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
            val resumeBtn = Button(this).apply {
                text = resumeLabel
                isAllCaps = true
                setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                minHeight = 0
                minimumHeight = 0
                background = createRoundedBg("#2600D4B2", MainActivity.ACCENT_TEAL, 2, 24)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    launchPlayer(
                        epUrl = savedHistory.pageUrl,
                        epLabel = savedHistory.episodeLabel,
                        embedUrl = savedHistory.embedUrl,
                        serverName = savedHistory.serverName,
                        resumeSec = savedHistory.currentSec
                    )
                }
            }
            heroActionCol.addView(resumeBtn)
        } else {
            val playBtn = Button(this).apply {
                text = "PLAY S${firstEp.seasonNumber}:E${firstEp.episodeNumber} NOW"
                isAllCaps = true
                setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                minHeight = 0
                minimumHeight = 0
                background = createRoundedBg("#2600D4B2", MainActivity.ACCENT_TEAL, 2, 24)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { playEpisode(firstEp) }
            }
            heroActionCol.addView(playBtn)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT
                letterSpacing = 0.05f
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(16), dp(7), dp(16), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dp(8)
                }
                setOnClickListener {
                    selectedSeason = sNum
                    refreshSeasonPills()
                    renderSeasonEpisodes(sNum)
                }
            }
            seasonTabButtons.add(sNum to btn)
            seasonTabsRow.addView(btn)
        }
        refreshSeasonPills()
    }

    private fun refreshSeasonPills() {
        seasonTabButtons.forEach { (sNum, btn) ->
            if (sNum == selectedSeason) {
                btn.setTextColor(Color.parseColor(MainActivity.TEXT_PRIMARY))
                btn.typeface = Typeface.DEFAULT_BOLD
                btn.background = createRoundedBg("#2600D4B2", MainActivity.ACCENT_TEAL, 2, 24)
            } else {
                btn.setTextColor(Color.parseColor("#8AB8C2"))
                btn.typeface = Typeface.DEFAULT
                btn.background = createRoundedBg("#00000000", "#00000000", 0, 24)
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

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedBg(MainActivity.BG_CARD_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 10)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
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

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val thumbWidth = dp(126)
            val thumbHeight = dp(72)
            val thumbFrame = FrameLayout(this).apply {
                background = createRoundedBg(MainActivity.BG_SURFACE_TEAL, MainActivity.BORDER_SUBTLE_TEAL, 1, 6)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(thumbWidth, thumbHeight).apply {
                    rightMargin = dp(12)
                }
            }

            val thumbImg = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            loadImageAsync(ep.stillUrl.ifBlank { backdropUrl.ifBlank { posterUrl } }, thumbImg)
            thumbFrame.addView(thumbImg)

            val epNumBadge = TextView(this).apply {
                text = String.format("EP %02d", ep.episodeNumber)
                setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg(MainActivity.ACCENT_TEAL, MainActivity.ACCENT_TEAL, 0, 4)
                setPadding(dp(5), dp(1), dp(5), dp(1))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dp(4), dp(4), 0, 0)
                }
            }
            thumbFrame.addView(epNumBadge)

            if (historyItem != null) {
                val pct = historyItem.progressPercent.coerceIn(6, 100)
                val progFill = View(this).apply {
                    setBackgroundColor(Color.parseColor(MainActivity.ACCENT_TEAL))
                    layoutParams = FrameLayout.LayoutParams(
                        (thumbWidth * (pct / 100f)).toInt(),
                        dp(3),
                        Gravity.BOTTOM
                    )
                }
                thumbFrame.addView(progFill)
            }

            val titleCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(8)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }

            val metaParts = listOfNotNull(
                ep.runtime.takeIf { it.isNotBlank() },
                ep.airDate.takeIf { it.isNotBlank() }
            )
            val epMetaView = TextView(this).apply {
                text = metaParts.joinToString("  •  ").ifBlank { "Season ${ep.seasonNumber}" }
                setTextColor(Color.parseColor(MainActivity.TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(2), 0, 0)
            }

            titleCol.addView(epTitleView)
            titleCol.addView(epMetaView)

            val playPill = TextView(this).apply {
                text = if (historyItem != null) "RESUME" else "PLAY"
                setTextColor(Color.parseColor(MainActivity.BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                background = createRoundedBg(MainActivity.ACCENT_TEAL, MainActivity.ACCENT_TEAL, 0, 6)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }

            topRow.addView(thumbFrame)
            topRow.addView(titleCol)
            topRow.addView(playPill)
            card.addView(topRow)

            val overviewText = ep.overview.ifBlank { plot }
            if (overviewText.isNotBlank()) {
                val epPlotView = TextView(this).apply {
                    text = overviewText
                    setTextColor(Color.parseColor(MainActivity.TEXT_SECONDARY))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setLineSpacing(0f, 1.14f)
                    setPadding(dp(2), dp(8), dp(2), 0)
                }
                card.addView(epPlotView)
            }

            episodesContainer.addView(card)
        }
    }

    private fun playEpisode(ep: EpisodeItem) {
        val defaultServer = if (Watch32Scraper.isWatch32Uri(ep.url)) "Cinezo (No Ads)" else "P2P Direct"
        launchPlayer(
            epUrl = ep.url,
            epLabel = ep.label,
            embedUrl = "",
            serverName = defaultServer
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
                imageView.alpha = 0.5f
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
                                    imageView.alpha = 0.4f
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
