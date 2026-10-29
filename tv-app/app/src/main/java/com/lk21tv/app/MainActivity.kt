package com.lk21tv.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
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
import android.view.Window
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // Tema Warna CiteraLoka Dark Teal
    companion object {
        const val BG_DEEP_TEAL = "#031117"
        const val BG_SURFACE_TEAL = "#071F29"
        const val BG_CARD_TEAL = "#0B2B38"
        const val BORDER_SUBTLE_TEAL = "#134556"
        const val ACCENT_TEAL = "#00D4B2"
        const val ACCENT_TEAL_DARK = "#0D9488"
        const val TEXT_PRIMARY = "#F0FDFA"
        const val TEXT_SECONDARY = "#99F6E4"
        const val TEXT_MUTED = "#5EEAD4"
    }

    private val executor = Executors.newFixedThreadPool(6)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var contentColumn: LinearLayout
    private lateinit var heroContainer: FrameLayout
    private lateinit var continueWatchingContainer: LinearLayout
    private lateinit var continueWatchingScroll: HorizontalScrollView
    private lateinit var recommendationContainer: LinearLayout
    private lateinit var recommendationScroll: HorizontalScrollView
    private lateinit var gridContainer: GridLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sectionTitleView: TextView

    private var currentPage = 1
    private var currentPath = "/latest"
    private var currentCategoryLabel = "TERBARU"
    private var allLoadedItems = mutableListOf<MediaItem>()

    private val prefs by lazy { getSharedPreferences("citeraloka_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    // Senarai Kategori Minimalis (Box & Name sahaja, tiada emoji)
    private val categories = listOf(
        "TERBARU" to "/latest",
        "UTAMA" to "/",
        "UNGGULAN" to "/top-movie-today",
        "POPULER" to "/populer",
        "SIRI TV" to "/latest-series",
        "2026" to "/year/2026",
        "BARAT" to "/country/usa",
        "KOREA" to "/country/south-korea",
        "ACTION" to "/genre/action",
        "HORROR" to "/genre/horror",
        "SCI-FI" to "/genre/sci-fi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Bar Navigasi Atas (Sleek Minimal Box & Name Only)
        val topBarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(24), 0)
        }

        val brandLogo = TextView(this).apply {
            text = "CITERALOKA"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        val brandSub = TextView(this).apply {
            text = "STREAM"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f
        }
        brandLayout.addView(brandLogo)
        brandLayout.addView(brandSub)
        topBar.addView(brandLayout)

        // Butang Kategori (Box & Name Sahaja)
        categories.forEach { (label, path) ->
            topBar.addView(createCategoryBox(label) {
                currentPage = 1
                currentPath = path
                currentCategoryLabel = label
                loadCatalog(currentPath, currentPage)
            })
        }

        topBar.addView(createCategoryBox("CARIAN") { showSearchDialog() })
        topBar.addView(createCategoryBox("DOMAIN") { showDomainDialog() })

        topBarScroll.addView(topBar)
        root.addView(topBarScroll)

        // Kontena Skrol Utama
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(32))
        }

        // 1. Hero Spotlight Banner
        heroContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20)
            }
        }
        contentColumn.addView(heroContainer)

        // 2. Bahagian Continue Watching (Sambung Menonton)
        continueWatchingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(22)
            }
        }
        contentColumn.addView(continueWatchingContainer)

        // 3. Bahagian Rekomendasi / Algoritma Cadangan Pintar
        recommendationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(22)
            }
        }
        contentColumn.addView(recommendationContainer)

        // 4. Bar Status & Tajuk Seksyen Katalog
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }

        sectionTitleView = TextView(this).apply {
            text = "KANDUNGAN: $currentCategoryLabel"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                rightMargin = dp(8)
            }
        }

        statusText = TextView(this).apply {
            text = "Memuatkan..."
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        headerRow.addView(sectionTitleView)
        headerRow.addView(progressBar)
        headerRow.addView(statusText)
        contentColumn.addView(headerRow)

        // 5. Grid Kad Poster Utama
        gridContainer = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = true
        }
        contentColumn.addView(gridContainer)

        // 6. Navigasi Halaman (Minimal Box Text)
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(20))
        }
        paginationRow.addView(createSleekButton("SEBELUM", isPrimary = false) {
            if (currentPage > 1) {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        })
        paginationRow.addView(createSleekButton("SETERUSNYA", isPrimary = true) {
            currentPage++
            loadCatalog(currentPath, currentPage)
        })
        contentColumn.addView(paginationRow)

        scrollView.addView(contentColumn)
        root.addView(scrollView)

        setContentView(root)

        loadCatalog(currentPath, currentPage)
    }

    override fun onResume() {
        super.onResume()
        // Muat semula seksyen Sambung Menonton setiap kali pengguna kembali ke MainActivity
        refreshContinueWatchingSection()
        refreshRecommendationSection()
    }

    private fun createCategoryBox(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 6)
            setPadding(dp(15), dp(7), dp(15), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createSleekButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) ACCENT_TEAL_DARK else BG_CARD_TEAL
        val normalBorder = if (isPrimary) ACCENT_TEAL else BORDER_SUBTLE_TEAL
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(10)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadCatalog(path: String, page: Int) {
        progressBar.visibility = View.VISIBLE
        sectionTitleView.text = "KANDUNGAN: $currentCategoryLabel  •  HALAMAN $page"
        statusText.text = "Memuatkan..."
        gridContainer.removeAllViews()

        executor.execute {
            try {
                val items = LK21Scraper.fetchCatalog(baseUrl, path, page)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (items.isEmpty()) {
                        statusText.text = "Tiada kandungan ditemui."
                    } else {
                        statusText.text = "${items.size} TAJUK TERSEDIA"
                        allLoadedItems.clear()
                        allLoadedItems.addAll(items)

                        renderHeroBanner(items.first())
                        refreshContinueWatchingSection()
                        refreshRecommendationSection()
                        renderCards(items)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Ralat: ${e.message}"
                }
            }
        }
    }

    /**
     * Hero Banner Tayangan Utama (Sleek Minimal Typography & Buttons)
     */
    private fun renderHeroBanner(featured: MediaItem) {
        heroContainer.removeAllViews()

        val bannerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#06222E"),
                    Color.parseColor("#0B3849"),
                    Color.parseColor("#051923")
                )
            ).apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor(BORDER_SUBTLE_TEAL))
            }
            setPadding(dp(24), dp(18), dp(24), dp(18))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val posterCard = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createRoundedBg(BG_DEEP_TEAL, ACCENT_TEAL, 1, 8)
            layoutParams = LinearLayout.LayoutParams(dp(125), dp(180)).apply {
                rightMargin = dp(24)
            }
        }
        loadImageAsync(featured.posterUrl, posterCard)
        bannerCard.addView(posterCard)

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tagBadge = TextView(this).apply {
            text = "CITERALOKA PILIHAN  •  ${featured.episodeOrQuality}  ${if (featured.rating.isNotBlank()) "•  IMDb ${featured.rating}" else ""}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }

        val heroTitle = TextView(this).apply {
            text = featured.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, dp(4))
        }

        val genreSub = TextView(this).apply {
            text = if (featured.genre.isNotBlank()) {
                "GENRE: ${featured.genre.uppercase()}  •  STRIM DEFINISI TINGGI"
            } else {
                "STRIM LANGSUNG HD  •  BEBAS IKLAN  •  KAWALAN PENUH TV"
            }
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(14))
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val playNowBtn = createSleekButton("MAIN SEKARANG", isPrimary = true) {
            onItemClicked(featured, playImmediately = true)
        }

        val detailBtn = createSleekButton("INFO & EPISOD", isPrimary = false) {
            onItemClicked(featured, playImmediately = false)
        }

        btnRow.addView(playNowBtn)
        btnRow.addView(detailBtn)

        infoCol.addView(tagBadge)
        infoCol.addView(heroTitle)
        infoCol.addView(genreSub)
        infoCol.addView(btnRow)

        bannerCard.addView(infoCol)
        heroContainer.addView(bannerCard)
    }

    /**
     * 3. Bahagian Sambung Menonton (Continue Watching)
     */
    private fun refreshContinueWatchingSection() {
        val historyList = ContinueWatchingManager.getList(this)
        if (historyList.isEmpty()) {
            continueWatchingContainer.visibility = View.GONE
            continueWatchingContainer.removeAllViews()
            return
        }

        continueWatchingContainer.visibility = View.VISIBLE
        continueWatchingContainer.removeAllViews()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        val sectionTitle = TextView(this).apply {
            text = "SAMBUNG MENONTON"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val countText = TextView(this).apply {
            text = "${historyList.size} TAJUK DALAM PROGRES"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        headerRow.addView(sectionTitle)
        headerRow.addView(countText)
        continueWatchingContainer.addView(headerRow)

        continueWatchingScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val itemsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        historyList.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isFocusableInTouchMode = false
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(6), dp(6), dp(6), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(12)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.background = createRoundedBg("#114254", ACCENT_TEAL, 2, 8)
                        v.scaleX = 1.05f
                        v.scaleY = 1.05f
                    } else {
                        v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                    }
                }
                setOnClickListener {
                    // Terus mainkan dari saat terakhir (Resume)
                    launchVideoPlayer(
                        pageUrl = item.pageUrl,
                        embedUrl = item.embedUrl,
                        title = item.title,
                        serverName = item.serverName,
                        posterUrl = item.posterUrl,
                        genre = item.genre,
                        resumeSec = item.currentSec
                    )
                }
            }

            // Bekas Poster dengan Progress Bar di bahagian bawah
            val posterFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(115)
                )
            }

            val posterView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            loadImageAsync(item.posterUrl, posterView)
            posterFrame.addView(posterView)

            // Baris Kemajuan Tontonan (Teal Progress Bar di Bawah Poster)
            val progressTrack = View(this).apply {
                setBackgroundColor(Color.parseColor("#99031117"))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(4),
                    Gravity.BOTTOM
                )
            }
            posterFrame.addView(progressTrack)

            val progressBarFill = View(this).apply {
                setBackgroundColor(Color.parseColor(ACCENT_TEAL))
                val pct = item.progressPercent.coerceIn(5, 100)
                layoutParams = FrameLayout.LayoutParams(
                    (dp(168) * (pct / 100f)).toInt(),
                    dp(4),
                    Gravity.BOTTOM
                )
            }
            posterFrame.addView(progressBarFill)

            // Badge Masa Berbaki / Episod
            val badgeView = TextView(this).apply {
                text = if (item.episodeLabel.isNotBlank()) {
                    item.episodeLabel.uppercase()
                } else if (item.remainingMinutes > 0) {
                    "BAKI ${item.remainingMinutes}M"
                } else {
                    "${item.progressPercent}%"
                }
                setTextColor(Color.parseColor(BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 4)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            }
            posterFrame.addView(badgeView)

            val titleView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(6), dp(4), 0)
            }

            val resumeActionText = TextView(this).apply {
                text = "SAMBUNG (${formatTime(item.currentSec)})"
                setTextColor(Color.parseColor(ACCENT_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(2), dp(4), 0)
            }

            card.addView(posterFrame)
            card.addView(titleView)
            card.addView(resumeActionText)
            itemsRow.addView(card)
        }

        continueWatchingScroll.addView(itemsRow)
        continueWatchingContainer.addView(continueWatchingScroll)
    }

    /**
     * 4. Bahagian Rekomendasi Pintar (User Recommendation Engine)
     */
    private fun refreshRecommendationSection() {
        if (allLoadedItems.isEmpty()) return

        val recommended = UserRecommendationManager.getRecommendedItems(this, allLoadedItems)
        if (recommended.isEmpty()) {
            recommendationContainer.visibility = View.GONE
            recommendationContainer.removeAllViews()
            return
        }

        recommendationContainer.visibility = View.VISIBLE
        recommendationContainer.removeAllViews()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        val titleView = TextView(this).apply {
            text = "DISYORKAN UNTUK ANDA"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subInfo = TextView(this).apply {
            text = "PADANAN KEGEMARAN ANDA"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.05f
        }

        headerRow.addView(titleView)
        headerRow.addView(subInfo)
        recommendationContainer.addView(headerRow)

        recommendationScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val itemsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        recommended.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isFocusableInTouchMode = false
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(6), dp(6), dp(6), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(155), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(12)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.background = createRoundedBg("#114254", ACCENT_TEAL, 2, 8)
                        v.scaleX = 1.05f
                        v.scaleY = 1.05f
                    } else {
                        v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                    }
                }
                setOnClickListener {
                    onItemClicked(item, playImmediately = !item.isSeries)
                }
            }

            val posterFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(210)
                )
            }

            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            loadImageAsync(item.posterUrl, imageView)
            posterFrame.addView(imageView)

            if (item.rating.isNotBlank()) {
                val ratingView = TextView(this).apply {
                    text = "IMDb ${item.rating}"
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg("#D904151F", BORDER_SUBTLE_TEAL, 1, 4)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                }
                posterFrame.addView(ratingView)
            }

            val recBadge = TextView(this).apply {
                text = "PILIHAN"
                setTextColor(Color.parseColor(BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 4)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            }
            posterFrame.addView(recBadge)

            val titleView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(6), dp(4), 0)
            }

            val subAction = TextView(this).apply {
                text = if (item.isSeries) "PILIH EPISOD" else "MAIN SEKARANG"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(4), dp(2), dp(4), 0)
            }

            card.addView(posterFrame)
            card.addView(titleView)
            card.addView(subAction)
            itemsRow.addView(card)
        }

        recommendationScroll.addView(itemsRow)
        recommendationContainer.addView(recommendationScroll)
    }

    /**
     * Grid Katalog Utama
     */
    private fun renderCards(items: List<MediaItem>) {
        gridContainer.removeAllViews()
        val displayMetrics = resources.displayMetrics
        val cols = if (displayMetrics.widthPixels >= dp(900)) 5 else 3
        gridContainer.columnCount = cols
        val cardWidth = (displayMetrics.widthPixels - dp(76)) / cols - dp(12)
        val cardHeight = (cardWidth * 1.44).toInt()

        items.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isFocusableInTouchMode = false
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(6), dp(6), dp(6), dp(10))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.background = createRoundedBg("#114254", ACCENT_TEAL, 2, 8)
                        v.scaleX = 1.05f
                        v.scaleY = 1.05f
                    } else {
                        v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                    }
                }
                setOnClickListener {
                    onItemClicked(item, playImmediately = !item.isSeries)
                }
                setOnLongClickListener {
                    onItemClicked(item, playImmediately = false)
                    true
                }
            }

            val posterContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cardHeight
                )
            }

            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            posterContainer.addView(imageView)

            if (item.episodeOrQuality.isNotBlank()) {
                val badgeView = TextView(this).apply {
                    text = item.episodeOrQuality.uppercase()
                    setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 4)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    ).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                }
                posterContainer.addView(badgeView)
            }

            if (item.rating.isNotBlank()) {
                val ratingView = TextView(this).apply {
                    text = "IMDb ${item.rating}"
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg("#D904151F", BORDER_SUBTLE_TEAL, 1, 4)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                }
                posterContainer.addView(ratingView)
            }

            val titleView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(8), dp(4), 0)
            }

            val subActionView = TextView(this).apply {
                text = if (item.isSeries) "PILIH EPISOD" else "MAIN SEKARANG"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(4), dp(2), dp(4), 0)
            }

            card.addView(posterContainer)
            card.addView(titleView)
            card.addView(subActionView)
            gridContainer.addView(card)

            loadImageAsync(item.posterUrl, imageView)
        }
    }

    private fun onItemClicked(item: MediaItem, playImmediately: Boolean) {
        // Rakam tabiat pengguna untuk cadangan pintar
        UserRecommendationManager.recordView(this, item.genre, item.url, weightBoost = 1)

        if (playImmediately && !item.isSeries) {
            launchVideoPlayer(
                pageUrl = item.url,
                embedUrl = "",
                title = item.title,
                serverName = "P2P Direct",
                posterUrl = item.posterUrl,
                genre = item.genre
            )
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = "Memuatkan maklumat: ${item.title}..."

        executor.execute {
            try {
                val detail = LK21Scraper.fetchDetail(baseUrl, item.url)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Sedia: ${detail.title}"
                    if (playImmediately && detail.episodes.isEmpty()) {
                        launchVideoPlayer(
                            pageUrl = detail.url,
                            embedUrl = detail.mainEmbedUrl ?: "",
                            title = detail.title,
                            serverName = detail.mirrors.firstOrNull()?.name ?: "P2P Direct",
                            posterUrl = detail.posterUrl,
                            genre = detail.genre,
                            mirrors = detail.mirrors
                        )
                    } else {
                        showNetflixDetailModal(detail)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    launchVideoPlayer(
                        pageUrl = item.url,
                        embedUrl = "",
                        title = item.title,
                        serverName = "P2P Direct",
                        posterUrl = item.posterUrl,
                        genre = item.genre
                    )
                }
            }
        }
    }

    private fun showNetflixDetailModal(detail: MediaDetail) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val modalRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 2, 10)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            layoutParams = ViewGroup.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = detail.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaView = TextView(this).apply {
            text = "${detail.quality}  ${if (detail.rating.isNotBlank()) "•  IMDb ${detail.rating}" else ""}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }

        val plotView = TextView(this).apply {
            text = detail.plot
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(16))
        }

        modalRoot.addView(titleView)
        modalRoot.addView(metaView)
        modalRoot.addView(plotView)

        val listScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(270)
            )
        }

        val optionsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Butang Mainkan Terus
        if (!detail.mainEmbedUrl.isNullOrBlank()) {
            val playBtn = createSleekButton("MAIN VIDEO SEKARANG", isPrimary = true) {
                dialog.dismiss()
                launchVideoPlayer(
                    pageUrl = detail.url,
                    embedUrl = detail.mainEmbedUrl,
                    title = detail.title,
                    serverName = detail.mirrors.firstOrNull()?.name ?: "P2P Direct",
                    posterUrl = detail.posterUrl,
                    genre = detail.genre,
                    mirrors = detail.mirrors
                )
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            optionsCol.addView(playBtn)
            playBtn.requestFocus()
        }

        // 2. Senarai Episod
        if (detail.episodes.isNotEmpty()) {
            val epHeader = TextView(this).apply {
                text = "SENARAI EPISOD (${detail.episodes.size} EPISOD):"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                setPadding(0, dp(6), 0, dp(8))
            }
            optionsCol.addView(epHeader)

            detail.episodes.forEach { ep ->
                val epBtn = createSleekButton(ep.label.uppercase(), isPrimary = false) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = ep.url,
                        embedUrl = "",
                        title = "${detail.title} • ${ep.label}",
                        serverName = "P2P Direct",
                        posterUrl = detail.posterUrl,
                        genre = detail.genre,
                        episodeLabel = ep.label
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                optionsCol.addView(epBtn)
            }
        }

        // 3. Pilihan Server
        if (detail.mirrors.size > 1) {
            val srvHeader = TextView(this).apply {
                text = "PILIHAN SERVER:"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                setPadding(0, dp(10), 0, dp(8))
            }
            optionsCol.addView(srvHeader)

            detail.mirrors.forEach { mirror ->
                val srvBtn = createSleekButton("SERVER: ${mirror.name}", isPrimary = false) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = detail.url,
                        embedUrl = mirror.embedUrl,
                        title = detail.title,
                        serverName = mirror.name,
                        posterUrl = detail.posterUrl,
                        genre = detail.genre,
                        mirrors = detail.mirrors
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                optionsCol.addView(srvBtn)
            }
        }

        listScroll.addView(optionsCol)
        modalRoot.addView(listScroll)

        val closeBtn = createSleekButton("TUTUP", isPrimary = false) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                gravity = Gravity.END
            }
        }
        modalRoot.addView(closeBtn)

        dialog.setContentView(modalRoot)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun launchVideoPlayer(
        pageUrl: String,
        embedUrl: String,
        title: String,
        serverName: String,
        posterUrl: String = "",
        genre: String = "",
        episodeLabel: String = "",
        resumeSec: Int = 0,
        mirrors: List<MirrorItem> = emptyList()
    ) {
        val mirrorNames = ArrayList(mirrors.map { it.name })
        val mirrorUrls = ArrayList(mirrors.map { it.embedUrl })

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("PAGE_URL", pageUrl)
            putExtra("EMBED_URL", embedUrl)
            putExtra("TITLE", title)
            putExtra("BASE_URL", baseUrl)
            putExtra("SERVER_NAME", serverName)
            putExtra("POSTER_URL", posterUrl)
            putExtra("GENRE", genre)
            putExtra("EPISODE_LABEL", episodeLabel)
            putExtra("RESUME_SEC", resumeSec)
            putStringArrayListExtra("MIRROR_NAMES", mirrorNames)
            putStringArrayListExtra("MIRROR_URLS", mirrorUrls)
        }
        startActivity(intent)
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Taip tajuk filem atau siri..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("CARIAN KANDUNGAN")
            .setView(input)
            .setPositiveButton("CARI") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    currentPage = 1
                    currentCategoryLabel = "CARIAN: $q"
                    currentPath = "/search?s=${URLEncoder.encode(q, "UTF-8")}"
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun showDomainDialog() {
        val input = EditText(this).apply {
            setText(baseUrl)
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("TETAPAN DOMAIN")
            .setMessage("Kemas kini URL domain penstriman jika terdapat pertukaran domain:")
            .setView(input)
            .setPositiveButton("SIMPAN") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.startsWith("http")) {
                    baseUrl = newUrl
                    currentPage = 1
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun loadImageAsync(url: String, imageView: ImageView) {
        if (url.isBlank()) return
        imageView.tag = url
        val cached = imageCache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        executor.execute {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Referer", baseUrl)
                }
                conn.inputStream.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        imageCache.put(url, bmp)
                        mainHandler.post {
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun createRoundedBg(
        fillHex: String,
        strokeHex: String,
        strokeWidthDp: Int,
        radiusDp: Int
    ): GradientDrawable {
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
