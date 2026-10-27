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

    // Tema Warna Netflix Dark Teal
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
    private val imageCache = object : LruCache<String, Bitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var heroContainer: FrameLayout
    private lateinit var gridContainer: GridLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sectionTitleView: TextView
    private var currentPage = 1
    private var currentPath = "/latest"
    private var currentCategoryLabel = "Filem Terbaru"

    private val prefs by lazy { getSharedPreferences("lk21_tv_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    private val categories = listOf(
        "🆕 Terbaru" to "/latest",
        "🔥 Beranda" to "/",
        "🏆 Unggulan" to "/top-movie-today",
        "⭐ Populer" to "/populer",
        "📺 Series" to "/latest-series",
        "🎬 2026" to "/year/2026",
        "🇺🇸 Barat" to "/country/usa",
        "🇰🇷 Korea" to "/country/south-korea",
        "⚔️ Action" to "/genre/action",
        "👻 Horror" to "/genre/horror",
        "🚀 Sci-Fi" to "/genre/sci-fi"
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

        // Bar Navigasi Atas Gaya Netflix Dark Teal
        val topBarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor(BG_SURFACE_TEAL))
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandLogo = TextView(this).apply {
            text = "LK21 STREAM"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, 0, dp(24), 0)
        }
        topBar.addView(brandLogo)

        categories.forEach { (label, path) ->
            topBar.addView(createNavPill(label) {
                currentPage = 1
                currentPath = path
                currentCategoryLabel = label
                loadCatalog(currentPath, currentPage)
            })
        }

        topBar.addView(createNavPill("🔍 Cari") { showSearchDialog() })
        topBar.addView(createNavPill("⚙️ Domain") { showDomainDialog() })

        topBarScroll.addView(topBar)
        root.addView(topBarScroll)

        // Kontena Skrol Utama (Hero Banner + Grid Katalog)
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isVerticalScrollBarEnabled = false
        }

        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(28))
        }

        // 1. Hero Banner Gaya Netflix (Tayangan Utama)
        heroContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        }
        contentColumn.addView(heroContainer)

        // 2. Bar Status & Tajuk Seksyen
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }

        sectionTitleView = TextView(this).apply {
            text = "🆕 Filem & Siri Terbaru"
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
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

        // 3. Grid Kad Poster Gaya Netflix
        gridContainer = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = true
        }
        contentColumn.addView(gridContainer)

        // 4. Navigasi Halaman (Pagination)
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(24))
        }
        paginationRow.addView(createActionButton("⬅  Halaman Sebelumnya", isPrimary = false) {
            if (currentPage > 1) {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        })
        paginationRow.addView(createActionButton("Halaman Seterusnya  ➡", isPrimary = true) {
            currentPage++
            loadCatalog(currentPath, currentPage)
        })
        contentColumn.addView(paginationRow)

        scrollView.addView(contentColumn)
        root.addView(scrollView)

        setContentView(root)

        loadCatalog(currentPath, currentPage)
    }

    private fun createNavPill(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 20)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(10)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 2, 20)
                    v.scaleX = 1.06f
                    v.scaleY = 1.06f
                } else {
                    (v as Button).setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 20)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) ACCENT_TEAL_DARK else BG_CARD_TEAL
        val normalBorder = if (isPrimary) ACCENT_TEAL else BORDER_SUBTLE_TEAL
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, 1, 10)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(12)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    v.background = createRoundedBg(ACCENT_TEAL, "#FFFFFF", 3, 10)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor(TEXT_PRIMARY))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 10)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadCatalog(path: String, page: Int) {
        progressBar.visibility = View.VISIBLE
        sectionTitleView.text = "$currentCategoryLabel  •  Halaman $page"
        statusText.text = "Menarik senarai cerita..."
        gridContainer.removeAllViews()

        executor.execute {
            try {
                val items = LK21Scraper.fetchCatalog(baseUrl, path, page)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (items.isEmpty()) {
                        statusText.text = "Tiada cerita ditemui pada halaman ini."
                    } else {
                        statusText.text = "${items.size} Tajuk Tersedia  •  Strim Terus HD"
                        renderHeroBanner(items.first())
                        renderCards(items)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Ralat sambungan: ${e.message}. Semak tetapan Domain."
                }
            }
        }
    }

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
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor(BORDER_SUBTLE_TEAL))
            }
            setPadding(dp(24), dp(20), dp(24), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Poster Kiri dalam Hero Banner
        val posterCard = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createRoundedBg(BG_DEEP_TEAL, ACCENT_TEAL, 1, 10)
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(185)).apply {
                rightMargin = dp(24)
            }
        }
        loadImageAsync(featured.posterUrl, posterCard)
        bannerCard.addView(posterCard)

        // Maklumat & Butang Kanan dalam Hero Banner
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tagBadge = TextView(this).apply {
            text = "★ TAYANGAN UTAMA LK21  •  ${featured.episodeOrQuality}  ${if (featured.rating.isNotBlank()) "•  IMDb ${featured.rating}" else ""}"
            setTextColor(Color.parseColor(ACCENT_TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }

        val heroTitle = TextView(this).apply {
            text = featured.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, dp(4))
        }

        val genreSub = TextView(this).apply {
            text = if (featured.genre.isNotBlank()) {
                "Genre: ${featured.genre}  •  Strim Video Terus Tanpa Iklan"
            } else {
                "Strim Video Terus HD  •  Tiada Pop-up  •  Kawalan Penuh Pemain TV"
            }
            setTextColor(Color.parseColor(TEXT_SECONDARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(14))
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Butang PLAY TERUS
        val playNowBtn = createActionButton("▶   Mainkan Sekarang", isPrimary = true) {
            onItemClicked(featured, playImmediately = true)
        }

        // Butang PILIH SERVER / EPISOD
        val detailBtn = createActionButton("ℹ️   Butiran & Server", isPrimary = false) {
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
                background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 10)
                setPadding(dp(6), dp(6), dp(6), dp(10))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.background = createRoundedBg("#114254", ACCENT_TEAL, 3, 10)
                        v.scaleX = 1.05f
                        v.scaleY = 1.05f
                    } else {
                        v.background = createRoundedBg(BG_CARD_TEAL, BORDER_SUBTLE_TEAL, 1, 10)
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                    }
                }
                // Bila pengguna klik kad cerita: jika Movie terus mainkan atau buka modal Dark Teal Netflix!
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
                    text = item.episodeOrQuality
                    setTextColor(Color.parseColor(BG_DEEP_TEAL))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg(ACCENT_TEAL, ACCENT_TEAL, 0, 6)
                    setPadding(dp(7), dp(2), dp(7), dp(2))
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
                    text = "★ ${item.rating}"
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedBg("#D904151F", BORDER_SUBTLE_TEAL, 1, 6)
                    setPadding(dp(7), dp(2), dp(7), dp(2))
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
                text = if (item.isSeries) "📺 Pilih Episod" else "▶ Klik Terus Main"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setPadding(dp(4), dp(2), dp(4), 0)
            }

            card.addView(posterContainer)
            card.addView(titleView)
            card.addView(subActionView)
            gridContainer.addView(card)

            if (index == 0) {
                card.requestFocus()
            }

            loadImageAsync(item.posterUrl, imageView)
        }
    }

    private fun onItemClicked(item: MediaItem, playImmediately: Boolean) {
        if (playImmediately && !item.isSeries) {
            // Terus buka Pemain Video Netflix Dark Teal & mainkan strim secara automatik!
            launchVideoPlayer(
                pageUrl = item.url,
                embedUrl = "",
                title = item.title,
                serverName = "P2P Direct"
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
                        serverName = "P2P Direct"
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
            background = createRoundedBg(BG_SURFACE_TEAL, ACCENT_TEAL, 2, 14)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            layoutParams = ViewGroup.LayoutParams(dp(680), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleView = TextView(this).apply {
            text = detail.title
            setTextColor(Color.parseColor(TEXT_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val metaView = TextView(this).apply {
            text = "${detail.quality}   ${if (detail.rating.isNotBlank()) "•   ★ IMDb ${detail.rating}" else ""}"
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

        // 1. Butang Mainkan Terus (jika ada pemain pada halaman ini)
        if (!detail.mainEmbedUrl.isNullOrBlank()) {
            val playBtn = createActionButton("▶   MAINKAN VIDEO SEKARANG (Terus Main)", isPrimary = true) {
                dialog.dismiss()
                launchVideoPlayer(
                    pageUrl = detail.url,
                    embedUrl = detail.mainEmbedUrl,
                    title = detail.title,
                    serverName = detail.mirrors.firstOrNull()?.name ?: "P2P Direct",
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

        // 2. Senarai Episod (jika Series)
        if (detail.episodes.isNotEmpty()) {
            val epHeader = TextView(this).apply {
                text = "📺 SENARAI EPISOD (${detail.episodes.size} Episod) — Klik Untuk Main Terus:"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(8))
            }
            optionsCol.addView(epHeader)

            detail.episodes.forEach { ep ->
                val epBtn = createActionButton("▶   ${ep.label}", isPrimary = false) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = ep.url,
                        embedUrl = "",
                        title = "${detail.title} - ${ep.label}",
                        serverName = "P2P Direct"
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

        // 3. Senarai Pilihan Server / Mirror
        if (detail.mirrors.size > 1) {
            val srvHeader = TextView(this).apply {
                text = "🖥️ PILIHAN PELAYAN (SERVER):"
                setTextColor(Color.parseColor(TEXT_MUTED))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, dp(8))
            }
            optionsCol.addView(srvHeader)

            detail.mirrors.forEach { mirror ->
                val srvBtn = createActionButton("⚡  Main guna Server: ${mirror.name}", isPrimary = false) {
                    dialog.dismiss()
                    launchVideoPlayer(
                        pageUrl = detail.url,
                        embedUrl = mirror.embedUrl,
                        title = detail.title,
                        serverName = mirror.name,
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

        val closeBtn = createActionButton("✖  Tutup", isPrimary = false) {
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
            putStringArrayListExtra("MIRROR_NAMES", mirrorNames)
            putStringArrayListExtra("MIRROR_URLS", mirrorUrls)
        }
        startActivity(intent)
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: Spider-Man, Venom, Doctor X..."
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("🔍 Cari Filem / Series")
            .setView(input)
            .setPositiveButton("Cari") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    currentPage = 1
                    currentCategoryLabel = "🔍 Hasil Carian: $q"
                    currentPath = "/search?s=${URLEncoder.encode(q, "UTF-8")}"
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDomainDialog() {
        val input = EditText(this).apply {
            setText(baseUrl)
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("⚙️ Tetapan Domain LK21")
            .setMessage("Jika domain tv12 bertukar (contoh ke tv13), kemas kini URL di bawah:")
            .setView(input)
            .setPositiveButton("Simpan & Muat Semula") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.startsWith("http")) {
                    baseUrl = newUrl
                    currentPage = 1
                    loadCatalog(currentPath, currentPage)
                }
            }
            .setNegativeButton("Batal", null)
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

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
