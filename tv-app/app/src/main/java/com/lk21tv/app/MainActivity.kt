package com.lk21tv.app

import android.app.Activity
import android.app.AlertDialog
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
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newFixedThreadPool(6)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageCache = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var gridContainer: GridLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private var currentPage = 1
    private var currentPath = "/"

    private val prefs by lazy { getSharedPreferences("lk21_tv_prefs", Context.MODE_PRIVATE) }

    private var baseUrl: String
        get() = prefs.getString("base_url", "https://tv12.lk21official.cc") ?: "https://tv12.lk21official.cc"
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    private val categories = listOf(
        "🔥 Utama" to "/",
        "🆕 Terbaru" to "/latest/",
        "⭐ Populer" to "/populer/",
        "🇰🇷 Korea" to "/country/south-korea/",
        "🇨🇳 China" to "/country/china/",
        "🇺🇸 Barat" to "/country/united-states/",
        "⚔️ Action" to "/genre/action/",
        "🚀 Sci-Fi" to "/genre/scifi/"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Bar Navigasi Atas (Mesra Remote TV D-Pad)
        val topBarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandTitle = TextView(this).apply {
            text = "🎬 LK21 TV"
            setTextColor(Color.parseColor("#F43F5E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(20), 0)
        }
        topBar.addView(brandTitle)

        categories.forEach { (label, path) ->
            topBar.addView(createNavButton(label) {
                currentPage = 1
                currentPath = path
                loadCatalog(currentPath, currentPage)
            })
        }

        topBar.addView(createNavButton("🔍 Cari") { showSearchDialog() })
        topBar.addView(createNavButton("🌐 Buka Web (AdBlock)") {
            openPlayer(baseUrl, "Mod Pelayar TV (Bebas Iklan)", isDirectWeb = true)
        })
        topBar.addView(createNavButton("⚙️ Domain") { showDomainDialog() })

        topBarScroll.addView(topBar)
        root.addView(topBarScroll)

        // Bar Status & Loading
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                rightMargin = dp(10)
            }
        }
        statusText = TextView(this).apply {
            text = "Memuatkan katalog dari $baseUrl..."
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        statusRow.addView(progressBar)
        statusRow.addView(statusText)
        root.addView(statusRow)

        // Kontena Skrol Grid Poster
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        gridContainer = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = true
        }
        contentColumn.addView(gridContainer)

        // Butang Halaman Seterusnya / Sebelumnya
        val paginationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
        }
        paginationRow.addView(createNavButton("⬅️ Halaman Sebelumnya") {
            if (currentPage > 1) {
                currentPage--
                loadCatalog(currentPath, currentPage)
            }
        })
        paginationRow.addView(createNavButton("Halaman Seterusnya ➡️") {
            currentPage++
            loadCatalog(currentPath, currentPage)
        })
        contentColumn.addView(paginationRow)

        scrollView.addView(contentColumn)
        root.addView(scrollView)

        setContentView(root)

        loadCatalog(currentPath, currentPage)
    }

    private fun createNavButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isFocusable = true
            isFocusableInTouchMode = false
            background = createFocusableBg("#1E293B", "#E11D48")
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) {
                    createSolidBg("#E11D48", "#FFFFFF", 2)
                } else {
                    createSolidBg("#1E293B", "#334155", 1)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadCatalog(path: String, page: Int) {
        progressBar.visibility = View.VISIBLE
        val pagedPath = if (page <= 1 || path.contains("?s=")) {
            path
        } else {
            "${path.trimEnd('/')}/page/$page/"
        }
        statusText.text = "Memuatkan: $baseUrl$pagedPath (Halaman $page)..."
        gridContainer.removeAllViews()

        executor.execute {
            try {
                val items = LK21Scraper.fetchCatalog(baseUrl, pagedPath)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    if (items.isEmpty()) {
                        statusText.text = "Tiada cerita ditemui. Cuba buka 'Mod Web (AdBlock)' atau semak Domain."
                    } else {
                        statusText.text = "Menunjukkan ${items.size} cerita • Halaman $page • Bebas Iklan"
                        renderCards(items)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Gagal sambung ke $baseUrl (${e.message}). Boleh tukar domain atau guna Mod Web."
                }
            }
        }
    }

    private fun renderCards(items: List<MediaItem>) {
        gridContainer.removeAllViews()
        val displayMetrics = resources.displayMetrics
        val cols = if (displayMetrics.widthPixels >= dp(900)) 5 else 3
        gridContainer.columnCount = cols
        val cardWidth = (displayMetrics.widthPixels - dp(64)) / cols - dp(12)
        val cardHeight = (cardWidth * 1.42).toInt()

        items.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isFocusableInTouchMode = false
                background = createSolidBg("#1E293B", "#334155", 1)
                setPadding(dp(6), dp(6), dp(6), dp(8))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnFocusChangeListener { v, hasFocus ->
                    v.background = if (hasFocus) {
                        createSolidBg("#1E293B", "#F43F5E", 3)
                    } else {
                        createSolidBg("#1E293B", "#334155", 1)
                    }
                    v.scaleX = if (hasFocus) 1.04f else 1.0f
                    v.scaleY = if (hasFocus) 1.04f else 1.0f
                }
                setOnClickListener { onCardSelected(item) }
            }

            val posterContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cardHeight
                )
            }

            val imageView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#0F172A"))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            posterContainer.addView(imageView)

            if (item.episodeOrQuality.isNotBlank()) {
                val badgeView = TextView(this).apply {
                    text = item.episodeOrQuality
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createSolidBg("#E11D48", "#E11D48", 0)
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
                    text = "★ ${item.rating}"
                    setTextColor(Color.parseColor("#FACC15"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    background = createSolidBg("#CC0F172A", "#CC0F172A", 0)
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
                setTextColor(Color.parseColor("#F8FAFC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(6), dp(4), 0)
            }

            card.addView(posterContainer)
            card.addView(titleView)
            gridContainer.addView(card)

            if (index == 0) {
                card.requestFocus()
            }

            loadImageAsync(item.posterUrl, imageView)
        }
    }

    private fun onCardSelected(item: MediaItem) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Memuatkan senarai episod & pemain untuk: ${item.title}..."

        executor.execute {
            try {
                val detail = LK21Scraper.fetchDetail(baseUrl, item.url)
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    statusText.text = "Sedia: ${detail.title}"
                    showDetailDialog(detail)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    // Jika gagal ekstrak, terus buka halaman dalam pemain tanpa iklan
                    openPlayer(item.url, item.title, isDirectWeb = false)
                }
            }
        }
    }

    private fun showDetailDialog(detail: MediaDetail) {
        val options = mutableListOf<Pair<String, String>>()

        // Pilihan 1: Terus mainkan halaman/iframe utama
        val primaryTarget = detail.mainEmbedUrl ?: detail.url
        options.add("▶️ Tonton Terus (Pemain Utama Tanpa Iklan)" to primaryTarget)

        // Pilihan 2: Senarai Pelayan (Mirror)
        detail.mirrors.forEach { mirror ->
            options.add("🖥️ Pelayan: ${mirror.name}" to mirror.embedUrl)
        }

        // Pilihan 3: Senarai Episod (untuk Drama/Series)
        detail.episodes.forEach { ep ->
            options.add("🎞️ ${ep.label}" to ep.url)
        }

        options.add("🌐 Buka Halaman Penuh (Mod AdBlock TV)" to detail.url)

        val labels = options.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(detail.title)
            .setItems(labels) { _, which ->
                val chosenUrl = options[which].second
                val chosenLabel = options[which].first
                openPlayer(chosenUrl, "${detail.title} - $chosenLabel", isDirectWeb = false)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun openPlayer(targetUrl: String, title: String, isDirectWeb: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("TARGET_URL", targetUrl)
            putExtra("TITLE", title)
            putExtra("BASE_URL", baseUrl)
            putExtra("IS_DIRECT_WEB", isDirectWeb)
        }
        startActivity(intent)
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: Squid Game, Moving, Marvel..."
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("🔍 Cari Drama / Siri TV")
            .setView(input)
            .setPositiveButton("Cari") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    currentPage = 1
                    currentPath = "/?s=${URLEncoder.encode(q, "UTF-8")}"
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
        }
        AlertDialog.Builder(this)
            .setTitle("⚙️ Tetapan Domain LK21 / NontonDrama")
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

    private fun createSolidBg(fillHex: String, strokeHex: String, strokeWidthDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(8).toFloat()
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun createFocusableBg(normalHex: String, focusedHex: String): GradientDrawable {
        return createSolidBg(normalHex, "#334155", 1)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
