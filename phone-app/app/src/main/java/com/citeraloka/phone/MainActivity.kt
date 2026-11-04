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
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

    private lateinit var heroContainer: FrameLayout
    private lateinit var heroBackdropView: ImageView
    private lateinit var heroLogoView: ImageView
    private lateinit var heroTitleView: TextView
    private lateinit var heroMetaView: TextView
    private lateinit var heroBadgeRow: LinearLayout
    private lateinit var heroPlotView: TextView

    private lateinit var continueWatchingContainer: LinearLayout
    private lateinit var recommendationContainer: LinearLayout
    private lateinit var gridContainer: GridLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sectionTitleView: TextView
    private lateinit var pageIndicatorText: TextView

    private val bottomNavIndicators = mutableListOf<View>()
    private val bottomNavLabels = mutableListOf<TextView>()
    private var activeBottomTabIndex = 0

    private var currentPage = 1
    private var currentPath = "/latest"
    private var currentCategoryLabel = "TERBARU"
    private var allLoadedItems = mutableListOf<MediaItem>()
    private var activeHeroItem: MediaItem? = null
    private var pendingHeroRunnable: Runnable? = null

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

    private val categoryButtons = mutableListOf<Pair<String, Button>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor(BG_SURFACE_TEAL)
            window.navigationBarColor = Color.parseColor(BG_SURFACE_TEAL)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG_DEEP_TEAL))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Bar Kepala Atas Mudah Alih (Logo Batik Gold 'C' + CITERALOKA + KEMAS KINI)
        root.addView(buildMobileTopHeader())

        // 2. Bar Carian Boleh Lipat (Inline Search Bar untuk Mobile)
        searchBarContainer = buildInlineSearchBar()
        root.addView(searchBarContainer)

        // 3. Jalur Kategori Mendatar (Sleek Minimal Box & Name Sahaja)
        root.addView(buildCategoryFilterStrip())

        // 4. Kawasan Skrol Utama Portrait (Hero di atas ikut skrol naik apabila skrol ke bawah)
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

        // Pentas Hero Spotlight Portrait Gaya Netflix Mobile (ikut skrol halaman)
        heroContainer = buildPortraitHeroSpotlight()
        scrollBody.addView(heroContainer)

        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(24))
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
            text = "Memuatkan..."
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        headerRow.addView(sectionTitleView)
        headerRow.addView(progressBar)
        headerRow.addView(statusText)
        contentColumn.addView(headerRow)

        // Grid Katalog 3 Kolum Portrait
        gridContainer = GridLayout(this).apply {
            columnCount = 3
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentColumn.addView(gridContainer)

        // Navigasi Halaman Bawah
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }

        val prevBtn = createTouchButton("SEBELUM", isPrimary = false) {
            if (currentPage > 1) {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        }

        pageIndicatorText = TextView(this).apply {
            text = "HAL 1"
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(dp(14), 0, dp(14), 0)
        }

        val nextBtn = createTouchButton("SETERUSNYA", isPrimary = true) {
            currentPage++
            loadCatalog(currentPath, currentPage)
        }

        paginationRow.addView(prevBtn)
        paginationRow.addView(pageIndicatorText)
        paginationRow.addView(nextBtn)
        contentColumn.addView(paginationRow)

        scrollBody.addView(contentColumn)
        mainScrollView.addView(scrollBody)
        root.addView(mainScrollView)

        // 5. Bottom Navigation Bar Gaya Netflix Mobile (Kekal di Bawah Skrin - Tanpa Emoji)
        root.addView(buildBottomNavigationBar())

        setContentView(root)

        updateActiveCategoryPill(currentPath)
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
        refreshContinueWatchingSection()
        refreshRecommendationSection()
    }

    private fun buildMobileTopHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(16), dp(10), dp(16), dp(8))
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
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    rightMargin = dp(10)
                }
            }
            header.addView(brandIcon)
        }

        val brandTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandTitle = TextView(this).apply {
            text = "CITERALOKA"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }

        val brandSub = TextView(this).apply {
            text = "MOBILE STREAM"
            setTextColor(Color.parseColor(TEXT_MUTED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.22f
        }

        brandTextCol.addView(brandTitle)
        brandTextCol.addView(brandSub)
        header.addView(brandTextCol)

        val updateBtn = Button(this).apply {
            text = "KEMAS KINI"
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 6)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                AppUpdateManager.checkUpdate(this@MainActivity, isManual = true)
            }
        }
        header.addView(updateBtn)

        return header
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
            hint = "Cari filem atau siri TV..."
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setHintTextColor(Color.parseColor("#64A6B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = createRoundedBg(BG_DEEP_TEAL, ACCENT_TEAL_DARK, 1, 8)
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
            text = "CARI"
            isAllCaps = true
            setTextColor(Color.parseColor(BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 8)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(6)
            }
            setOnClickListener { performInlineSearch() }
        }

        val closeSearchBtn = Button(this).apply {
            text = "TUTUP"
            isAllCaps = true
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 8)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                hideKeyboard()
                bar.visibility = View.GONE
                if (activeBottomTabIndex == 3) {
                    setBottomTabActive(0)
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
        currentPage = 1
        currentCategoryLabel = "CARIAN: ${q.uppercase()}"
        currentPath = "/search?s=${URLEncoder.encode(q, "UTF-8")}"
        updateActiveCategoryPill("")
        loadCatalog(currentPath, currentPage)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun buildCategoryFilterStrip(): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(14), dp(2), dp(14), dp(10))
        }

        categoryPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        categoryButtons.clear()
        categories.forEach { (label, path) ->
            val pill = Button(this).apply {
                text = label
                isAllCaps = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(14), dp(6), dp(14), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dp(8)
                }
                setOnClickListener {
                    currentPage = 1
                    currentPath = path
                    currentCategoryLabel = label
                    updateActiveCategoryPill(path)
                    when (path) {
                        "/top-movie-today", "/populer" -> setBottomTabActive(1)
                        "/latest-series" -> setBottomTabActive(2)
                        else -> setBottomTabActive(0)
                    }
                    loadCatalog(currentPath, currentPage)
                }
            }
            categoryButtons.add(path to pill)
            categoryPillsRow.addView(pill)
        }

        scroll.addView(categoryPillsRow)
        return scroll
    }

    private fun updateActiveCategoryPill(selectedPath: String) {
        categoryButtons.forEach { (path, btn) ->
            if (path == selectedPath) {
                btn.setTextColor(Color.parseColor(BG_DEEP_TEAL))
                btn.background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 16)
            } else {
                btn.setTextColor(Color.parseColor(TEXT_PRIMARY))
                btn.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 16)
            }
        }
    }

    /**
     * Pentas Hero Spotlight Portrait Gaya Netflix Mobile
     * Berada di bahagian atas skrol (ikut bergerak naik dan hilang apabila skrol ke bawah)
     */
    private fun buildPortraitHeroSpotlight(): FrameLayout {
        val dm = resources.displayMetrics
        val heroHeight = (dm.heightPixels * 0.48f).toInt().coerceIn(dp(330), dp(420))

        val outerWrap = FrameLayout(this).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
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

        // Lapisan 1: Gambar Backdrop / Poster Skrin Penuh dalam Kad Billboard
        heroBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        cardFrame.addView(heroBackdropView)

        // Lapisan 2A: Vignette Atas Halus
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
                dp(70),
                Gravity.TOP
            )
        }
        cardFrame.addView(topVignette)

        // Lapisan 2B: Vignette Bawah Pekat Dark Teal untuk kejelasan ClearLogo & Butang
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
                (heroHeight * 0.75f).toInt(),
                Gravity.BOTTOM
            )
        }
        cardFrame.addView(bottomVignette)

        // Lapisan 3: Kandungan Billboard Berpusat di Bawah (Gaya Netflix Mobile)
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM
            )
        }

        // Official ClearLogo PNG
        heroLogoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = dp(240)
            maxHeight = dp(58)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dp(230),
                dp(56)
            ).apply {
                bottomMargin = dp(6)
            }
        }
        infoCol.addView(heroLogoView)

        // Fallback Tajuk Teks Bold
        heroTitleView = TextView(this).apply {
            text = "CITERALOKA"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(4))
        }
        infoCol.addView(heroTitleView)

        // Baris Metadata (Genre • Durasi • Tahun)
        heroMetaView = TextView(this).apply {
            text = "Memuatkan katalog..."
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroMetaView)

        // Baris Badge Minimalis (Tanpa Emoji)
        heroBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        infoCol.addView(heroBadgeRow)

        // Sinopsis Ringkas (2 Baris)
        heroPlotView = TextView(this).apply {
            text = ""
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D5F5F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.14f)
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        infoCol.addView(heroPlotView)

        // Baris 2 Butang Utama Netflix Mobile (MAIN SEKARANG + INFO & SERVER)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val playBtn = Button(this).apply {
            text = "MAIN SEKARANG"
            isAllCaps = true
            setTextColor(Color.parseColor(BG_DEEP_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 8)
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
            background = createRoundedBg("#D9071F29", BORDER_SUBTLE_TEAL, 1, 8)
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

        cardFrame.addView(infoCol)

        // Border bingkai luar halus supaya sudut kekal tajam di semua versi Android
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

    /**
     * Bottom Navigation Bar Gaya Netflix Mobile (Kekal di Bawah Skrin - Sleek Box & Text Sahaja)
     */
    private fun buildBottomNavigationBar(): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val topDivider = View(this).apply {
            setBackgroundColor(Color.parseColor(BORDER_SUBTLE_TEAL))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
        wrapper.addView(topDivider)

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        }

        val tabs = listOf(
            "UTAMA",
            "UNGGULAN",
            "SIRI TV",
            "CARIAN"
        )

        bottomNavIndicators.clear()
        bottomNavLabels.clear()

        tabs.forEachIndexed { idx, title ->
            val tabBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    onBottomTabSelected(idx)
                }
            }

            val indicator = View(this).apply {
                background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 2)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(3)).apply {
                    bottomMargin = dp(6)
                }
                visibility = if (idx == 0) View.VISIBLE else View.INVISIBLE
            }

            val label = TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.07f
                setTextColor(
                    Color.parseColor(if (idx == 0) ACCENT_TEAL else TEXT_SECONDARY)
                )
            }

            bottomNavIndicators.add(indicator)
            bottomNavLabels.add(label)

            tabBox.addView(indicator)
            tabBox.addView(label)
            navRow.addView(tabBox)
        }

        wrapper.addView(navRow)
        return wrapper
    }

    private fun setBottomTabActive(index: Int) {
        activeBottomTabIndex = index
        for (i in bottomNavIndicators.indices) {
            bottomNavIndicators[i].visibility = if (i == index) View.VISIBLE else View.INVISIBLE
            bottomNavLabels[i].setTextColor(
                Color.parseColor(if (i == index) ACCENT_TEAL else TEXT_SECONDARY)
            )
        }
    }

    private fun onBottomTabSelected(index: Int) {
        setBottomTabActive(index)
        when (index) {
            0 -> {
                searchBarContainer.visibility = View.GONE
                hideKeyboard()
                currentPage = 1
                currentPath = "/latest"
                currentCategoryLabel = "TERBARU"
                updateActiveCategoryPill(currentPath)
                loadCatalog(currentPath, currentPage)
            }
            1 -> {
                searchBarContainer.visibility = View.GONE
                hideKeyboard()
                currentPage = 1
                currentPath = "/top-movie-today"
                currentCategoryLabel = "UNGGULAN"
                updateActiveCategoryPill(currentPath)
                loadCatalog(currentPath, currentPage)
            }
            2 -> {
                searchBarContainer.visibility = View.GONE
                hideKeyboard()
                currentPage = 1
                currentPath = "/latest-series"
                currentCategoryLabel = "SIRI TV"
                updateActiveCategoryPill(currentPath)
                loadCatalog(currentPath, currentPage)
            }
            3 -> {
                searchBarContainer.visibility = View.VISIBLE
                searchInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
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
        pageIndicatorText.text = "HAL $page"
        statusText.text = "Memuatkan..."
        gridContainer.removeAllViews()

        executor.execute {
            try {
                var usedAutoFallback = false
                val combined = mutableListOf<MediaItem>()

                // Mod AUTO Pintar: Gabungkan LK21 + Watch32 secara selari
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
                    w32Items.forEach { item ->
                        val key = normalizeTitleKey(item.title)
                        if (key.isNotBlank() && !seenKeys.contains(key)) {
                            seenKeys.add(key)
                            combined.add(item)
                        }
                    }
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (combined.isEmpty()) {
                        statusText.text = "Tiada tajuk ditemui"
                    } else {
                        statusText.text = if (usedAutoFallback) {
                            "WATCH32 • ${combined.size} TAJUK"
                        } else {
                            "${combined.size} TAJUK"
                        }
                        allLoadedItems.clear()
                        allLoadedItems.addAll(combined)

                        updateHeroSpotlightImmediate(combined.first())
                        scheduleHeroSpotlight(combined.first(), delayMs = 0L)
                        refreshContinueWatchingSection()
                        refreshRecommendationSection()
                        renderPortraitGridCards(combined)
                        mainScrollView.post { mainScrollView.scrollTo(0, 0) }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Ralat sambungan"
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
        ).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "Strim HD Bebas Iklan" }
        heroMetaView.text = initialMeta

        renderHeroBadges(
            status = item.statusBadge.ifBlank { if (item.isSeries) "SERIES" else "RELEASED" },
            quality = item.episodeOrQuality.replace("W32 • ", "").ifBlank { "HD" },
            rating = item.rating,
            source = item.source
        )

        heroPlotView.text = item.plot.ifBlank { "Memuatkan sinopsis cerita..." }

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

    private fun scheduleHeroSpotlight(item: MediaItem, delayMs: Long = 0L) {
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
     * Seksyen Sambung Menonton (Continue Watching) Mesra Sentuhan Jari
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
            setPadding(dp(2), 0, dp(2), dp(8))
        }

        val sectionTitle = TextView(this).apply {
            text = "SAMBUNG MENONTON"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val countText = TextView(this).apply {
            text = "${historyList.size} TAJUK"
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
                        .setTitle("PADAM SEJARAH TONTONAN?")
                        .setMessage(item.title)
                        .setPositiveButton("PADAM") { _, _ ->
                            ContinueWatchingManager.remove(this@MainActivity, item.pageUrl)
                            refreshContinueWatchingSection()
                        }
                        .setNegativeButton("BATAL", null)
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
                    item.episodeLabel.isNotBlank() -> item.episodeLabel.uppercase()
                    item.remainingMinutes > 0 -> "BAKI ${item.remainingMinutes}M"
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
                text = "SAMBUNG (${formatTime(item.currentSec)})"
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
            text = "DISYORKAN UNTUK ANDA"
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
                text = if (item.isSeries) "PILIH EPISOD" else "INFO & SERVER"
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

        if (playImmediately && !item.isSeries) {
            val defaultSrv = if (Watch32Scraper.isWatch32Uri(item.url)) {
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
        statusText.text = "Memuatkan butiran..."

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
                        lkDetail.copy(mirrors = mergedMirrors)
                    } else {
                        Watch32Scraper.findFallbackDetail(item.title, preferSeries = item.isSeries)
                            ?: throw IllegalStateException("Pelayan tidak dapat dihubungi")
                    }
                }

                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "${allLoadedItems.size} TAJUK"
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
                        showMobileDetailSheet(detail)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    val defaultSrv = if (Watch32Scraper.isWatch32Uri(item.url)) "W32 • Cinezo (No Ads)" else "P2P Direct"
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

    private fun showMobileDetailSheet(detail: MediaDetail) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dm = resources.displayMetrics
        val modalWidth = (dm.widthPixels * 0.92f).toInt().coerceAtMost(dp(440))
        val maxListHeight = (dm.heightPixels * 0.44f).toInt().coerceIn(dp(220), dp(360))

        val modalRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 2, 14)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = ViewGroup.LayoutParams(modalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = detail.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaView = TextView(this).apply {
            val parts = listOfNotNull(
                detail.quality.takeIf { it.isNotBlank() },
                detail.rating.takeIf { it.isNotBlank() }?.let { "IMDb $it" },
                detail.source.takeIf { it.isNotBlank() }
            )
            text = parts.joinToString("  •  ")
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
        }

        val plotView = TextView(this).apply {
            text = detail.plot
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(12))
        }

        modalRoot.addView(titleView)
        modalRoot.addView(metaView)
        modalRoot.addView(plotView)

        val listScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxListHeight
            )
        }

        val optionsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (!detail.mainEmbedUrl.isNullOrBlank()) {
            val playBtn = createTouchButton("MAIN VIDEO SEKARANG", isPrimary = true) {
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
        }

        if (detail.episodes.isNotEmpty()) {
            val epHeader = TextView(this).apply {
                text = "SENARAI EPISOD (${detail.episodes.size}):"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                setPadding(0, dp(4), 0, dp(6))
            }
            optionsCol.addView(epHeader)

            detail.episodes.forEach { ep ->
                val epBtn = createTouchButton(ep.label.uppercase(), isPrimary = false) {
                    dialog.dismiss()
                    val epSrv = if (Watch32Scraper.isWatch32Uri(ep.url)) "W32 • Cinezo (No Ads)" else "P2P Direct"
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
                    ).apply { bottomMargin = dp(7) }
                }
                optionsCol.addView(epBtn)
            }
        }

        if (detail.mirrors.size > 1) {
            val srvHeader = TextView(this).apply {
                text = "PILIHAN SERVER:"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                setPadding(0, dp(8), 0, dp(6))
            }
            optionsCol.addView(srvHeader)

            detail.mirrors.forEach { mirror ->
                val srvBtn = createTouchButton("SERVER: ${mirror.name}", isPrimary = false) {
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
                    ).apply { bottomMargin = dp(7) }
                }
                optionsCol.addView(srvBtn)
            }
        }

        listScroll.addView(optionsCol)
        modalRoot.addView(listScroll)

        val closeBtn = createTouchButton("TUTUP", isPrimary = false) {
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
    }

    private fun createTouchButton(label: String, isPrimary: Boolean, onClick: () -> Unit): Button {
        val bg = if (isPrimary) ACCENT_TEAL else BG_CARD_TEAL
        val border = if (isPrimary) ACCENT_TEAL else BORDER_SUBTLE_TEAL
        val txtColor = if (isPrimary) BG_DEEP_TEAL else TEXT_PRIMARY
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(txtColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            minHeight = 0
            minimumHeight = 0
            background = createRoundedBg(bg, border, 1, 8)
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
            hideKeyboard()
            searchBarContainer.visibility = View.GONE
            setBottomTabActive(0)
            return
        }
        super.onBackPressed()
    }
}
