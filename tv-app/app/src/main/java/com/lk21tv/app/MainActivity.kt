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
import java.util.concurrent.Callable
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
    private val imageCache = object : LruCache<String, Bitmap>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var mainScrollView: ScrollView
    private lateinit var contentColumn: LinearLayout
    private lateinit var heroContainer: FrameLayout
    private lateinit var heroBackdropView: ImageView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView
    private lateinit var sourceModeBtn: Button

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
    private var currentCategoryLabel = "LATEST"
    private var allLoadedItems = mutableListOf<MediaItem>()
    private var activeHeroItem: MediaItem? = null
    private var pendingHeroRunnable: Runnable? = null

    private val prefs by lazy { getSharedPreferences("citeraloka_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    private var sourceMode: String
        get() = prefs.getString("source_mode", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("source_mode", value).apply()

    // Minimalist Category List (Box & Name only, no emojis, 100% English)
    private val categories = listOf(
        "LATEST" to "/latest",
        "HOME" to "/",
        "FEATURED" to "/top-movie-today",
        "POPULAR" to "/populer",
        "TV SERIES" to "/latest-series",
        "2026" to "/year/2026",
        "WESTERN" to "/country/usa",
        "KOREAN" to "/country/south-korea",
        "ACTION" to "/genre/action",
        "HORROR" to "/genre/horror",
        "SCI-FI" to "/genre/sci-fi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tiada profil aktif (cth. selepas proses dipulihkan) -> kembali ke skrin "Who's watching?"
        if (ProfileManager.getActive(this) == null) {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
            return
        }

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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(24), 0)
        }

        // Ikon Rasmi CiteraLoka (Batik Gold 'C')
        val iconResId = resources.getIdentifier("ic_tv_logo", "drawable", packageName)
        if (iconResId != 0) {
            val brandIcon = ImageView(this).apply {
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    rightMargin = dp(12)
                }
            }
            brandLayout.addView(brandIcon)
        }

        val brandTextGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
        brandTextGroup.addView(brandLogo)
        brandTextGroup.addView(brandSub)
        brandLayout.addView(brandTextGroup)
        topBar.addView(brandLayout)

        // Profil aktif (Netflix-style): tekan untuk tukar / urus profil
        topBar.addView(createProfileChip())

        // Pastikan mod sumber sentiasa AUTO (Gabungan Pintar LK21 + Watch32) apabila butang disembunyikan
        sourceMode = "AUTO"

        // Butang Tukar Sumber Pelayan (disembunyikan mengikut tetapan UI terkini)
        sourceModeBtn = createCategoryBox(getSourceModeButtonLabel()) {
            showSourceModeDialog()
        }.apply {
            visibility = View.GONE
        }
        topBar.addView(sourceModeBtn)

        // Butang Kategori (Box & Name Sahaja)
        categories.forEach { (label, path) ->
            topBar.addView(createCategoryBox(label) {
                currentPage = 1
                currentPath = path
                currentCategoryLabel = label
                loadCatalog(currentPath, currentPage)
            })
        }

        topBar.addView(createCategoryBox("SEARCH") { showSearchDialog() })
        val domainBtn = createCategoryBox("DOMAIN") { showDomainDialog() }.apply {
            visibility = View.GONE
        }
        topBar.addView(domainBtn)
        topBar.addView(createCategoryBox("UPDATE") {
            AppUpdateManager.checkUpdate(this, isManual = true)
        })

        topBarScroll.addView(topBar)
        root.addView(topBarScroll)

        // Kontena Skrol Utama (Hero Spotlight di bahagian atas skrol supaya ikut bergerak naik apabila skrol ke bawah)
        mainScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val scrollBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Live Hero Spotlight (Berada di atas halaman & hilang apabila skrol ke bawah)
        heroContainer = buildPinnedHeroSpotlightStage()
        scrollBody.addView(heroContainer)

        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(32))
        }

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
            text = currentCategoryLabel
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
            text = "Loading..."
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        headerRow.addView(sectionTitleView)
        headerRow.addView(progressBar)
        headerRow.addView(statusText)
        contentColumn.addView(headerRow)

        // 5. Poster Card Grid
        gridContainer = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = true
        }
        contentColumn.addView(gridContainer)

        // 6. Pagination Navigation (Minimal Box Text)
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(20))
        }
        paginationRow.addView(createSleekButton("PREVIOUS", isPrimary = false) {
            if (currentPage > 1) {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        })
        paginationRow.addView(createSleekButton("NEXT", isPrimary = true) {
            currentPage++
            loadCatalog(currentPath, currentPage)
        })
        contentColumn.addView(paginationRow)

        scrollBody.addView(contentColumn)
        mainScrollView.addView(scrollBody)
        root.addView(mainScrollView)

        setContentView(root)

        loadCatalog(currentPath, currentPage)

        // Semak kemas kini OTA & Remote Config secara senyap semasa pelancaran app
        AppUpdateManager.checkUpdate(this, isManual = false) { newDomain ->
            if (newDomain.isNotBlank() && newDomain != baseUrl) {
                baseUrl = newDomain
                mainHandler.post {
                    loadCatalog(currentPath, currentPage)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Muat semula seksyen Sambung Menonton setiap kali pengguna kembali ke MainActivity
        refreshContinueWatchingSection()
        refreshRecommendationSection()
    }

    private fun createProfileChip(): LinearLayout {
        val profile = ProfileManager.getActive(this)
        val colorHex = profile?.let { ProfileManager.colorOf(it) } ?: ACCENT_TEAL
        val displayName = profile?.name ?: "PROFILE"

        val avatar = TextView(this).apply {
            text = ProfileManager.initialOf(displayName)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedBg(colorHex, colorHex, 0, 6)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(8) }
        }
        val nameView = TextView(this).apply {
            text = displayName.uppercase()
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            maxLines = 1
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 6)
            setPadding(dp(8), dp(5), dp(14), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(10) }
            addView(avatar)
            addView(nameView)
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    nameView.setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    nameView.setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
            }
        }
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

    private fun getSourceModeButtonLabel(): String {
        return when (sourceMode) {
            "LK21" -> "SOURCE: LK21"
            "WATCH32" -> "SOURCE: WATCH32"
            else -> "SOURCE: AUTO"
        }
    }

    private fun showSourceModeDialog() {
        val options = arrayOf(
            "SMART COMBINED (AUTO-FALLBACK + DUAL CATALOG)",
            "PRIMARY SERVER (WITH WATCH32 BACKUP)",
            "WATCH32 GLOBAL (FULL TMDB CATALOG + MULTI-SERVER)"
        )
        val modes = arrayOf("AUTO", "LK21", "WATCH32")
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("SELECT CONTENT SOURCE")
            .setItems(options) { _, which ->
                sourceMode = modes[which]
                sourceModeBtn.text = getSourceModeButtonLabel()
                currentPage = 1
                loadCatalog(currentPath, currentPage)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun normalizeTitleKey(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun loadCatalog(path: String, page: Int) {
        val mode = sourceMode
        progressBar.visibility = View.VISIBLE
        sectionTitleView.text = "$currentCategoryLabel  •  PAGE $page"
        statusText.text = "Loading..."
        gridContainer.removeAllViews()

        executor.execute {
            try {
                var usedAutoFallback = false
                val combined = mutableListOf<MediaItem>()

                when (mode) {
                    "WATCH32" -> {
                        combined.addAll(Watch32Scraper.fetchCatalog(path, page))
                    }
                    "LK21" -> {
                        val lkItems = try {
                            LK21Scraper.fetchCatalog(baseUrl, path, page)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (lkItems.isNotEmpty()) {
                            combined.addAll(lkItems)
                        } else {
                            // Auto-fallback ke Watch32 jika LK21 down/disekat atau carian tiada dalam LK21
                            val w32Items = Watch32Scraper.fetchCatalog(path, page)
                            if (w32Items.isNotEmpty()) {
                                usedAutoFallback = true
                                combined.addAll(w32Items)
                            }
                        }
                    }
                    else -> {
                        // Mod AUTO (Gabungan Pintar): Tarik LK21 & Watch32 secara selari!
                        val w32Future = executor.submit(Callable {
                            try {
                                Watch32Scraper.fetchCatalog(path, page)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        })
                        val lkItems = try {
                            LK21Scraper.fetchCatalog(baseUrl, path, page)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val w32Items = try {
                            w32Future.get()
                        } catch (_: Exception) {
                            emptyList()
                        }

                        if (lkItems.isEmpty() && w32Items.isNotEmpty()) {
                            usedAutoFallback = true
                            combined.addAll(w32Items)
                        } else {
                            val seenKeys = HashSet<String>()
                            lkItems.forEach { item ->
                                seenKeys.add(normalizeTitleKey(item.title))
                                combined.add(item)
                            }
                            // Tambah cerita eksklusif dari Watch32 yang tiada dalam LK21
                            w32Items.forEach { item ->
                                val key = normalizeTitleKey(item.title)
                                if (key.isNotBlank() && !seenKeys.contains(key)) {
                                    seenKeys.add(key)
                                    combined.add(item)
                                }
                            }
                        }
                    }
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (combined.isEmpty()) {
                        statusText.text = "No titles found."
                    } else {
                        statusText.text = when {
                            usedAutoFallback -> "WATCH32 AUTO-FALLBACK  •  ${combined.size} TITLES"
                            mode == "WATCH32" -> "WATCH32 GLOBAL  •  ${combined.size} TITLES"
                            mode == "LK21" -> "LK21  •  ${combined.size} TITLES"
                            else -> "${combined.size} TITLES"
                        }
                        allLoadedItems.clear()
                        allLoadedItems.addAll(combined)

                        updateHeroSpotlightImmediate(combined.first())
                        scheduleHeroSpotlight(combined.first(), delayMs = 0L)
                        refreshContinueWatchingSection()
                        refreshRecommendationSection()
                        renderCards(combined)
                        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Error: ${e.message}"
                }
            }
        }
    }

    /**
     * Live Hero Spotlight (Expanded Cinematic Height - Dark Teal, No Emojis, 100% English)
     * Scrolls up naturally when scrolling down the catalog.
     */
    private fun buildPinnedHeroSpotlightStage(): FrameLayout {
        val dm = resources.displayMetrics
        val stageHeight = (dm.heightPixels * 0.64f).toInt().coerceIn(dp(290), dp(395))
        val isCompactHeight = dm.heightPixels < dp(460)

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                stageHeight
            )
        }

        // Layer 1: 16:9 Widescreen Backdrop on the right
        val backdropWidth = (dm.widthPixels * 0.75f).toInt()
        heroBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                backdropWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }
        stage.addView(heroBackdropView)

        // Layer 2A: Left-to-Right Vignette
        val leftVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#FF031117"),
                    Color.parseColor("#FA031117"),
                    Color.parseColor("#CC031117"),
                    Color.parseColor("#4D031117"),
                    Color.parseColor("#0D031117")
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        stage.addView(leftVignette)

        // Layer 2B: Bottom-to-Top Vignette
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
                dp(76),
                Gravity.BOTTOM
            )
        }
        stage.addView(bottomVignette)

        // Subtle Teal Divider at Bottom of Hero Stage
        val bottomDivider = View(this).apply {
            setBackgroundColor(Color.parseColor(BORDER_SUBTLE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
                Gravity.BOTTOM
            )
        }
        stage.addView(bottomDivider)

        // Layer 3: Live Info Column & Official ClearLogo PNG on the Left
        val infoWidth = (dm.widthPixels * 0.62f).toInt().coerceAtLeast(dp(320))
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(30), dp(18), dp(18), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                infoWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.BOTTOM
            )
        }

        // Official ClearLogo PNG (from TMDB)
        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
            maxWidth = dp(285)
            maxHeight = if (isCompactHeight) dp(52) else dp(72)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dp(275),
                if (isCompactHeight) dp(52) else dp(70)
            ).apply {
                bottomMargin = dp(8)
            }
        }
        infoCol.addView(heroLogoView)

        // Stylized Text Title Fallback
        heroTitleView = TextView(this).apply {
            text = "CITERALOKA"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompactHeight) 22f else 28f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(5))
        }
        infoCol.addView(heroTitleView)

        // Metadata Row: Genre • Runtime • Release Date
        heroMetaView = TextView(this).apply {
            text = "Loading details..."
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8))
        }
        infoCol.addView(heroMetaView)

        // Minimalist Pill Badges Row (Status, Quality, IMDb, Source - No Emojis)
        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        infoCol.addView(heroBadgeRow)

        // Synopsis (3-4 Lines)
        heroPlotView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompactHeight) 11.5f else 13f)
            maxLines = if (isCompactHeight) 3 else 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.18f)
            setPadding(0, 0, 0, dp(14))
        }
        infoCol.addView(heroPlotView)

        // Quick Action Row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playHeroBtn = createCompactHeroButton("PLAY NOW", isPrimary = true) {
            activeHeroItem?.let { onItemClicked(it, playImmediately = !it.isSeries) }
        }
        val infoHeroBtn = createCompactHeroButton("INFO & SERVER", isPrimary = false) {
            activeHeroItem?.let { onItemClicked(it, playImmediately = false) }
        }
        actionRow.addView(playHeroBtn)
        actionRow.addView(infoHeroBtn)
        infoCol.addView(actionRow)

        stage.addView(infoCol)
        return stage
    }

    private fun createCompactHeroButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) ACCENT_TEAL_DARK else "#CC071F29"
        val normalBorder = if (isPrimary) ACCENT_TEAL else BORDER_SUBTLE_TEAL
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            minHeight = 0
            minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(10)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 2, 5)
                    v.scaleX = 1.04f
                    v.scaleY = 1.04f
                } else {
                    (v as Button).setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 5)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createHeroPillBadge(textLabel: String, textHex: String, bgHex: String, borderHex: String): TextView {
        return TextView(this).apply {
            text = textLabel
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
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

    private fun updateHeroSpotlightImmediate(item: MediaItem) {
        activeHeroItem = item
        val cleanTitle = item.title.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "").trim().ifBlank { item.title }
        heroLogoView.setImageDrawable(null)
        heroLogoView.visibility = View.GONE
        heroTitleView.visibility = View.VISIBLE
        heroTitleView.text = cleanTitle

        val initialMeta = listOf(
            item.genre.replace(",", " •").trim(),
            item.releaseInfo
        ).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "HD Streaming  •  Ad-Free" }
        heroMetaView.text = initialMeta

        renderHeroBadges(
            status = item.statusBadge.ifBlank { if (item.isSeries) "SERIES" else "RELEASED" },
            quality = item.episodeOrQuality.replace("W32 • ", "").ifBlank { "HD" },
            rating = item.rating,
            source = item.source
        )

        heroPlotView.text = item.plot.ifBlank { "Loading synopsis and full details..." }

        val initialBackdrop = item.backdropUrl.ifBlank { item.posterUrl }
        if (initialBackdrop.isNotBlank()) {
            loadImageAsync(initialBackdrop, heroBackdropView, crossfade = true)
        }
    }

    private fun renderHeroBadges(status: String, quality: String, rating: String, source: String) {
        heroBadgeRow.removeAllViews()
        if (status.isNotBlank()) {
            heroBadgeRow.addView(createHeroPillBadge(status.uppercase(), ACCENT_TEAL, "#CC071F29", ACCENT_TEAL_DARK))
        }
        if (quality.isNotBlank()) {
            heroBadgeRow.addView(createHeroPillBadge(quality.uppercase(), TEXT_PRIMARY, BG_CARD_TEAL, BORDER_SUBTLE_TEAL))
        }
        if (rating.isNotBlank()) {
            heroBadgeRow.addView(createHeroPillBadge("IMDb $rating", "#FACC15", "#D904151F", BORDER_SUBTLE_TEAL))
        }
        if (source.isNotBlank()) {
            val srcBorder = if (source == "WATCH32") ACCENT_TEAL else BORDER_SUBTLE_TEAL
            heroBadgeRow.addView(createHeroPillBadge(source.uppercase(), TEXT_SECONDARY, "#CC061C24", srcBorder))
        }
    }

    private fun scheduleHeroSpotlight(item: MediaItem, delayMs: Long = 110L) {
        pendingHeroRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            updateHeroSpotlightImmediate(item)
            executor.execute {
                val enriched = Watch32Scraper.enrichHeroSpotlight(item)
                mainHandler.post {
                    if (activeHeroItem?.url != item.url) return@post

                    heroMetaView.text = enriched.metaLine
                    heroPlotView.text = enriched.plot
                    renderHeroBadges(
                        status = enriched.statusBadge,
                        quality = enriched.qualityBadge,
                        rating = enriched.rating,
                        source = enriched.sourceBadge
                    )

                    if (enriched.backdropUrl.isNotBlank()) {
                        loadImageAsync(enriched.backdropUrl, heroBackdropView, crossfade = true)
                    }

                    if (enriched.logoUrl.isNotBlank()) {
                        loadImageAsync(enriched.logoUrl, heroLogoView, crossfade = false) { success ->
                            if (success && activeHeroItem?.url == item.url) {
                                heroLogoView.visibility = View.VISIBLE
                                heroTitleView.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
        pendingHeroRunnable = task
        if (delayMs <= 0L) {
            task.run()
        } else {
            mainHandler.postDelayed(task, delayMs)
        }
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
            text = "CONTINUE WATCHING FOR ${ProfileManager.getActive(this@MainActivity)?.name?.uppercase() ?: "YOU"}"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val countText = TextView(this).apply {
            text = "${historyList.size} IN PROGRESS"
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
                        val cwHeroItem = MediaItem(
                            title = item.title,
                            url = item.pageUrl,
                            posterUrl = item.posterUrl,
                            rating = "",
                            episodeOrQuality = if (item.episodeLabel.isNotBlank()) item.episodeLabel else "${item.remainingMinutes}M LEFT",
                            genre = item.genre,
                            isSeries = item.episodeLabel.isNotBlank(),
                            source = if (Watch32Scraper.isWatch32Uri(item.pageUrl)) "WATCH32" else "LK21",
                            statusBadge = "WATCHED ${item.progressPercent}%"
                        )
                        scheduleHeroSpotlight(cwHeroItem)
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
                        episodeLabel = item.episodeLabel,
                        resumeSec = item.currentSec
                    )
                }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("REMOVE FROM CONTINUE WATCHING?")
                        .setMessage(item.title)
                        .setPositiveButton("REMOVE") { _, _ ->
                            ContinueWatchingManager.remove(this@MainActivity, item.pageUrl)
                            refreshContinueWatchingSection()
                        }
                        .setNegativeButton("CANCEL", null)
                        .show()
                    true
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
                    item.episodeLabel.replace(Regex("(?i)episod(e)?"), "EP").uppercase()
                } else if (item.remainingMinutes > 0) {
                    "${item.remainingMinutes}M LEFT"
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
                text = "RESUME (${formatTime(item.currentSec)})"
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
            text = "RECOMMENDED FOR YOU"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val subInfo = TextView(this).apply {
            text = "TOP PICKS FOR ${ProfileManager.getActive(this@MainActivity)?.name?.uppercase() ?: "YOU"}"
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
                        scheduleHeroSpotlight(item)
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
                text = "TOP PICK"
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
                text = if (item.isSeries) "EPISODES" else "PLAY NOW"
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
                        scheduleHeroSpotlight(item)
                    } else {
                        v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                    }
                }
                setOnClickListener {
                    scheduleHeroSpotlight(item, delayMs = 0L)
                    onItemClicked(item, playImmediately = !item.isSeries)
                }
                setOnLongClickListener {
                    scheduleHeroSpotlight(item, delayMs = 0L)
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
                text = if (item.isSeries) "EPISODES" else "PLAY NOW"
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

        // Untuk Siri TV (Series sahaja): terus masuk ke Halaman Pilihan Siri Gaya Netflix (Hero + Musim + Episod)
        if (item.isSeries) {
            launchSeriesDetailPage(item)
            return
        }

        if (playImmediately && !item.isSeries) {
            val defaultSrv = if (Watch32Scraper.isWatch32Uri(item.url)) {
                "W32 • VidLink HD"
            } else {
                "P2P Direct"
            }
            launchVideoPlayer(
                pageUrl = item.url,
                embedUrl = "",
                title = item.title,
                serverName = defaultSrv,
                posterUrl = item.posterUrl,
                genre = item.genre
            )
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = "Loading: ${item.title}..."

        executor.execute {
            try {
                val detail: MediaDetail = if (Watch32Scraper.isWatch32Uri(item.url)) {
                    Watch32Scraper.fetchDetail(item.url)
                } else {
                    val lkDetail = try {
                        LK21Scraper.fetchDetail(baseUrl, item.url)
                    } catch (_: Exception) {
                        null
                    }
                    if (lkDetail != null && (!lkDetail.mainEmbedUrl.isNullOrBlank() || lkDetail.mirrors.isNotEmpty() || lkDetail.episodes.isNotEmpty())) {
                        val mergedMirrors = lkDetail.mirrors.toMutableList()
                        val w32Backups = Watch32Scraper.findBackupMirrorsForLk21(
                            rawTitle = lkDetail.title,
                            isSeries = lkDetail.episodes.isNotEmpty() || item.isSeries
                        )
                        w32Backups.forEach { bm ->
                            if (mergedMirrors.none { it.embedUrl == bm.embedUrl }) {
                                mergedMirrors.add(bm)
                            }
                        }
                        Watch32Scraper.enrichMovieDetail(lkDetail.copy(mirrors = mergedMirrors))
                    } else {
                        Watch32Scraper.findFallbackDetail(item.title, preferSeries = item.isSeries)
                            ?: throw IllegalStateException("Server unreachable")
                    }
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Ready (${detail.source}): ${detail.title}"
                    if (detail.episodes.isNotEmpty()) {
                        launchSeriesDetailPage(
                            item.copy(
                                title = detail.title,
                                posterUrl = detail.posterUrl.ifBlank { item.posterUrl },
                                backdropUrl = detail.backdropUrl.ifBlank { item.backdropUrl },
                                logoUrl = detail.logoUrl.ifBlank { item.logoUrl },
                                plot = detail.plot,
                                rating = detail.rating.ifBlank { item.rating },
                                genre = detail.genre.ifBlank { item.genre },
                                source = detail.source,
                                isSeries = true
                            )
                        )
                    } else if (playImmediately) {
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
                    val defaultSrv = if (Watch32Scraper.isWatch32Uri(item.url)) "W32 • VidLink HD" else "P2P Direct"
                    launchVideoPlayer(
                        pageUrl = item.url,
                        embedUrl = "",
                        title = item.title,
                        serverName = defaultSrv,
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

        // 1. Direct Play Button
        if (!detail.mainEmbedUrl.isNullOrBlank()) {
            val playBtn = createSleekButton("PLAY NOW", isPrimary = true) {
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

        // 2. Episode List
        if (detail.episodes.isNotEmpty()) {
            val epHeader = TextView(this).apply {
                text = "EPISODES (${detail.episodes.size}):"
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
                    val epSrv = if (Watch32Scraper.isWatch32Uri(ep.url)) "W32 • VidLink HD" else "P2P Direct"
                    launchVideoPlayer(
                        pageUrl = ep.url,
                        embedUrl = "",
                        title = "${detail.title} • ${ep.label}",
                        serverName = epSrv,
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

        // 3. Server Selection
        if (detail.mirrors.size > 1) {
            val srvHeader = TextView(this).apply {
                text = "SELECT SERVER:"
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

        val closeBtn = createSleekButton("CLOSE", isPrimary = false) {
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

        val dm = resources.displayMetrics
        val modalWidth = (dm.widthPixels * 0.70f).toInt().coerceIn(dp(560), dp(820))

        dialog.setContentView(modalRoot)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun launchSeriesDetailPage(item: MediaItem) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("PAGE_URL", item.url)
            putExtra("BASE_URL", baseUrl)
            putExtra("TITLE", item.title)
            putExtra("POSTER_URL", item.posterUrl)
            putExtra("BACKDROP_URL", item.backdropUrl)
            putExtra("LOGO_URL", item.logoUrl)
            putExtra("PLOT", item.plot)
            putExtra("RATING", item.rating)
            putExtra("GENRE", item.genre)
            putExtra("SOURCE", item.source)
        }
        startActivity(intent)
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
            hint = "Search movies or TV series..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("SEARCH TITLES")
            .setView(input)
            .setPositiveButton("SEARCH") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    currentPage = 1
                    currentCategoryLabel = "SEARCH: $q"
                    currentPath = "/search?s=${URLEncoder.encode(q, "UTF-8")}"
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showDomainDialog() {
        val input = EditText(this).apply {
            setText(baseUrl)
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("DOMAIN SETTINGS")
            .setMessage("Update the primary streaming domain URL if needed:")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.startsWith("http")) {
                    baseUrl = newUrl
                    currentPage = 1
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
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
                                    imageView.animate().alpha(1f).setDuration(220).start()
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
