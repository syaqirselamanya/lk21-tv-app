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
import com.lk21tv.app.domains.DomainManager
import com.lk21tv.app.domains.DualDomainMediaResult
import com.lk21tv.app.domains.lk21.Lk21Domain
import com.lk21tv.app.domains.watch32.Watch32Domain
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
    private lateinit var heroHotPickEyebrow: TextView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView
    private lateinit var heroCarouselDotsRow: LinearLayout
    private lateinit var heroPrevSlideBtn: Button
    private lateinit var heroNextSlideBtn: Button
    private lateinit var navHomeBtn: Button
    private lateinit var navContinueBtn: Button
    private lateinit var navMovieBtn: Button
    private lateinit var navSeriesBtn: Button
    private lateinit var navFilterBtn: Button
    private lateinit var navSettingsBtn: Button
    private lateinit var searchTopBtn: Button
    private val categoryPillButtons = mutableListOf<Pair<String, Button>>()

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
    private var currentCategoryLabel = "HOME"
    private var activeFilterLabel = ""
    private var allLoadedItems = mutableListOf<MediaItem>()
    private var hotPicksList = mutableListOf<MediaItem>()
    private var currentHeroSlideIndex = 0
    private var activeHeroItem: MediaItem? = null
    private var pendingHeroRunnable: Runnable? = null

    private val prefs by lazy { getSharedPreferences("citeraloka_prefs", Context.MODE_PRIVATE) }
    private val tvPrefs by lazy { getSharedPreferences("citeraloka_tv_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    private var isHeroAutoCarouselEnabled: Boolean
        get() = tvPrefs.getBoolean("hero_auto_carousel", true)
        set(value) = tvPrefs.edit().putBoolean("hero_auto_carousel", value).apply()

    // Senarai Kategori & Genre di dalam Modal FILTER (Netflix / Disney+ Hotstar Style)
    private val filterCatalogOptions = listOf(
        "POPULAR" to "/populer",
        "FEATURED" to "/top-movie-today",
        "LATEST RELEASES" to "/latest",
        "2026 RELEASES" to "/year/2026",
        "WESTERN" to "/country/usa",
        "KOREAN" to "/country/south-korea",
        "ACTION" to "/genre/action",
        "SCI-FI" to "/genre/sci-fi",
        "HORROR" to "/genre/horror",
        "THRILLER" to "/genre/thriller",
        "COMEDY" to "/genre/comedy",
        "DRAMA" to "/genre/drama",
        "ROMANCE" to "/genre/romance",
        "ANIMATION" to "/genre/animation",
        "CRIME" to "/genre/crime",
        "FANTASY" to "/genre/fantasy",
        "ADVENTURE" to "/genre/adventure"
    )

    private val heroCarouselRunnable = object : Runnable {
        override fun run() {
            if (isHeroAutoCarouselEnabled && hotPicksList.size > 1 && ::mainScrollView.isInitialized && mainScrollView.scrollY < dp(180)) {
                val nextIdx = (currentHeroSlideIndex + 1) % hotPicksList.size
                showHeroHotPickSlide(nextIdx, resetTimer = false)
            }
            mainHandler.postDelayed(this, 7000L)
        }
    }

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

        // Bar Navigasi Atas Modern & Minimalis (Gaya Netflix / Disney+ Hotstar / Apple TV+)
        // Kiri: Brand Logo | Tengah: HOME, CONTINUE WATCHING, MOVIE, TV SERIES, FILTER, SETTINGS | Kanan: SEARCH & PROFIL
        val topHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(20), dp(8), dp(20), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Bahagian Kiri Tetap (Brand Logo)
        val leftPinnedGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconResId = resources.getIdentifier("ic_tv_logo", "drawable", packageName)
        if (iconResId != 0) {
            val brandIcon = ImageView(this).apply {
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    rightMargin = dp(8)
                }
            }
            leftPinnedGroup.addView(brandIcon)
        }

        val brandLogo = TextView(this).apply {
            text = "CITERALOKA"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setPadding(0, 0, dp(10), 0)
        }
        leftPinnedGroup.addView(brandLogo)

        val leftDivider = View(this).apply {
            setBackgroundColor(Color.parseColor(BORDER_SUBTLE_TEAL))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                leftMargin = dp(4)
                rightMargin = dp(12)
            }
        }
        leftPinnedGroup.addView(leftDivider)
        topHeaderRow.addView(leftPinnedGroup)

        // 2. Bahagian Tengah: Tab Navigasi Utama Bentuk Bujur Minimalis (Tanpa perlu skrol melintang)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        categoryPillButtons.clear()

        navHomeBtn = createMinimalNavPill("HOME", isSelectedProvider = {
            currentCategoryLabel == "HOME" && activeFilterLabel.isBlank()
        }) {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/latest"
            currentCategoryLabel = "HOME"
            refreshCategoryPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }
        topBar.addView(navHomeBtn)

        navContinueBtn = createMinimalNavPill("CONTINUE WATCHING", isSelectedProvider = {
            currentCategoryLabel == "CONTINUE WATCHING"
        }) {
            activeFilterLabel = ""
            currentCategoryLabel = "CONTINUE WATCHING"
            refreshCategoryPillsAppearance()
            openContinueWatchingTab()
        }
        topBar.addView(navContinueBtn)

        navMovieBtn = createMinimalNavPill("MOVIE", isSelectedProvider = {
            currentCategoryLabel == "MOVIE" && activeFilterLabel.isBlank()
        }) {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/top-movie-today"
            currentCategoryLabel = "MOVIE"
            refreshCategoryPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }
        topBar.addView(navMovieBtn)

        navSeriesBtn = createMinimalNavPill("TV SERIES", isSelectedProvider = {
            currentCategoryLabel == "TV SERIES" && activeFilterLabel.isBlank()
        }) {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/latest-series"
            currentCategoryLabel = "TV SERIES"
            refreshCategoryPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }
        topBar.addView(navSeriesBtn)

        topHeaderRow.addView(topBar)

        // 3. Bahagian Kanan Tetap (Di TV: Ikon Filter, Ikon Search, Ikon Settings & Profil)
        val rightPinnedGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }

        navFilterBtn = createMinimalIconNavPill(
            iconType = UiIcons.Type.FILTER,
            label = "",
            isSelectedProvider = { activeFilterLabel.isNotBlank() }
        ) {
            showFilterDialog()
        }
        rightPinnedGroup.addView(navFilterBtn)

        searchTopBtn = createMinimalIconNavPill(
            iconType = UiIcons.Type.SEARCH,
            label = "",
            isSelectedProvider = { currentPath.contains("?s=") }
        ) {
            showSearchDialog()
        }
        rightPinnedGroup.addView(searchTopBtn)

        navSettingsBtn = createMinimalIconNavPill(
            iconType = UiIcons.Type.SETTINGS,
            label = "",
            isSelectedProvider = { false }
        ) {
            showSettingsDialog()
        }
        rightPinnedGroup.addView(navSettingsBtn)

        rightPinnedGroup.addView(createProfileChip())

        topHeaderRow.addView(rightPinnedGroup)
        root.addView(topHeaderRow)
        refreshCategoryPillsAppearance()

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

        // 6. Pagination Navigation (Ikon Minimalis ‹ / ›)
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(20))
        }
        paginationRow.addView(createSleekButton("‹", isPrimary = false) {
            if (currentPage > 1 && currentCategoryLabel != "CONTINUE WATCHING") {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        })
        paginationRow.addView(createSleekButton("›", isPrimary = true) {
            if (currentCategoryLabel != "CONTINUE WATCHING") {
                currentPage++
                loadCatalog(currentPath, currentPage)
            }
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
        if (currentCategoryLabel == "CONTINUE WATCHING") {
            openContinueWatchingTab()
        } else {
            continueWatchingContainer.visibility = View.GONE
            continueWatchingContainer.removeAllViews()
        }
        refreshRecommendationSection()
        mainHandler.removeCallbacks(heroCarouselRunnable)
        mainHandler.postDelayed(heroCarouselRunnable, 7000L)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(heroCarouselRunnable)
        super.onPause()
    }

    private fun createProfileChip(): LinearLayout {
        val profile = ProfileManager.getActive(this)
        val colorHex = profile?.let { ProfileManager.colorOf(it) } ?: ACCENT_TEAL
        val displayName = profile?.name ?: "PROFILE"

        val avatar = TextView(this).apply {
            text = ProfileManager.initialOf(displayName)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedBg(colorHex, colorHex, 0, 14)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(8) }
        }
        val nameView = TextView(this).apply {
            text = displayName.uppercase()
            setTextColor(Color.parseColor("#8AB8C2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT
            letterSpacing = 0.05f
            maxLines = 1
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg("#00000000", "#00000000", 0, 24)
            setPadding(dp(8), dp(5), dp(14), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4) }
            addView(avatar)
            addView(nameView)
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    nameView.setTextColor(Color.parseColor(TEXT_PRIMARY))
                    nameView.typeface = Typeface.DEFAULT_BOLD
                    v.background = createRoundedBg("#2400D4B2", ACCENT_TEAL, 2, 24)
                    v.scaleX = 1.04f
                    v.scaleY = 1.04f
                } else {
                    nameView.setTextColor(Color.parseColor("#8AB8C2"))
                    nameView.typeface = Typeface.DEFAULT
                    v.background = createRoundedBg("#00000000", "#00000000", 0, 24)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
            }
        }
    }

    /**
     * Butang Navigasi Bentuk Bujur (Pill) Minimalis:
     * - Tanpa dipilih: tiada outline, teks nipis/normal.
     * - Apabila dipilih / difokus: ada outline Teal & teks Bold.
     */
    private fun createMinimalNavPill(
        label: String,
        isSelectedProvider: () -> Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            letterSpacing = 0.05f
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(13), dp(6), dp(13), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            applyMinimalPillStyle(this, isSelected = isSelectedProvider(), hasFocus = false)
            setOnFocusChangeListener { v, hasFocus ->
                applyMinimalPillStyle(v as Button, isSelected = isSelectedProvider(), hasFocus = hasFocus)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createMinimalIconNavPill(
        iconType: UiIcons.Type,
        label: String = "",
        isSelectedProvider: () -> Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            letterSpacing = 0.05f
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            UiIcons.attachIcon(this, iconType, sizeDp = 16, paddingDp = 6)
            applyMinimalPillStyle(this, isSelected = isSelectedProvider(), hasFocus = false)
            setOnFocusChangeListener { v, hasFocus ->
                applyMinimalPillStyle(v as Button, isSelected = isSelectedProvider(), hasFocus = hasFocus)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun applyMinimalPillStyle(btn: Button, isSelected: Boolean, hasFocus: Boolean) {
        when {
            hasFocus -> {
                val c = Color.parseColor("#FFFFFF")
                btn.setTextColor(c)
                UiIcons.syncIconTint(btn, c)
                btn.typeface = Typeface.DEFAULT_BOLD
                btn.background = createRoundedBg("#2E00D4B2", ACCENT_TEAL, 2, 24)
                btn.scaleX = 1.04f
                btn.scaleY = 1.04f
            }
            isSelected -> {
                val c = Color.parseColor(TEXT_PRIMARY)
                btn.setTextColor(c)
                UiIcons.syncIconTint(btn, c)
                btn.typeface = Typeface.DEFAULT_BOLD
                btn.background = createRoundedBg("#1400D4B2", ACCENT_TEAL, 1, 24)
                btn.scaleX = 1.0f
                btn.scaleY = 1.0f
            }
            else -> {
                val c = Color.parseColor("#8AB8C2")
                btn.setTextColor(c)
                UiIcons.syncIconTint(btn, c)
                btn.typeface = Typeface.DEFAULT
                btn.background = createRoundedBg("#00000000", "#00000000", 0, 24)
                btn.scaleX = 1.0f
                btn.scaleY = 1.0f
            }
        }
    }

    private fun refreshCategoryPillsAppearance() {
        if (::navHomeBtn.isInitialized) {
            applyMinimalPillStyle(
                navHomeBtn,
                isSelected = (currentCategoryLabel == "HOME" && activeFilterLabel.isBlank()),
                hasFocus = navHomeBtn.hasFocus()
            )
        }
        if (::navContinueBtn.isInitialized) {
            applyMinimalPillStyle(
                navContinueBtn,
                isSelected = (currentCategoryLabel == "CONTINUE WATCHING"),
                hasFocus = navContinueBtn.hasFocus()
            )
        }
        if (::navMovieBtn.isInitialized) {
            applyMinimalPillStyle(
                navMovieBtn,
                isSelected = (currentCategoryLabel == "MOVIE" && activeFilterLabel.isBlank()),
                hasFocus = navMovieBtn.hasFocus()
            )
        }
        if (::navSeriesBtn.isInitialized) {
            applyMinimalPillStyle(
                navSeriesBtn,
                isSelected = (currentCategoryLabel == "TV SERIES" && activeFilterLabel.isBlank()),
                hasFocus = navSeriesBtn.hasFocus()
            )
        }
        if (::navFilterBtn.isInitialized) {
            navFilterBtn.text = activeFilterLabel
            UiIcons.attachIcon(navFilterBtn, UiIcons.Type.FILTER, sizeDp = 16, paddingDp = 6)
            applyMinimalPillStyle(
                navFilterBtn,
                isSelected = activeFilterLabel.isNotBlank(),
                hasFocus = navFilterBtn.hasFocus()
            )
        }
        if (::navSettingsBtn.isInitialized) {
            applyMinimalPillStyle(
                navSettingsBtn,
                isSelected = false,
                hasFocus = navSettingsBtn.hasFocus()
            )
        }
        if (::searchTopBtn.isInitialized) {
            applyMinimalPillStyle(
                searchTopBtn,
                isSelected = currentPath.contains("?s="),
                hasFocus = searchTopBtn.hasFocus()
            )
        }
    }

    private fun openContinueWatchingTab() {
        val historyList = ContinueWatchingManager.getList(this)
        refreshContinueWatchingSection()
        if (historyList.isEmpty()) {
            Toast.makeText(this, "No titles in Continue Watching yet", Toast.LENGTH_SHORT).show()
            sectionTitleView.text = "CONTINUE WATCHING  •  0 IN PROGRESS"
            statusText.text = "Start watching any movie or TV series to see it here."
            return
        }
        val cwItems = historyList.map { cw ->
            MediaItem(
                title = cw.title,
                url = cw.pageUrl,
                posterUrl = cw.posterUrl,
                rating = "",
                episodeOrQuality = if (cw.episodeLabel.isNotBlank()) cw.episodeLabel else "${cw.remainingMinutes}M LEFT",
                genre = cw.genre,
                isSeries = cw.episodeLabel.isNotBlank(),
                source = if (Watch32Domain.isWatch32Uri(cw.pageUrl)) "WATCH32" else "LK21",
                statusBadge = "WATCHED ${cw.progressPercent}%"
            )
        }
        sectionTitleView.text = "CONTINUE WATCHING  •  ${cwItems.size} IN PROGRESS"
        statusText.text = "Select any title to resume or choose server"
        hotPicksList.clear()
        hotPicksList.addAll(cwItems.take(7))
        currentHeroSlideIndex = 0
        renderHeroCarouselDots()
        showHeroHotPickSlide(0, resetTimer = true)
        renderCards(cwItems)
        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
    }

    private fun createCategoryBox(label: String, onClick: () -> Unit): Button {
        return createMinimalNavPill(label, isSelectedProvider = { false }, onClick = onClick)
    }

    private fun createSleekButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) "#1F00D4B2" else "#120B2B38"
        val normalBorder = if (isPrimary) ACCENT_TEAL else "#00000000"
        val normalStroke = if (isPrimary) 1 else 0
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(if (isPrimary) TEXT_PRIMARY else "#99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = 0.05f
            isFocusable = true
            isFocusableInTouchMode = false
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(normalBg, normalBorder, normalStroke, 24)
            setPadding(dp(20), dp(9), dp(20), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(10)
            }
            setOnFocusChangeListener { v, hasFocus ->
                val btn = v as Button
                if (hasFocus) {
                    btn.setTextColor(Color.parseColor("#FFFFFF"))
                    btn.typeface = Typeface.DEFAULT_BOLD
                    btn.background = createRoundedBg("#3300D4B2", ACCENT_TEAL, 2, 24)
                    btn.scaleX = 1.04f
                    btn.scaleY = 1.04f
                } else {
                    btn.setTextColor(Color.parseColor(if (isPrimary) TEXT_PRIMARY else "#99F6E4"))
                    btn.typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    btn.background = createRoundedBg(normalBg, normalBorder, normalStroke, 24)
                    btn.scaleX = 1.0f
                    btn.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun normalizeTitleKey(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun loadCatalog(path: String, page: Int) {
        progressBar.visibility = View.VISIBLE
        sectionTitleView.text = "$currentCategoryLabel  •  PAGE $page"
        statusText.text = "Loading (${DomainManager.getActiveStatusLabel(this)})..."
        gridContainer.removeAllViews()
        refreshCategoryPillsAppearance()

        executor.execute {
            try {
                val combined = DomainManager.fetchCatalog(
                    context = this@MainActivity,
                    executor = executor,
                    baseUrl = baseUrl,
                    path = path,
                    page = page
                )

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (combined.isEmpty()) {
                        statusText.text = "No titles found."
                    } else {
                        statusText.text = "${DomainManager.getActiveStatusLabel(this@MainActivity)}  •  ${combined.size} TITLES"
                        allLoadedItems.clear()
                        allLoadedItems.addAll(combined)

                        hotPicksList.clear()
                        hotPicksList.addAll(combined.take(7))
                        currentHeroSlideIndex = 0
                        renderHeroCarouselDots()
                        showHeroHotPickSlide(0, resetTimer = true)

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
     * Apple TV+ Style "Hot Picks" Hero Carousel Stage
     * Keeps the rich Dark Teal Hero Spotlight (16:9 backdrop + ClearLogo PNG + Badges + Synopsis)
     * and adds auto-rotating Hot Picks slides with interactive `‹` / pill indicators / `›` controls.
     */
    private fun buildPinnedHeroSpotlightStage(): FrameLayout {
        val dm = resources.displayMetrics
        val stageHeight = (dm.heightPixels * 0.65f).toInt().coerceIn(dp(295), dp(405))
        val isCompactHeight = dm.heightPixels < dp(460)

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                stageHeight
            )
        }

        // Layer 1: 16:9 Widescreen Backdrop on the right
        val backdropWidth = (dm.widthPixels * 0.76f).toInt()
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
                dp(82),
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
            setPadding(dp(30), dp(16), dp(18), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                infoWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.BOTTOM
            )
        }

        // Apple TV+ Eyebrow Badge ("HOT PICK #1 OF 7  •  SPOTLIGHT")
        heroHotPickEyebrow = TextView(this).apply {
            text = "HOT PICKS  •  SPOTLIGHT"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroHotPickEyebrow)

        // Official ClearLogo PNG (from TMDB)
        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_START
            adjustViewBounds = true
            maxWidth = dp(285)
            maxHeight = if (isCompactHeight) dp(50) else dp(68)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dp(275),
                if (isCompactHeight) dp(50) else dp(66)
            ).apply {
                bottomMargin = dp(6)
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
            setPadding(0, 0, 0, dp(7))
        }
        infoCol.addView(heroMetaView)

        // Minimalist Pill Badges Row (Status, Quality, IMDb, Source - No Emojis)
        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(7))
        }
        infoCol.addView(heroBadgeRow)

        // Synopsis (3-4 Lines)
        heroPlotView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompactHeight) 11.5f else 12.8f)
            maxLines = if (isCompactHeight) 3 else 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.16f)
            setPadding(0, 0, 0, dp(12))
        }
        infoCol.addView(heroPlotView)

        // Quick Action Row + Apple TV+ Carousel Switcher
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val playHeroBtn = createCompactHeroButton("PLAY", isPrimary = true) {
            activeHeroItem?.let { onItemClicked(it, playImmediately = !it.isSeries) }
        }.apply {
            UiIcons.attachIcon(this, UiIcons.Type.PLAY, sizeDp = 14, paddingDp = 6)
        }
        val infoHeroBtn = createCompactHeroButton("INFO & SERVER", isPrimary = false) {
            activeHeroItem?.let { onItemClicked(it, playImmediately = false) }
        }.apply {
            UiIcons.attachIcon(this, UiIcons.Type.INFO, sizeDp = 14, paddingDp = 6)
        }
        actionRow.addView(playHeroBtn)
        actionRow.addView(infoHeroBtn)
        infoCol.addView(actionRow)

        stage.addView(infoCol)

        // Layer 4: Apple TV+ Carousel Controls & Animated Pill Indicators (Bottom-Right of Hero Stage)
        val carouselBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg("#B3031117", BORDER_SUBTLE_TEAL, 1, 24)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(28)
                bottomMargin = dp(20)
            }
        }

        heroPrevSlideBtn = createMinimalNavPill("‹", isSelectedProvider = { false }) {
            if (hotPicksList.isNotEmpty()) {
                val prev = (currentHeroSlideIndex - 1 + hotPicksList.size) % hotPicksList.size
                showHeroHotPickSlide(prev, resetTimer = true)
            }
        }.apply {
            setPadding(dp(12), dp(3), dp(12), dp(3))
        }

        heroCarouselDotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }

        heroNextSlideBtn = createMinimalNavPill("›", isSelectedProvider = { false }) {
            if (hotPicksList.isNotEmpty()) {
                val next = (currentHeroSlideIndex + 1) % hotPicksList.size
                showHeroHotPickSlide(next, resetTimer = true)
            }
        }.apply {
            setPadding(dp(12), dp(3), dp(12), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        carouselBar.addView(heroPrevSlideBtn)
        carouselBar.addView(heroCarouselDotsRow)
        carouselBar.addView(heroNextSlideBtn)
        stage.addView(carouselBar)

        return stage
    }

    private fun renderHeroCarouselDots() {
        if (!::heroCarouselDotsRow.isInitialized) return
        heroCarouselDotsRow.removeAllViews()
        val total = hotPicksList.size
        if (total <= 1) return
        for (i in 0 until total) {
            val isActive = (i == currentHeroSlideIndex)
            val dotView = View(this).apply {
                val widthDp = if (isActive) dp(24) else dp(7)
                val heightDp = dp(7)
                val fillHex = if (isActive) ACCENT_TEAL else "#4D99F6E4"
                background = createRoundedBg(fillHex, fillHex, 0, 8)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(widthDp, heightDp).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                }
                setOnClickListener {
                    showHeroHotPickSlide(i, resetTimer = true)
                }
            }
            heroCarouselDotsRow.addView(dotView)
        }
    }

    private fun showHeroHotPickSlide(index: Int, resetTimer: Boolean) {
        if (hotPicksList.isEmpty()) return
        currentHeroSlideIndex = index.coerceIn(0, hotPicksList.size - 1)
        val item = hotPicksList[currentHeroSlideIndex]
        if (::heroHotPickEyebrow.isInitialized) {
            heroHotPickEyebrow.text = "HOT PICK #${currentHeroSlideIndex + 1} OF ${hotPicksList.size}  •  APPLE TV+ SPOTLIGHT"
        }
        renderHeroCarouselDots()
        scheduleHeroSpotlight(item, delayMs = 0L, isHotPickSlide = true)
        if (resetTimer) {
            mainHandler.removeCallbacks(heroCarouselRunnable)
            mainHandler.postDelayed(heroCarouselRunnable, 7000L)
        }
    }

    private fun createCompactHeroButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) "#1F00D4B2" else "#80071F29"
        val normalBorder = if (isPrimary) ACCENT_TEAL else "#00000000"
        val normalStroke = if (isPrimary) 1 else 0
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(if (isPrimary) TEXT_PRIMARY else "#99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = 0.06f
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, normalStroke, 24)
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
                val btn = v as Button
                if (hasFocus) {
                    val c = Color.parseColor("#FFFFFF")
                    btn.setTextColor(c)
                    UiIcons.syncIconTint(btn, c)
                    btn.typeface = Typeface.DEFAULT_BOLD
                    btn.background = createRoundedBg("#3300D4B2", ACCENT_TEAL, 2, 24)
                    btn.scaleX = 1.04f
                    btn.scaleY = 1.04f
                    mainHandler.removeCallbacks(heroCarouselRunnable)
                    mainHandler.postDelayed(heroCarouselRunnable, 9000L)
                } else {
                    val c = Color.parseColor(if (isPrimary) TEXT_PRIMARY else "#99F6E4")
                    btn.setTextColor(c)
                    UiIcons.syncIconTint(btn, c)
                    btn.typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    btn.background = createRoundedBg(normalBg, normalBorder, normalStroke, 24)
                    btn.scaleX = 1.0f
                    btn.scaleY = 1.0f
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

    private fun updateHeroSpotlightImmediate(item: MediaItem, isHotPickSlide: Boolean = false) {
        activeHeroItem = item
        if (!isHotPickSlide && ::heroHotPickEyebrow.isInitialized) {
            val hpIdx = hotPicksList.indexOfFirst { it.url == item.url }
            if (hpIdx >= 0) {
                currentHeroSlideIndex = hpIdx
                heroHotPickEyebrow.text = "HOT PICK #${hpIdx + 1} OF ${hotPicksList.size}  •  APPLE TV+ SPOTLIGHT"
                renderHeroCarouselDots()
            } else {
                heroHotPickEyebrow.text = "SELECTED TITLE  •  SPOTLIGHT PREVIEW"
            }
        }
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

    private fun scheduleHeroSpotlight(item: MediaItem, delayMs: Long = 110L, isHotPickSlide: Boolean = false) {
        pendingHeroRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            updateHeroSpotlightImmediate(item, isHotPickSlide = isHotPickSlide)
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
     * 3. Bahagian Sambung Menonton (Continue Watching) - Hanya dipaparkan di dalam halaman CONTINUE WATCHING sahaja
     */
    private fun refreshContinueWatchingSection() {
        if (currentCategoryLabel != "CONTINUE WATCHING") {
            continueWatchingContainer.visibility = View.GONE
            continueWatchingContainer.removeAllViews()
            return
        }
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
                            source = "LK21",
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
                    onItemClicked(item, playImmediately = false)
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
                    onItemClicked(item, playImmediately = false)
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

        // Untuk Siri TV (Series sahaja): terus masuk ke Halaman Pilihan Siri Gaya Netflix (Hero + Domain + Musim + Episod)
        if (item.isSeries) {
            launchSeriesDetailPage(item)
            return
        }

        if (playImmediately && !item.isSeries) {
            val defaultSrv = if (Watch32Domain.isWatch32Uri(item.url)) {
                "W32 • Cinezo (No Ads)"
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
        statusText.text = "Checking domains (LK21 & Watch32): ${item.title}..."

        executor.execute {
            try {
                val dualResult = DomainManager.fetchDualDetails(
                    executor = executor,
                    baseUrl = baseUrl,
                    pageUrl = item.url,
                    title = item.title,
                    isSeries = item.isSeries
                )
                val primary = dualResult.primaryDetail()
                    ?: throw IllegalStateException("Server unreachable")

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Ready: ${primary.title}"
                    if (primary.episodes.isNotEmpty()) {
                        launchSeriesDetailPage(
                            item.copy(
                                title = primary.title,
                                posterUrl = primary.posterUrl.ifBlank { item.posterUrl },
                                backdropUrl = primary.backdropUrl.ifBlank { item.backdropUrl },
                                logoUrl = primary.logoUrl.ifBlank { item.logoUrl },
                                plot = primary.plot,
                                rating = primary.rating.ifBlank { item.rating },
                                genre = primary.genre.ifBlank { item.genre },
                                source = primary.source,
                                isSeries = true
                            )
                        )
                    } else if (playImmediately) {
                        launchVideoPlayer(
                            pageUrl = primary.url,
                            embedUrl = primary.mainEmbedUrl ?: "",
                            title = primary.title,
                            serverName = primary.mirrors.firstOrNull()?.name ?: "P2P Direct",
                            posterUrl = primary.posterUrl,
                            genre = primary.genre,
                            mirrors = primary.mirrors
                        )
                    } else {
                        showNetflixDetailModal(dualResult)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    val defaultSrv = if (Watch32Domain.isWatch32Uri(item.url)) "W32 • Cinezo (No Ads)" else "P2P Direct"
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

    private fun showNetflixDetailModal(dualResult: DualDomainMediaResult) {
        val initialDetail = dualResult.primaryDetail() ?: return
        val lkDetail = dualResult.lk21Detail
        val w32Detail = dualResult.watch32Detail

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val modalRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 18)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            layoutParams = ViewGroup.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = initialDetail.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaView = TextView(this).apply {
            text = "${initialDetail.quality}  ${if (initialDetail.rating.isNotBlank()) "•  IMDb ${initialDetail.rating}" else ""}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }

        val plotView = TextView(this).apply {
            text = initialDetail.plot
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(12))
        }

        modalRoot.addView(titleView)
        modalRoot.addView(metaView)
        modalRoot.addView(plotView)

        // Baris Pilihan Tick Domain di dalam Movie yang dipilih (1. LK21 / 2. Watch32 / Both)
        val domainHeader = TextView(this).apply {
            text = "SELECT DOMAIN (TICK ONE OR BOTH):"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(6))
        }
        modalRoot.addView(domainHeader)

        var tickLk21 = lkDetail != null && (DomainManager.isLk21Enabled(this) || w32Detail == null)
        var tickWatch32 = w32Detail != null && (DomainManager.isWatch32Enabled(this) || lkDetail == null)
        if (!tickLk21 && !tickWatch32) {
            tickLk21 = lkDetail != null
            tickWatch32 = w32Detail != null
        }

        val domainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val listScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(250)
            )
        }

        val optionsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        lateinit var lkTickBtn: Button
        lateinit var w32TickBtn: Button

        fun refreshDomainTickButtonsAndServers() {
            val lkLabel = if (lkDetail != null) {
                "${if (tickLk21) "[✓]" else "[  ]"} 1. LK21 (${lkDetail.mirrors.size} SRV)"
            } else {
                "[ - ] 1. LK21 (UNAVAILABLE)"
            }
            val w32Label = if (w32Detail != null) {
                "${if (tickWatch32) "[✓]" else "[  ]"} 2. WATCH32 (${w32Detail.mirrors.size} SRV)"
            } else {
                "[ - ] 2. WATCH32 (UNAVAILABLE)"
            }
            lkTickBtn.text = lkLabel
            w32TickBtn.text = w32Label
            applyMinimalPillStyle(lkTickBtn, isSelected = tickLk21, hasFocus = lkTickBtn.hasFocus())
            applyMinimalPillStyle(w32TickBtn, isSelected = tickWatch32, hasFocus = w32TickBtn.hasFocus())

            optionsCol.removeAllViews()
            val combinedMirrors = DomainManager.combineMirrors(lkDetail, w32Detail, tickLk21, tickWatch32)

            if (tickLk21 && lkDetail != null && !lkDetail.mainEmbedUrl.isNullOrBlank()) {
                val playLkBtn = createSleekButton("PLAY NOW  •  DOMAIN 1: LK21", isPrimary = true) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = lkDetail.url,
                        embedUrl = lkDetail.mainEmbedUrl,
                        title = lkDetail.title,
                        serverName = lkDetail.mirrors.firstOrNull()?.name ?: "P2P Direct",
                        posterUrl = lkDetail.posterUrl.ifBlank { initialDetail.posterUrl },
                        genre = lkDetail.genre.ifBlank { initialDetail.genre },
                        mirrors = combinedMirrors.ifEmpty { lkDetail.mirrors }
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                optionsCol.addView(playLkBtn)
            }

            if (tickWatch32 && w32Detail != null && !w32Detail.mainEmbedUrl.isNullOrBlank()) {
                val playW32Btn = createSleekButton("PLAY NOW  •  DOMAIN 2: WATCH32 (WATCH32.SITE)", isPrimary = true) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = w32Detail.url,
                        embedUrl = w32Detail.mainEmbedUrl,
                        title = w32Detail.title,
                        serverName = w32Detail.mirrors.firstOrNull()?.name ?: "W32 • Cinezo (No Ads)",
                        posterUrl = w32Detail.posterUrl.ifBlank { initialDetail.posterUrl },
                        genre = w32Detail.genre.ifBlank { initialDetail.genre },
                        mirrors = combinedMirrors.ifEmpty { w32Detail.mirrors }
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                optionsCol.addView(playW32Btn)
            }

            if (combinedMirrors.isNotEmpty()) {
                val srvHeader = TextView(this).apply {
                    text = "SERVERS FROM TICKED DOMAIN(S) (${combinedMirrors.size}):"
                    setTextColor(Color.parseColor(TEXT_MUTED))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.05f
                    setPadding(0, dp(8), 0, dp(6))
                }
                optionsCol.addView(srvHeader)

                combinedMirrors.forEach { mirror ->
                    val isW32Mirror = Watch32Domain.isWatch32EmbedUrl(mirror.embedUrl)
                    val targetDetail = if (isW32Mirror) (w32Detail ?: initialDetail) else (lkDetail ?: initialDetail)
                    val srvBtn = createSleekButton("SERVER: ${mirror.name}", isPrimary = false) {
                        dialog.dismiss()
                        launchVideoPlayer(
                            pageUrl = targetDetail.url,
                            embedUrl = mirror.embedUrl,
                            title = targetDetail.title,
                            serverName = mirror.name,
                            posterUrl = targetDetail.posterUrl.ifBlank { initialDetail.posterUrl },
                            genre = targetDetail.genre.ifBlank { initialDetail.genre },
                            mirrors = combinedMirrors
                        )
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(7) }
                    }
                    optionsCol.addView(srvBtn)
                }
            }
        }

        lkTickBtn = createMinimalNavPill("1. LK21", isSelectedProvider = { tickLk21 }) {
            if (lkDetail == null) {
                Toast.makeText(this, "Title not found on LK21 domain", Toast.LENGTH_SHORT).show()
                return@createMinimalNavPill
            }
            if (tickLk21 && !tickWatch32 && w32Detail != null) {
                tickLk21 = false
                tickWatch32 = true
            } else if (!tickLk21 || tickWatch32) {
                tickLk21 = !tickLk21
            }
            refreshDomainTickButtonsAndServers()
        }

        w32TickBtn = createMinimalNavPill("2. WATCH32", isSelectedProvider = { tickWatch32 }) {
            if (w32Detail == null) {
                Toast.makeText(this, "Title not found on Watch32 domain", Toast.LENGTH_SHORT).show()
                return@createMinimalNavPill
            }
            if (tickWatch32 && !tickLk21 && lkDetail != null) {
                tickWatch32 = false
                tickLk21 = true
            } else if (!tickWatch32 || tickLk21) {
                tickWatch32 = !tickWatch32
            }
            refreshDomainTickButtonsAndServers()
        }

        domainRow.addView(lkTickBtn)
        domainRow.addView(w32TickBtn)
        modalRoot.addView(domainRow)

        refreshDomainTickButtonsAndServers()

        listScroll.addView(optionsCol)
        modalRoot.addView(listScroll)

        val closeBtn = createSleekButton("✕", isPrimary = false) {
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
        val modalWidth = (dm.widthPixels * 0.72f).toInt().coerceIn(dp(560), dp(820))

        dialog.setContentView(modalRoot)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        lkTickBtn.requestFocus()
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

    private fun showFilterDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 20)
            setPadding(dp(26), dp(22), dp(26), dp(22))
            layoutParams = ViewGroup.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = "FILTER CATALOG & GENRES"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        val sub = TextView(this).apply {
            text = "Browse by Popular, Featured, Country, Year, or Genre (Netflix / Disney+ Hotstar Style):"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(title)
        root.addView(sub)

        val grid = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        var firstFilterBtn: Button? = null
        filterCatalogOptions.forEach { (label, path) ->
            val isSelectedNow = (activeFilterLabel == label || currentPath == path)
            val btn = createMinimalNavPill(label, isSelectedProvider = { isSelectedNow }) {
                activeFilterLabel = label
                currentCategoryLabel = label
                currentPath = path
                currentPage = 1
                dialog.dismiss()
                refreshCategoryPillsAppearance()
                loadCatalog(currentPath, currentPage)
            }.apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(182)
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
            }
            if (firstFilterBtn == null) firstFilterBtn = btn
            grid.addView(btn)
        }
        root.addView(grid)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (activeFilterLabel.isNotBlank()) {
            bottomRow.addView(createSleekButton("CLEAR", isPrimary = false) {
                activeFilterLabel = ""
                currentCategoryLabel = "HOME"
                currentPath = "/latest"
                currentPage = 1
                dialog.dismiss()
                refreshCategoryPillsAppearance()
                loadCatalog(currentPath, currentPage)
            })
        }
        bottomRow.addView(createSleekButton("✕", isPrimary = false) {
            dialog.dismiss()
        })
        root.addView(bottomRow)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT)
        firstFilterBtn?.requestFocus()
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var tickLk21 = DomainManager.isLk21Enabled(this)
        var tickWatch32 = DomainManager.isWatch32Enabled(this)

        val outerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 20)
            setPadding(dp(26), dp(20), dp(26), dp(20))
            layoutParams = ViewGroup.LayoutParams(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = "SETTINGS & PREFERENCES"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        val activeProfile = ProfileManager.getActive(this)?.name ?: "Default"
        val sub = TextView(this).apply {
            text = "Active Profile: $activeProfile  •  ${DomainManager.getDomainStatusBadge(this@MainActivity)}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(title)
        root.addView(sub)

        // SECTION 1: DOMAIN SELECT (1. LK21 & 2. WATCH32)
        val domainHeader = TextView(this).apply {
            text = "1. STREAMING DOMAIN SELECT (TICK ONE OR BOTH)"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(domainHeader)

        lateinit var btnLk21: Button
        lateinit var btnWatch32: Button

        fun updateTickButtons() {
            btnLk21.text = "${if (tickLk21) "[✓]" else "[   ]"}  1. LK21  (${baseUrl.removePrefix("https://")})"
            btnWatch32.text = "${if (tickWatch32) "[✓]" else "[   ]"}  2. WATCH32  (watch32.site)"
            applyMinimalPillStyle(btnLk21, isSelected = tickLk21, hasFocus = btnLk21.hasFocus())
            applyMinimalPillStyle(btnWatch32, isSelected = tickWatch32, hasFocus = btnWatch32.hasFocus())
        }

        btnLk21 = createMinimalNavPill("1. LK21", isSelectedProvider = { tickLk21 }) {
            if (tickLk21 && !tickWatch32) {
                Toast.makeText(this, "Please keep at least 1 domain ticked", Toast.LENGTH_SHORT).show()
            } else {
                tickLk21 = !tickLk21
                updateTickButtons()
            }
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        btnWatch32 = createMinimalNavPill("2. WATCH32", isSelectedProvider = { tickWatch32 }) {
            if (tickWatch32 && !tickLk21) {
                Toast.makeText(this, "Please keep at least 1 domain ticked", Toast.LENGTH_SHORT).show()
            } else {
                tickWatch32 = !tickWatch32
                updateTickButtons()
            }
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        root.addView(btnLk21)
        root.addView(btnWatch32)
        updateTickButtons()

        val urlInput = EditText(this).apply {
            setText(baseUrl)
            hint = "LK21 Mirror URL (https://...)"
            setSingleLine(true)
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setHintTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            background = createRoundedBg(BG_DEEP_TEAL, BORDER_SUBTLE_TEAL, 1, 14)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        root.addView(urlInput)

        // SECTION 2: APP UPDATE, PROFILE & PLAYBACK PREFERENCES
        val prefHeader = TextView(this).apply {
            text = "2. APP UPDATE, PROFILE & PLAYBACK SETTINGS"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            setPadding(0, dp(2), 0, dp(8))
        }
        root.addView(prefHeader)

        val prefRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val checkUpdateBtn = createSleekButton("CHECK FOR UPDATE", isPrimary = true) {
            dialog.dismiss()
            AppUpdateManager.checkUpdate(this, isManual = true)
        }
        val switchProfileBtn = createSleekButton("SWITCH PROFILE", isPrimary = false) {
            dialog.dismiss()
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        prefRow1.addView(checkUpdateBtn)
        prefRow1.addView(switchProfileBtn)
        root.addView(prefRow1)

        val prefRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val fitModes = listOf("contain" to "FIT", "cover" to "ZOOM", "fill" to "FILL")
        fun getAspectLabel(): String {
            val cur = tvPrefs.getString("default_aspect_mode", "contain") ?: "contain"
            val lbl = fitModes.firstOrNull { it.first == cur }?.second ?: "FIT"
            return "DEFAULT ASPECT: $lbl"
        }

        lateinit var aspectPrefBtn: Button
        aspectPrefBtn = createSleekButton(getAspectLabel(), isPrimary = false) {
            val cur = tvPrefs.getString("default_aspect_mode", "contain") ?: "contain"
            val idx = fitModes.indexOfFirst { it.first == cur }.takeIf { it >= 0 } ?: 0
            val next = fitModes[(idx + 1) % fitModes.size]
            tvPrefs.edit().putString("default_aspect_mode", next.first).apply()
            aspectPrefBtn.text = getAspectLabel()
        }

        lateinit var carouselToggleBtn: Button
        carouselToggleBtn = createSleekButton(
            "HOT PICKS AUTO-SLIDE: ${if (isHeroAutoCarouselEnabled) "ON" else "OFF"}",
            isPrimary = false
        ) {
            isHeroAutoCarouselEnabled = !isHeroAutoCarouselEnabled
            carouselToggleBtn.text = "HOT PICKS AUTO-SLIDE: ${if (isHeroAutoCarouselEnabled) "ON" else "OFF"}"
        }
        prefRow2.addView(aspectPrefBtn)
        prefRow2.addView(carouselToggleBtn)
        root.addView(prefRow2)

        val prefRow3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        val clearHistoryBtn = createSleekButton("CLEAR WATCH HISTORY", isPrimary = false) {
            ContinueWatchingManager.getList(this).forEach { cw ->
                ContinueWatchingManager.remove(this, cw.pageUrl)
            }
            refreshContinueWatchingSection()
            Toast.makeText(this, "Continue Watching history cleared", Toast.LENGTH_SHORT).show()
        }
        val clearCacheBtn = createSleekButton("CLEAR IMAGE CACHE", isPrimary = false) {
            imageCache.evictAll()
            Toast.makeText(this, "Image cache cleared", Toast.LENGTH_SHORT).show()
        }
        prefRow3.addView(clearHistoryBtn)
        prefRow3.addView(clearCacheBtn)
        root.addView(prefRow3)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = createSleekButton("✕", isPrimary = false) {
            dialog.dismiss()
        }
        val saveBtn = createSleekButton("SAVE & APPLY", isPrimary = true) {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.startsWith("http")) {
                baseUrl = newUrl
            }
            DomainManager.setDomainsEnabled(this, tickLk21, tickWatch32)
            refreshCategoryPillsAppearance()
            dialog.dismiss()
            currentPage = 1
            loadCatalog(currentPath, currentPage)
        }
        actionRow.addView(cancelBtn)
        actionRow.addView(saveBtn)
        root.addView(actionRow)

        outerScroll.addView(root)
        dialog.setContentView(outerScroll)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT)
        btnLk21.requestFocus()
    }

    private fun showDomainDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var tickLk21 = DomainManager.isLk21Enabled(this)
        var tickWatch32 = DomainManager.isWatch32Enabled(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 18)
            setPadding(dp(26), dp(22), dp(26), dp(22))
            layoutParams = ViewGroup.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = "SELECT STREAMING DOMAIN"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }
        val sub = TextView(this).apply {
            text = "Tick one or both domains. Catalog and servers will load from your ticked domains:"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(4), 0, dp(16))
        }
        root.addView(title)
        root.addView(sub)

        lateinit var btnLk21: Button
        lateinit var btnWatch32: Button

        fun updateTickButtons() {
            btnLk21.text = "${if (tickLk21) "[✓]" else "[   ]"}  1. LK21  (${baseUrl.removePrefix("https://")})"
            btnWatch32.text = "${if (tickWatch32) "[✓]" else "[   ]"}  2. WATCH32  (watch32.site)"
            applyMinimalPillStyle(btnLk21, isSelected = tickLk21, hasFocus = btnLk21.hasFocus())
            applyMinimalPillStyle(btnWatch32, isSelected = tickWatch32, hasFocus = btnWatch32.hasFocus())
        }

        btnLk21 = createMinimalNavPill("1. LK21", isSelectedProvider = { tickLk21 }) {
            if (tickLk21 && !tickWatch32) {
                Toast.makeText(this, "Please keep at least 1 domain ticked", Toast.LENGTH_SHORT).show()
            } else {
                tickLk21 = !tickLk21
                updateTickButtons()
            }
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        btnWatch32 = createMinimalNavPill("2. WATCH32", isSelectedProvider = { tickWatch32 }) {
            if (tickWatch32 && !tickLk21) {
                Toast.makeText(this, "Please keep at least 1 domain ticked", Toast.LENGTH_SHORT).show()
            } else {
                tickWatch32 = !tickWatch32
                updateTickButtons()
            }
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        root.addView(btnLk21)
        root.addView(btnWatch32)
        updateTickButtons()

        val urlLabel = TextView(this).apply {
            text = "LK21 MIRROR URL (OPTIONAL):"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        val urlInput = EditText(this).apply {
            setText(baseUrl)
            setSingleLine(true)
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = createRoundedBg(BG_DEEP_TEAL, BORDER_SUBTLE_TEAL, 1, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }
        root.addView(urlLabel)
        root.addView(urlInput)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelBtn = createSleekButton("CANCEL", isPrimary = false) {
            dialog.dismiss()
        }
        val saveBtn = createSleekButton("APPLY DOMAIN", isPrimary = true) {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.startsWith("http")) {
                baseUrl = newUrl
            }
            DomainManager.setDomainsEnabled(this, tickLk21, tickWatch32)
            refreshCategoryPillsAppearance()
            dialog.dismiss()
            currentPage = 1
            loadCatalog(currentPath, currentPage)
        }
        actionRow.addView(cancelBtn)
        actionRow.addView(saveBtn)
        root.addView(actionRow)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT)
        btnLk21.requestFocus()
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
