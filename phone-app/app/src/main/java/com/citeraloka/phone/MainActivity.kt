package com.citeraloka.phone

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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.*
import com.citeraloka.phone.domains.DomainManager
import com.citeraloka.phone.domains.DualDomainMediaResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MainActivity : Activity() {

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

    private lateinit var mainScrollView: ScrollView
    private lateinit var contentColumn: LinearLayout
    private lateinit var searchBarContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var categoryPillsRow: LinearLayout
    private lateinit var floatingSearchBtn: Button
    private lateinit var topDomainStatusView: TextView

    private lateinit var navHomeBtn: Button
    private lateinit var navContinueBtn: Button
    private lateinit var navMovieBtn: Button
    private lateinit var navSeriesBtn: Button
    private lateinit var navFilterBtn: Button
    private lateinit var navSettingsBtn: Button

    private lateinit var heroContainer: FrameLayout
    private lateinit var heroBackdropView: ImageView
    private lateinit var heroHotPickEyebrow: TextView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView
    private lateinit var heroCarouselDotsRow: LinearLayout

    private lateinit var continueWatchingContainer: LinearLayout
    private lateinit var recommendationContainer: LinearLayout
    private lateinit var gridContainer: GridLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sectionTitleView: TextView
    private lateinit var pageIndicatorText: TextView

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
    private val phonePrefs by lazy { getSharedPreferences("citeraloka_phone_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    private var isHeroAutoCarouselEnabled: Boolean
        get() = phonePrefs.getBoolean("hero_auto_carousel", true)
        set(value) = phonePrefs.edit().putBoolean("hero_auto_carousel", value).apply()

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
            if (isHeroAutoCarouselEnabled && hotPicksList.size > 1 && ::mainScrollView.isInitialized && mainScrollView.scrollY < dp(220)) {
                val nextIdx = (currentHeroSlideIndex + 1) % hotPicksList.size
                showHeroHotPickSlide(nextIdx, resetTimer = false)
            }
            mainHandler.postDelayed(this, 7000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor(BG_SURFACE_TEAL)
            window.navigationBarColor = Color.parseColor(BG_DEEP_TEAL)
        }

        val outerFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Bar Kepala Atas Mudah Alih (Logo Batik Gold 'C' + CITERALOKA + Status Domain)
        root.addView(buildMobileTopHeader())

        // 2. Jalur Tab Header Minimalis (HOME, CONTINUE WATCHING, MOVIE, TV SERIES, FILTER, SETTINGS)
        root.addView(buildCategoryFilterStrip())

        // 3. Bar Carian Boleh Lipat (Dibuka melalui Butang Floating SEARCH di Bawah)
        searchBarContainer = buildInlineSearchBar()
        root.addView(searchBarContainer)

        // 4. Kawasan Skrol Utama Portrait (Hero Carousel di atas ikut skrol naik apabila skrol ke bawah)
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

        // Pentas Hero Carousel "Hot Picks" Gaya Apple TV+ (ikut skrol halaman)
        heroContainer = buildPortraitHeroSpotlight()
        scrollBody.addView(heroContainer)

        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(84))
        }

        // Seksyen Sambung Menonton (Continue Watching)
        continueWatchingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        }
        contentColumn.addView(continueWatchingContainer)

        // Seksyen Cadangan Pintar (Disyorkan Untuk Anda)
        recommendationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        }
        contentColumn.addView(recommendationContainer)

        // Bar Tajuk Seksyen & Status Muatan
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(10))
        }

        sectionTitleView = TextView(this).apply {
            text = currentCategoryLabel
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                rightMargin = dp(6)
            }
        }

        statusText = TextView(this).apply {
            text = "Loading..."
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        headerRow.addView(sectionTitleView)
        headerRow.addView(progressBar)
        headerRow.addView(statusText)
        contentColumn.addView(headerRow)

        // 3-Column Portrait Catalog Grid
        gridContainer = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentColumn.addView(gridContainer)

        // Bottom Pagination Row (Ikon Minimalis ‹ / ›)
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }

        val prevBtn = createTouchButton("‹", isPrimary = false) {
            if (currentPage > 1 && currentCategoryLabel != "CONTINUE WATCHING") {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        }

        pageIndicatorText = TextView(this).apply {
            text = "PAGE 1"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(dp(14), 0, dp(14), 0)
        }

        val nextBtn = createTouchButton("›", isPrimary = true) {
            if (currentCategoryLabel != "CONTINUE WATCHING") {
                currentPage++
                loadCatalog(currentPath, currentPage)
            }
        }

        paginationRow.addView(prevBtn)
        paginationRow.addView(pageIndicatorText)
        paginationRow.addView(nextBtn)
        contentColumn.addView(paginationRow)

        scrollBody.addView(contentColumn)
        mainScrollView.addView(scrollBody)
        root.addView(mainScrollView)
        outerFrame.addView(root)

        // 5. Butang Ikon SEARCH Terapung di Bawah (Floating Search Icon Button - Phone)
        floatingSearchBtn = Button(this).apply {
            text = ""
            isAllCaps = false
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg("#EB071F29", ACCENT_TEAL, 2, 28)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            UiIcons.attachIcon(this, UiIcons.Type.SEARCH, sizeDp = 18, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(10).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                rightMargin = dp(18)
                bottomMargin = dp(20)
            }
            setOnClickListener {
                toggleFloatingSearchBar()
            }
        }
        outerFrame.addView(floatingSearchBtn)

        setContentView(outerFrame)

        refreshHeaderPillsAppearance()
        loadCatalog(currentPath, currentPage)

        // Semak kemas kini OTA Phone & penyelarasan domain secara senyap
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

    private fun buildMobileTopHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(12), dp(10), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val iconResId = resources.getIdentifier("ic_tv_logo", "drawable", packageName)
        if (iconResId != 0) {
            val brandIcon = ImageView(this).apply {
                setImageResource(iconResId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                    rightMargin = dp(7)
                }
            }
            header.addView(brandIcon)
        }

        val brandTitle = TextView(this).apply {
            text = "CITERALOKA"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(brandTitle)

        topDomainStatusView = TextView(this).apply {
            text = DomainManager.getDomainStatusBadge(this@MainActivity)
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            background = createRoundedBg("#1400D4B2", BORDER_SUBTLE_TEAL, 1, 20)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                showSettingsModal()
            }
        }
        header.addView(topDomainStatusView)

        // Butang Ikon FILTER di sebelah kanan atas
        navFilterBtn = Button(this).apply {
            text = ""
            isAllCaps = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), dp(7), dp(10), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            UiIcons.attachIcon(this, UiIcons.Type.FILTER, sizeDp = 15, paddingDp = 5)
            setOnClickListener { showFilterModal() }
        }
        header.addView(navFilterBtn)

        // Butang Ikon SETTINGS di sebelah kanan atas
        navSettingsBtn = Button(this).apply {
            text = ""
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), dp(7), dp(10), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            UiIcons.attachIcon(this, UiIcons.Type.SETTINGS, sizeDp = 15, paddingDp = 0)
            setOnClickListener { showSettingsModal() }
        }
        header.addView(navSettingsBtn)

        return header
    }

    private fun toggleFloatingSearchBar() {
        if (searchBarContainer.visibility == View.VISIBLE) {
            hideKeyboard()
            searchBarContainer.visibility = View.GONE
            floatingSearchBtn.text = ""
            UiIcons.attachIcon(floatingSearchBtn, UiIcons.Type.SEARCH, sizeDp = 18, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
        } else {
            searchBarContainer.visibility = View.VISIBLE
            floatingSearchBtn.text = ""
            UiIcons.attachIcon(floatingSearchBtn, UiIcons.Type.CLOSE, sizeDp = 18, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
            searchInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun createHeaderNavPill(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = true
            setSingleLine(true)
            maxLines = 1
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.2f)
            letterSpacing = 0.03f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(9), dp(6), dp(9), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(4)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun styleHeaderNavPill(btn: Button, isSelected: Boolean) {
        if (isSelected) {
            val c = Color.parseColor(TEXT_PRIMARY)
            btn.setTextColor(c)
            UiIcons.syncIconTint(btn, c)
            btn.typeface = Typeface.DEFAULT_BOLD
            btn.background = createRoundedBg("#2400D4B2", ACCENT_TEAL, 2, 24)
        } else {
            val c = Color.parseColor("#8AB8C2")
            btn.setTextColor(c)
            UiIcons.syncIconTint(btn, c)
            btn.typeface = Typeface.DEFAULT
            btn.background = createRoundedBg("#00000000", "#00000000", 0, 24)
        }
    }

    private fun refreshHeaderPillsAppearance() {
        if (::topDomainStatusView.isInitialized) {
            topDomainStatusView.text = DomainManager.getDomainStatusBadge(this)
        }
        if (::navHomeBtn.isInitialized) {
            styleHeaderNavPill(navHomeBtn, currentCategoryLabel == "HOME" && activeFilterLabel.isBlank())
        }
        if (::navContinueBtn.isInitialized) {
            styleHeaderNavPill(navContinueBtn, currentCategoryLabel == "CONTINUE WATCHING")
        }
        if (::navMovieBtn.isInitialized) {
            styleHeaderNavPill(navMovieBtn, currentCategoryLabel == "MOVIE" && activeFilterLabel.isBlank())
        }
        if (::navSeriesBtn.isInitialized) {
            styleHeaderNavPill(navSeriesBtn, currentCategoryLabel == "TV SERIES" && activeFilterLabel.isBlank())
        }
        if (::navFilterBtn.isInitialized) {
            navFilterBtn.text = activeFilterLabel
            UiIcons.attachIcon(navFilterBtn, UiIcons.Type.FILTER, sizeDp = 15, paddingDp = 5)
            styleHeaderNavPill(navFilterBtn, activeFilterLabel.isNotBlank())
        }
        if (::navSettingsBtn.isInitialized) {
            styleHeaderNavPill(navSettingsBtn, false)
        }
    }

    /**
     * Bar Halaman Header (HOME, CONTINUE WATCHING, MOVIE, TV SERIES)
     * Muat sepenuhnya dalam satu baris tanpa perlu skrol melintang!
     */
    private fun buildCategoryFilterStrip(): LinearLayout {
        categoryPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(10), dp(2), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        navHomeBtn = createHeaderNavPill("HOME") {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/latest"
            currentCategoryLabel = "HOME"
            refreshHeaderPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }

        navContinueBtn = createHeaderNavPill("CONTINUE WATCHING") {
            activeFilterLabel = ""
            currentCategoryLabel = "CONTINUE WATCHING"
            refreshHeaderPillsAppearance()
            openContinueWatchingTab()
        }

        navMovieBtn = createHeaderNavPill("MOVIE") {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/top-movie-today"
            currentCategoryLabel = "MOVIE"
            refreshHeaderPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }

        navSeriesBtn = createHeaderNavPill("TV SERIES") {
            activeFilterLabel = ""
            currentPage = 1
            currentPath = "/latest-series"
            currentCategoryLabel = "TV SERIES"
            refreshHeaderPillsAppearance()
            loadCatalog(currentPath, currentPage)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        categoryPillsRow.addView(navHomeBtn)
        categoryPillsRow.addView(navContinueBtn)
        categoryPillsRow.addView(navMovieBtn)
        categoryPillsRow.addView(navSeriesBtn)

        return categoryPillsRow
    }

    private fun openContinueWatchingTab() {
        val historyList = ContinueWatchingManager.getList(this)
        refreshContinueWatchingSection()
        if (historyList.isEmpty()) {
            Toast.makeText(this, "No titles in Continue Watching yet", Toast.LENGTH_SHORT).show()
            sectionTitleView.text = "CONTINUE WATCHING"
            statusText.text = "0 IN PROGRESS"
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
                source = if (cw.pageUrl.startsWith("watch32://") || cw.pageUrl.contains("watch32")) "WATCH32" else "LK21",
                statusBadge = "WATCHED ${cw.progressPercent}%"
            )
        }
        sectionTitleView.text = "CONTINUE WATCHING"
        statusText.text = "${cwItems.size} IN PROGRESS"
        hotPicksList.clear()
        hotPicksList.addAll(cwItems.take(7))
        currentHeroSlideIndex = 0
        renderHeroCarouselDots()
        showHeroHotPickSlide(0, resetTimer = true)
        renderPortraitGridCards(cwItems)
        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
    }

    private fun showFilterModal() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dm = resources.displayMetrics
        val modalWidth = (dm.widthPixels * 0.92f).toInt().coerceAtMost(dp(420))

        val outerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 20)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = ViewGroup.LayoutParams(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = "FILTER CATALOG & GENRES"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        val subView = TextView(this).apply {
            text = "Browse by Popular, Featured, Year, Country, or Genre:"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(titleView)
        root.addView(subView)

        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val pillWidth = (modalWidth - dp(56)) / 2
        filterCatalogOptions.forEach { (label, path) ->
            val isSel = (activeFilterLabel == label || currentPath == path)
            val btn = Button(this).apply {
                text = label
                isAllCaps = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (isSel) TEXT_PRIMARY else "#8AB8C2"))
                background = if (isSel) {
                    createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
                } else {
                    createRoundedBg("#140B2B38", "#00000000", 0, 24)
                }
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(10), dp(9), dp(10), dp(9))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = pillWidth
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    activeFilterLabel = label
                    currentCategoryLabel = label
                    currentPath = path
                    currentPage = 1
                    dialog.dismiss()
                    refreshHeaderPillsAppearance()
                    loadCatalog(currentPath, currentPage)
                }
            }
            grid.addView(btn)
        }
        root.addView(grid)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (activeFilterLabel.isNotBlank()) {
            bottomRow.addView(createTouchButton("CLEAR", isPrimary = false) {
                activeFilterLabel = ""
                currentCategoryLabel = "HOME"
                currentPath = "/latest"
                currentPage = 1
                dialog.dismiss()
                refreshHeaderPillsAppearance()
                loadCatalog(currentPath, currentPage)
            })
        }
        bottomRow.addView(createTouchButton("✕", isPrimary = false) {
            dialog.dismiss()
        })
        root.addView(bottomRow)

        outerScroll.addView(root)
        dialog.setContentView(outerScroll)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showSettingsModal() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dm = resources.displayMetrics
        val modalWidth = (dm.widthPixels * 0.92f).toInt().coerceAtMost(dp(420))

        val outerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 1, 20)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = ViewGroup.LayoutParams(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = "SETTINGS & PREFERENCES"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }
        val subView = TextView(this).apply {
            text = "Active Domain: ${DomainManager.getDomainStatusBadge(this@MainActivity)}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(titleView)
        root.addView(subView)

        val domainHeader = TextView(this).apply {
            text = "1. STREAMING DOMAIN SELECT (TICK ONE OR BOTH)"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(domainHeader)

        var lkChecked = DomainManager.isLk21Enabled(this)
        var w32Checked = DomainManager.isWatch32Enabled(this)

        lateinit var lkBtn: Button
        lateinit var w32Btn: Button

        fun refreshTickButtons() {
            lkBtn.text = if (lkChecked) "[✓]  1. LK21 (${baseUrl.removePrefix("https://")})" else "[   ]  1. LK21 (${baseUrl.removePrefix("https://")})"
            lkBtn.typeface = if (lkChecked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            lkBtn.setTextColor(Color.parseColor(if (lkChecked) TEXT_PRIMARY else "#8AB8C2"))
            lkBtn.background = if (lkChecked) {
                createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }

            w32Btn.text = if (w32Checked) "[✓]  2. WATCH32 (watch32.site)" else "[   ]  2. WATCH32 (watch32.site)"
            w32Btn.typeface = if (w32Checked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            w32Btn.setTextColor(Color.parseColor(if (w32Checked) TEXT_PRIMARY else "#8AB8C2"))
            w32Btn.background = if (w32Checked) {
                createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }
        }

        lkBtn = Button(this).apply {
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            setOnClickListener {
                if (lkChecked && !w32Checked) w32Checked = true
                lkChecked = !lkChecked
                refreshTickButtons()
            }
        }

        w32Btn = Button(this).apply {
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            setOnClickListener {
                if (w32Checked && !lkChecked) lkChecked = true
                w32Checked = !w32Checked
                refreshTickButtons()
            }
        }

        refreshTickButtons()
        root.addView(lkBtn)
        root.addView(w32Btn)

        val urlInput = EditText(this).apply {
            setText(baseUrl)
            hint = "LK21 Mirror URL (https://...)"
            setSingleLine(true)
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setHintTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createRoundedBg(BG_DEEP_TEAL, BORDER_SUBTLE_TEAL, 1, 14)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        root.addView(urlInput)

        val prefHeader = TextView(this).apply {
            text = "2. APP UPDATE & PLAYBACK SETTINGS"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(prefHeader)

        val checkUpdateBtn = createTouchButton("CHECK FOR UPDATE", isPrimary = true) {
            dialog.dismiss()
            AppUpdateManager.checkUpdate(this, isManual = true)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(checkUpdateBtn)

        val fitModes = listOf("contain" to "FIT", "cover" to "ZOOM", "fill" to "FILL")
        fun getAspectLabel(): String {
            val cur = phonePrefs.getString("default_aspect_mode", "contain") ?: "contain"
            val lbl = fitModes.firstOrNull { it.first == cur }?.second ?: "FIT"
            return "DEFAULT PLAYER ASPECT: $lbl"
        }

        lateinit var aspectPrefBtn: Button
        aspectPrefBtn = createTouchButton(getAspectLabel(), isPrimary = false) {
            val cur = phonePrefs.getString("default_aspect_mode", "contain") ?: "contain"
            val idx = fitModes.indexOfFirst { it.first == cur }.takeIf { it >= 0 } ?: 0
            val next = fitModes[(idx + 1) % fitModes.size]
            phonePrefs.edit().putString("default_aspect_mode", next.first).apply()
            aspectPrefBtn.text = getAspectLabel()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(aspectPrefBtn)

        lateinit var carouselToggleBtn: Button
        carouselToggleBtn = createTouchButton(
            "HOT PICKS AUTO-SLIDE: ${if (isHeroAutoCarouselEnabled) "ON" else "OFF"}",
            isPrimary = false
        ) {
            isHeroAutoCarouselEnabled = !isHeroAutoCarouselEnabled
            carouselToggleBtn.text = "HOT PICKS AUTO-SLIDE: ${if (isHeroAutoCarouselEnabled) "ON" else "OFF"}"
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(carouselToggleBtn)

        val clearHistoryBtn = createTouchButton("CLEAR CONTINUE WATCHING HISTORY", isPrimary = false) {
            ContinueWatchingManager.getList(this).forEach { cw ->
                ContinueWatchingManager.remove(this, cw.pageUrl)
            }
            refreshContinueWatchingSection()
            Toast.makeText(this, "Continue Watching history cleared", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(clearHistoryBtn)

        lateinit var clearCacheBtn: Button
        clearCacheBtn = createTouchButton("REMOVE CACHE (${getFormattedCacheSize()})", isPrimary = false) {
            val freedText = removeAllAppCache()
            clearCacheBtn.text = "REMOVE CACHE (0.0 MB)"
            UiIcons.attachIcon(clearCacheBtn, UiIcons.Type.TRASH, sizeDp = 14, paddingDp = 6)
            Toast.makeText(this, "Cache removed ($freedText freed)", Toast.LENGTH_SHORT).show()
        }.apply {
            UiIcons.attachIcon(this, UiIcons.Type.TRASH, sizeDp = 14, paddingDp = 6)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        root.addView(clearCacheBtn)

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        bottomRow.addView(createTouchButton("✕", isPrimary = false) {
            dialog.dismiss()
        })
        bottomRow.addView(createTouchButton("SAVE & APPLY", isPrimary = true) {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.startsWith("http")) {
                baseUrl = newUrl
            }
            DomainManager.setLk21Enabled(this, lkChecked)
            DomainManager.setWatch32Enabled(this, w32Checked)
            dialog.dismiss()
            refreshHeaderPillsAppearance()
            currentPage = 1
            loadCatalog(currentPath, currentPage)
        })
        root.addView(bottomRow)

        outerScroll.addView(root)
        dialog.setContentView(outerScroll)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun buildInlineSearchBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(14), dp(4), dp(14), dp(10))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        searchInput = EditText(this).apply {
            hint = "Search movies or TV series..."
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setHintTextColor(Color.parseColor("#64A6B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = createRoundedBg(BG_DEEP_TEAL, ACCENT_TEAL_DARK, 1, 24)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    performInlineSearch()
                    true
                } else {
                    false
                }
            }
        }

        val submitBtn = Button(this).apply {
            text = ""
            isAllCaps = false
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            UiIcons.attachIcon(this, UiIcons.Type.SEARCH, sizeDp = 16, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener { performInlineSearch() }
        }

        val closeSearchBtn = Button(this).apply {
            text = "✕"
            isAllCaps = false
            setTextColor(Color.parseColor("#8AB8C2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg("#1A0B2B38", "#00000000", 0, 24)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            setOnClickListener {
                hideKeyboard()
                bar.visibility = View.GONE
                if (::floatingSearchBtn.isInitialized) {
                    floatingSearchBtn.text = ""
                    UiIcons.attachIcon(floatingSearchBtn, UiIcons.Type.SEARCH, sizeDp = 18, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
                }
            }
        }

        bar.addView(searchInput)
        bar.addView(submitBtn)
        bar.addView(closeSearchBtn)
        return bar
    }

    private fun performInlineSearch() {
        val q = searchInput.text.toString().trim()
        if (q.isEmpty()) return
        hideKeyboard()
        searchBarContainer.visibility = View.GONE
        if (::floatingSearchBtn.isInitialized) {
            floatingSearchBtn.text = ""
            UiIcons.attachIcon(floatingSearchBtn, UiIcons.Type.SEARCH, sizeDp = 18, paddingDp = 0, colorInt = Color.parseColor(TEXT_PRIMARY))
        }
        activeFilterLabel = ""
        currentPage = 1
        currentCategoryLabel = "SEARCH: ${q.uppercase()}"
        currentPath = "/search?s=${URLEncoder.encode(q, "UTF-8")}"
        refreshHeaderPillsAppearance()
        loadCatalog(currentPath, currentPage)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    /**
     * Apple TV+ Style "Hot Picks" Hero Carousel Portrait Stage (Phone)
     * Scrolls up naturally when scrolling down the catalog.
     */
    private fun buildPortraitHeroSpotlight(): FrameLayout {
        val dm = resources.displayMetrics
        val heroHeight = (dm.heightPixels * 0.61f).toInt().coerceIn(dp(435), dp(555))

        val outerWrap = FrameLayout(this).apply {
            setPadding(dp(14), dp(10), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heroHeight
            )
        }

        val cardFrame = FrameLayout(this).apply {
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 14)
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Layer 1: Full-Height Backdrop / Poster inside Billboard Card
        heroBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        cardFrame.addView(heroBackdropView)

        // Layer 2A: Subtle Top Vignette
        val topVignette = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#B3031117"),
                    Color.TRANSPARENT
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(80),
                Gravity.TOP
            )
        }
        cardFrame.addView(topVignette)

        // Layer 2B: Rich Dark Teal Bottom Vignette for ClearLogo & Buttons
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
                (heroHeight * 0.74f).toInt(),
                Gravity.BOTTOM
            )
        }
        cardFrame.addView(bottomVignette)

        // Layer 3: Centered Bottom Billboard Content (Apple TV+ / Netflix Mobile Style)
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setPadding(dp(18), dp(14), dp(18), dp(16))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
            )
        }

        // Apple TV+ Eyebrow Badge ("HOT PICK #1 OF 7  •  APPLE TV+ SPOTLIGHT")
        heroHotPickEyebrow = TextView(this).apply {
            text = "HOT PICKS  •  APPLE TV+ SPOTLIGHT"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(5))
        }
        infoCol.addView(heroHotPickEyebrow)

        // Official ClearLogo PNG
        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = dp(255)
            maxHeight = dp(64)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dp(245),
                dp(62)
            ).apply {
                bottomMargin = dp(6)
            }
        }
        infoCol.addView(heroLogoView)

        // Bold Text Title Fallback
        heroTitleView = TextView(this).apply {
            text = "CITERALOKA"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(4))
        }
        infoCol.addView(heroTitleView)

        // Metadata Row (Genre • Runtime • Year)
        heroMetaView = TextView(this).apply {
            text = "Loading catalog..."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroMetaView)

        // Minimalist Badges Row (No Emojis)
        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(7))
        }
        infoCol.addView(heroBadgeRow)

        // Synopsis (3 Lines)
        heroPlotView = TextView(this).apply {
            text = ""
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.8f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        infoCol.addView(heroPlotView)

        // Action Row (PLAY NOW + INFO & SERVER)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val playBtn = Button(this).apply {
            text = "▶ PLAY"
            isAllCaps = true
            setTextColor(Color.parseColor(BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 24)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                activeHeroItem?.let { onItemClicked(it, playImmediately = !it.isSeries) }
            }
        }

        val infoBtn = Button(this).apply {
            text = "INFO & SERVER"
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg("#D9071F29", BORDER_SUBTLE_TEAL, 1, 24)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(6)
            }
            setOnClickListener {
                activeHeroItem?.let { onItemClicked(it, playImmediately = false) }
            }
        }

        actionRow.addView(playBtn)
        actionRow.addView(infoBtn)
        infoCol.addView(actionRow)

        // Apple TV+ Carousel Switcher Row (‹ + Animated Pill Dots + ›)
        val carouselRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg("#B3031117", BORDER_SUBTLE_TEAL, 1, 24)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val prevSlideBtn = TextView(this).apply {
            text = "‹"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                if (hotPicksList.isNotEmpty()) {
                    val prev = (currentHeroSlideIndex - 1 + hotPicksList.size) % hotPicksList.size
                    showHeroHotPickSlide(prev, resetTimer = true)
                }
            }
        }

        heroCarouselDotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }

        val nextSlideBtn = TextView(this).apply {
            text = "›"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                if (hotPicksList.isNotEmpty()) {
                    val next = (currentHeroSlideIndex + 1) % hotPicksList.size
                    showHeroHotPickSlide(next, resetTimer = true)
                }
            }
        }

        carouselRow.addView(prevSlideBtn)
        carouselRow.addView(heroCarouselDotsRow)
        carouselRow.addView(nextSlideBtn)
        infoCol.addView(carouselRow)

        cardFrame.addView(infoCol)

        val borderOverlay = View(this).apply {
            background = createRoundedBg("#00000000", BORDER_SUBTLE_TEAL, 1, 14)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        cardFrame.addView(borderOverlay)

        outerWrap.addView(cardFrame)
        return outerWrap
    }

    private fun renderHeroCarouselDots() {
        if (!::heroCarouselDotsRow.isInitialized) return
        heroCarouselDotsRow.removeAllViews()
        val total = hotPicksList.size
        if (total <= 1) return
        for (i in 0 until total) {
            val isActive = (i == currentHeroSlideIndex)
            val dotView = View(this).apply {
                val widthDp = if (isActive) dp(22) else dp(6)
                val heightDp = dp(6)
                val fillHex = if (isActive) ACCENT_TEAL else "#4D99F6E4"
                background = createRoundedBg(fillHex, fillHex, 0, 8)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(widthDp, heightDp).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
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

    private fun normalizeTitleKey(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun loadCatalog(path: String, page: Int) {
        progressBar.visibility = View.VISIBLE
        sectionTitleView.text = currentCategoryLabel
        pageIndicatorText.text = "PAGE $page"
        statusText.text = "Loading..."
        gridContainer.removeAllViews()
        refreshHeaderPillsAppearance()

        executor.execute {
            try {
                val combined = DomainManager.fetchCatalog(this@MainActivity, baseUrl, path, page)

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (combined.isEmpty()) {
                        statusText.text = "No titles found"
                    } else {
                        statusText.text = "${combined.size} TITLES"
                        allLoadedItems.clear()
                        allLoadedItems.addAll(combined)

                        hotPicksList.clear()
                        hotPicksList.addAll(combined.take(7))
                        currentHeroSlideIndex = 0
                        renderHeroCarouselDots()
                        showHeroHotPickSlide(0, resetTimer = true)

                        refreshContinueWatchingSection()
                        refreshRecommendationSection()
                        renderPortraitGridCards(combined)
                        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Connection error"
                }
            }
        }
    }

    private fun createHeroPillBadge(textLabel: String, textHex: String, bgHex: String, borderHex: String): TextView {
        return TextView(this).apply {
            text = textLabel
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
        ).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "HD Ad-Free Streaming" }
        heroMetaView.text = initialMeta

        renderHeroBadges(
            status = item.statusBadge.ifBlank { if (item.isSeries) "SERIES" else "RELEASED" },
            quality = item.episodeOrQuality.replace("W32 • ", "").ifBlank { "HD" },
            rating = item.rating,
            source = item.source
        )

        heroPlotView.text = item.plot.ifBlank { "Loading synopsis..." }

        val initialImg = item.backdropUrl.ifBlank { item.posterUrl }
        if (initialImg.isNotBlank()) {
            loadImageAsync(initialImg, heroBackdropView, crossfade = true)
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

    private fun scheduleHeroSpotlight(item: MediaItem, delayMs: Long = 0L, isHotPickSlide: Boolean = false) {
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
     * Seksyen Sambung Menonton (Continue Watching) - Hanya dipaparkan di dalam halaman CONTINUE WATCHING sahaja
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
            setPadding(dp(2), 0, dp(2), dp(8))
        }

        val sectionTitle = TextView(this).apply {
            text = "CONTINUE WATCHING"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val countText = TextView(this).apply {
            text = "${historyList.size} TITLES"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        }

        headerRow.addView(sectionTitle)
        headerRow.addView(countText)
        continueWatchingContainer.addView(headerRow)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val itemsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        historyList.forEach { item ->
            val cardWidth = dp(142)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(5), dp(5), dp(5), dp(7))
                layoutParams = LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(10)
                }
                setOnClickListener {
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

            val posterFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(92)
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

            val progressTrack = View(this).apply {
                setBackgroundColor(Color.parseColor("#99031117"))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(4),
                    Gravity.BOTTOM
                )
            }
            posterFrame.addView(progressTrack)

            val pct = item.progressPercent.coerceIn(6, 100)
            val progressFill = View(this).apply {
                setBackgroundColor(Color.parseColor(ACCENT_TEAL))
                layoutParams = FrameLayout.LayoutParams(
                    ((cardWidth - dp(10)) * (pct / 100f)).toInt(),
                    dp(4),
                    Gravity.BOTTOM
                )
            }
            posterFrame.addView(progressFill)

            val badgeView = TextView(this).apply {
                text = when {
                    item.episodeLabel.isNotBlank() -> item.episodeLabel.replace(Regex("(?i)episod(e)?"), "EP").uppercase()
                    item.remainingMinutes > 0 -> "${item.remainingMinutes}M LEFT"
                    else -> "$pct%"
                }
                setTextColor(Color.parseColor(BG_DEEP_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 4)
                setPadding(dp(5), dp(2), dp(5), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            posterFrame.addView(badgeView)

            val removeCardBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                background = createRoundedBg("#CC031117", BORDER_SUBTLE_TEAL, 1, 12)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    ContinueWatchingManager.remove(this@MainActivity, item.pageUrl)
                    openContinueWatchingTab()
                }
            }
            posterFrame.addView(removeCardBtn)

            val titleView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(5), dp(2), 0)
            }

            val subText = TextView(this).apply {
                text = "RESUME (${formatTime(item.currentSec)})"
                setTextColor(Color.parseColor(ACCENT_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(2), dp(2), dp(2), 0)
            }

            card.addView(posterFrame)
            card.addView(titleView)
            card.addView(subText)
            itemsRow.addView(card)
        }

        scroll.addView(itemsRow)
        continueWatchingContainer.addView(scroll)
    }

    /**
     * Seksyen Cadangan Pintar (Disyorkan Untuk Anda)
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
            setPadding(dp(2), 0, dp(2), dp(8))
        }

        val titleView = TextView(this).apply {
            text = "RECOMMENDED FOR YOU"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerRow.addView(titleView)
        recommendationContainer.addView(headerRow)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val itemsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        recommended.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(5), dp(5), dp(5), dp(7))
                layoutParams = LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(10)
                }
                setOnClickListener {
                    scheduleHeroSpotlight(item)
                    onItemClicked(item, playImmediately = !item.isSeries)
                }
                setOnLongClickListener {
                    scheduleHeroSpotlight(item)
                    onItemClicked(item, playImmediately = false)
                    true
                }
            }

            val posterFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(158)
                )
            }

            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            loadImageAsync(item.posterUrl, img)
            posterFrame.addView(img)

            if (item.rating.isNotBlank()) {
                val rBadge = TextView(this).apply {
                    text = item.rating
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg("#D904151F", BORDER_SUBTLE_TEAL, 1, 4)
                    setPadding(dp(5), dp(2), dp(5), dp(2))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                posterFrame.addView(rBadge)
            }

            val tView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(5), dp(2), 0)
            }

            card.addView(posterFrame)
            card.addView(tView)
            itemsRow.addView(card)
        }

        scroll.addView(itemsRow)
        recommendationContainer.addView(scroll)
    }

    /**
     * Grid Katalog Portrait 3 Kolum Responsif
     */
    private fun renderPortraitGridCards(items: List<MediaItem>) {
        gridContainer.removeAllViews()
        val dm = resources.displayMetrics
        val cols = 3
        gridContainer.columnCount = cols

        val availableWidth = dm.widthPixels - dp(28)
        val marginPerCard = dp(4)
        val cardWidth = (availableWidth / cols) - (marginPerCard * 2)
        val posterHeight = (cardWidth * 1.45f).toInt()

        items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
                setPadding(dp(4), dp(4), dp(4), dp(6))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    setMargins(marginPerCard, dp(5), marginPerCard, dp(5))
                }
                setOnClickListener {
                    scheduleHeroSpotlight(item)
                    onItemClicked(item, playImmediately = !item.isSeries)
                }
                setOnLongClickListener {
                    scheduleHeroSpotlight(item)
                    onItemClicked(item, playImmediately = false)
                    true
                }
            }

            val posterContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    posterHeight
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
                val cleanTag = item.episodeOrQuality.replace("W32 • ", "").uppercase()
                val badgeView = TextView(this).apply {
                    text = cleanTag
                    setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 4)
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    ).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                posterContainer.addView(badgeView)
            }

            if (item.rating.isNotBlank()) {
                val ratingView = TextView(this).apply {
                    text = item.rating
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg("#D904151F", BORDER_SUBTLE_TEAL, 1, 4)
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                posterContainer.addView(ratingView)
            }

            val titleView = TextView(this).apply {
                text = item.title
                setTextColor(Color.parseColor(TEXT_PRIMARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                minLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(5), dp(2), dp(3))
            }

            // Jalur sentuh bawah kad untuk terus buka pilihan Server / Episod / Sinopsis
            val infoActionBtn = TextView(this).apply {
                text = if (item.isSeries) "EPISODES" else "INFO & SERVER"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(ACCENT_TEAL))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.04f
                background = createRoundedBg(BG_SURFACE_TEAL, BORDER_SUBTLE_TEAL, 1, 5)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener {
                    scheduleHeroSpotlight(item)
                    onItemClicked(item, playImmediately = false)
                }
            }

            card.addView(posterContainer)
            card.addView(titleView)
            card.addView(infoActionBtn)
            gridContainer.addView(card)

            loadImageAsync(item.posterUrl, imageView)
        }
    }

    private fun onItemClicked(item: MediaItem, playImmediately: Boolean) {
        UserRecommendationManager.recordView(this, item.genre, item.url, weightBoost = 1)

        // Untuk Siri TV (Series sahaja): terus masuk ke Halaman Pilihan Siri Gaya Netflix Mobile
        if (item.isSeries) {
            launchSeriesDetailPage(item)
            return
        }

        if (playImmediately && !item.isSeries) {
            val defaultSrv = if (item.source == "WATCH32") "Cinezo (No Ads)" else "P2P Direct"
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
        statusText.text = "Loading details..."

        executor.execute {
            try {
                val dualResult = DomainManager.fetchDualDetails(
                    context = this@MainActivity,
                    lk21BaseUrl = baseUrl,
                    primaryUrl = item.url,
                    fallbackTitle = item.title,
                    isSeriesHint = item.isSeries
                )
                val detail = dualResult.primaryDetail ?: throw IllegalStateException("Server unreachable")

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "${allLoadedItems.size} TITLES"
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
                        showMobileDetailSheet(dualResult)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    val defaultSrv = if (item.source == "WATCH32") "Cinezo (No Ads)" else "P2P Direct"
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

    private fun showMobileDetailSheet(dualResult: DualDomainMediaResult) {
        val primaryDetail = dualResult.primaryDetail ?: return
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dm = resources.displayMetrics
        val modalWidth = (dm.widthPixels * 0.92f).toInt().coerceAtMost(dp(440))
        val maxListHeight = (dm.heightPixels * 0.44f).toInt().coerceIn(dp(220), dp(360))

        val modalRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 2, 18)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = ViewGroup.LayoutParams(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = primaryDetail.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaView = TextView(this).apply {
            val parts = listOfNotNull(
                primaryDetail.quality.takeIf { it.isNotBlank() },
                primaryDetail.rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
                if (dualResult.lk21Detail != null && dualResult.watch32Detail != null) "1. LK21 & 2. WATCH32" else primaryDetail.source
            )
            text = parts.joinToString("  •  ")
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }

        val plotView = TextView(this).apply {
            text = primaryDetail.plot
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(10))
        }

        modalRoot.addView(titleView)
        modalRoot.addView(metaView)
        modalRoot.addView(plotView)

        var tickLk21 = dualResult.lk21Detail != null && DomainManager.isLk21Enabled(this)
        var tickW32 = dualResult.watch32Detail != null && DomainManager.isWatch32Enabled(this)
        if (!tickLk21 && !tickW32) {
            tickLk21 = dualResult.lk21Detail != null
            tickW32 = dualResult.watch32Detail != null
        }

        val domainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        lateinit var lkDomainPill: Button
        lateinit var w32DomainPill: Button
        val optionsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun rebuildServerOptions() {
            optionsCol.removeAllViews()

            val activeDetail = when {
                tickLk21 && dualResult.lk21Detail != null -> dualResult.lk21Detail
                tickW32 && dualResult.watch32Detail != null -> dualResult.watch32Detail
                else -> primaryDetail
            }

            val combinedMirrors = mutableListOf<Pair<MediaDetail, MirrorItem>>()
            if (tickLk21 && dualResult.lk21Detail != null) {
                dualResult.lk21Detail.mirrors.forEach { m ->
                    combinedMirrors.add(dualResult.lk21Detail to m.copy(name = "[1. LK21] ${m.name}"))
                }
            }
            if (tickW32 && dualResult.watch32Detail != null) {
                dualResult.watch32Detail.mirrors.forEach { m ->
                    val cleanName = m.name.removePrefix("Watch32 • ").trim()
                    combinedMirrors.add(dualResult.watch32Detail to m.copy(name = "[2. WATCH32] $cleanName"))
                }
            }

            val firstChoice = combinedMirrors.firstOrNull()
            if (firstChoice != null) {
                val (firstDet, firstMir) = firstChoice
                val allMirs = combinedMirrors.map { it.second }
                val playBtn = createTouchButton("PLAY NOW (${firstMir.name})", isPrimary = true) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = firstDet.url,
                        embedUrl = firstMir.embedUrl,
                        title = primaryDetail.title,
                        serverName = firstMir.name,
                        posterUrl = primaryDetail.posterUrl,
                        genre = primaryDetail.genre,
                        mirrors = allMirs
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(10) }
                }
                optionsCol.addView(playBtn)
            } else if (!activeDetail.mainEmbedUrl.isNullOrBlank()) {
                val playBtn = createTouchButton("PLAY NOW", isPrimary = true) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = activeDetail.url,
                        embedUrl = activeDetail.mainEmbedUrl,
                        title = activeDetail.title,
                        serverName = activeDetail.mirrors.firstOrNull()?.name ?: "P2P Direct",
                        posterUrl = activeDetail.posterUrl,
                        genre = activeDetail.genre,
                        mirrors = activeDetail.mirrors
                    )
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(10) }
                }
                optionsCol.addView(playBtn)
            }

            if (combinedMirrors.isNotEmpty()) {
                val srvHeader = TextView(this).apply {
                    text = "SELECT SERVER (${combinedMirrors.size}):"
                    setTextColor(Color.parseColor(TEXT_MUTED))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = 0.05f
                    setPadding(0, dp(4), 0, dp(6))
                }
                optionsCol.addView(srvHeader)

                val allMirs = combinedMirrors.map { it.second }
                combinedMirrors.forEach { (det, mirror) ->
                    val srvBtn = createTouchButton(mirror.name, isPrimary = false) {
                        dialog.dismiss()
                        launchVideoPlayer(
                            pageUrl = det.url,
                            embedUrl = mirror.embedUrl,
                            title = primaryDetail.title,
                            serverName = mirror.name,
                            posterUrl = primaryDetail.posterUrl,
                            genre = primaryDetail.genre,
                            mirrors = allMirs
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

        fun updateDomainPillStyles() {
            val lkAvail = dualResult.lk21Detail != null
            lkDomainPill.text = if (!lkAvail) "1. LK21 (Tiada)" else if (tickLk21) "[✓] 1. LK21" else "[  ] 1. LK21"
            lkDomainPill.isEnabled = lkAvail
            lkDomainPill.typeface = if (tickLk21 && lkAvail) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            lkDomainPill.setTextColor(Color.parseColor(if (tickLk21 && lkAvail) TEXT_PRIMARY else "#8AB8C2"))
            lkDomainPill.background = if (tickLk21 && lkAvail) {
                createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }

            val w32Avail = dualResult.watch32Detail != null
            w32DomainPill.text = if (!w32Avail) "2. WATCH32 (Tiada)" else if (tickW32) "[✓] 2. WATCH32" else "[  ] 2. WATCH32"
            w32DomainPill.isEnabled = w32Avail
            w32DomainPill.typeface = if (tickW32 && w32Avail) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            w32DomainPill.setTextColor(Color.parseColor(if (tickW32 && w32Avail) TEXT_PRIMARY else "#8AB8C2"))
            w32DomainPill.background = if (tickW32 && w32Avail) {
                createRoundedBg("#2600D4B2", ACCENT_TEAL, 2, 24)
            } else {
                createRoundedBg("#140B2B38", "#00000000", 0, 24)
            }
        }

        lkDomainPill = Button(this).apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener {
                if (dualResult.lk21Detail == null) return@setOnClickListener
                if (tickLk21 && (!tickW32 || dualResult.watch32Detail == null)) {
                    if (dualResult.watch32Detail != null) tickW32 = true else return@setOnClickListener
                }
                tickLk21 = !tickLk21
                updateDomainPillStyles()
                rebuildServerOptions()
            }
        }

        w32DomainPill = Button(this).apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (dualResult.watch32Detail == null) return@setOnClickListener
                if (tickW32 && (!tickLk21 || dualResult.lk21Detail == null)) {
                    if (dualResult.lk21Detail != null) tickLk21 = true else return@setOnClickListener
                }
                tickW32 = !tickW32
                updateDomainPillStyles()
                rebuildServerOptions()
            }
        }

        domainRow.addView(lkDomainPill)
        domainRow.addView(w32DomainPill)
        modalRoot.addView(domainRow)

        updateDomainPillStyles()
        rebuildServerOptions()

        val listScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxListHeight
            )
        }
        listScroll.addView(optionsCol)
        modalRoot.addView(listScroll)

        val closeBtn = createTouchButton("✕", isPrimary = false) {
            dialog.dismiss()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        modalRoot.addView(closeBtn)

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

    private fun createTouchButton(label: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        val bg = if (isPrimary) "#2600D4B2" else "#140B2B38"
        val border = if (isPrimary) ACCENT_TEAL else "#00000000"
        val strokeW = if (isPrimary) 2 else 0
        val txtColor = if (isPrimary) TEXT_PRIMARY else "#8AB8C2"
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(txtColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = 0.05f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(bg, border, strokeW, 24)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }
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

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += getDirSize(child)
        }
        return total
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteDirContents(child)
            }
            try {
                child.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun getFormattedCacheSize(): String {
        val memBytes = imageCache.size().toLong()
        val diskBytes = getDirSize(cacheDir) +
            getDirSize(codeCacheDir) +
            getDirSize(externalCacheDir) +
            getDirSize(File(applicationInfo.dataDir, "app_webview/Cache")) +
            getDirSize(File(applicationInfo.dataDir, "app_webview/GPUCache"))
        val totalMb = (memBytes + diskBytes).toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", totalMb.coerceAtLeast(0.1))
    }

    private fun removeAllAppCache(): String {
        val beforeLabel = getFormattedCacheSize()
        try {
            imageCache.evictAll()
        } catch (_: Exception) {
        }
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
        }
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        try {
            WebView(this).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
                destroy()
            }
        } catch (_: Exception) {
        }
        executor.execute {
            deleteDirContents(cacheDir)
            deleteDirContents(codeCacheDir)
            deleteDirContents(externalCacheDir)
            deleteDirContents(File(applicationInfo.dataDir, "app_webview/Cache"))
            deleteDirContents(File(applicationInfo.dataDir, "app_webview/GPUCache"))
        }
        return beforeLabel
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (searchBarContainer.visibility == View.VISIBLE) {
            toggleFloatingSearchBar()
            return
        }
        super.onBackPressed()
    }
}
