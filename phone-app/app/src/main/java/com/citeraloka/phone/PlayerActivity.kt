package com.citeraloka.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import java.util.concurrent.Executors

class PlayerActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var webView: WebView
    private lateinit var osdContainer: FrameLayout
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var loadingStatusText: TextView
    private lateinit var centerToastView: TextView

    // Kawalan OSD Sleek Minimalis Mudah Alih (Tanpa Emoji)
    private lateinit var titleTextView: TextView
    private lateinit var serverBadgeBtn: Button
    private lateinit var episodeListBtn: Button
    private lateinit var playPauseBtn: Button
    private lateinit var timeCurrentView: TextView
    private lateinit var timeTotalView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var volumeStatusBtn: Button
    private lateinit var speedBtn: Button
    private lateinit var aspectBtn: Button

    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector
    private var isPlaying = true
    private var isMuted = false
    private var currentDurationSec = 0
    private var currentPositionSec = 0
    private var isUserSeeking = false
    private var lastSavedSec = 0

    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1

    private val fitModes = listOf("contain" to "FIT", "cover" to "ZOOM", "fill" to "FILL")
    private var fitIndex = 0

    private var pageUrl = ""
    private var baseUrl = "https://tv12.lk21official.cc"
    private var currentTitle = "CITERALOKA PLAYER"
    private var posterUrl = ""
    private var currentGenre = ""
    private var currentEpisodeLabel = ""
    private var currentServerName = "P2P Direct"
    private var currentEmbedUrl = ""
    private var resumeSec = 0

    private var mirrors = mutableListOf<MirrorItem>()
    private var episodes = mutableListOf<EpisodeItem>()

    private val autoHideRunnable = Runnable {
        osdContainer.visibility = View.GONE
        enterImmersiveFullscreen()
    }

    private val hideToastRunnable = Runnable {
        centerToastView.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveFullscreen()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        pageUrl = intent.getStringExtra("PAGE_URL") ?: ""
        val initialEmbedUrl = intent.getStringExtra("EMBED_URL") ?: ""
        currentTitle = intent.getStringExtra("TITLE") ?: "CITERALOKA PLAYER"
        baseUrl = intent.getStringExtra("BASE_URL") ?: "https://tv12.lk21official.cc"
        currentServerName = intent.getStringExtra("SERVER_NAME") ?: "P2P Direct"
        posterUrl = intent.getStringExtra("POSTER_URL") ?: ""
        currentGenre = intent.getStringExtra("GENRE") ?: ""
        currentEpisodeLabel = intent.getStringExtra("EPISODE_LABEL") ?: ""
        resumeSec = intent.getIntExtra("RESUME_SEC", 0)

        val mirrorNames = intent.getStringArrayListExtra("MIRROR_NAMES") ?: arrayListOf()
        val mirrorUrls = intent.getStringArrayListExtra("MIRROR_URLS") ?: arrayListOf()
        for (i in 0 until minOf(mirrorNames.size, mirrorUrls.size)) {
            mirrors.add(MirrorItem(mirrorNames[i], mirrorUrls[i]))
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Enjin Video WebView Skrin Penuh Landscape
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        configureWebView()
        root.addView(webView)

        // 2. Lapisan Loading Minimalis Dark Teal
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6031117"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val spinner = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                bottomMargin = dp(14)
            }
        }
        loadingStatusText = TextView(this).apply {
            text = "MEMUATKAN STRIM HD..."
            setTextColor(Color.parseColor("#00D4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        loadingOverlay.addView(spinner)
        loadingOverlay.addView(loadingStatusText)
        root.addView(loadingOverlay)

        // 3. Toast Penunjuk Tengah
        centerToastView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            background = createRoundedBg("#E6071F29", "#00D4B2", 2, 8)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        root.addView(centerToastView)

        // Pengesan Sentuhan Jari: Single-tap buka/tutup OSD, Double-tap kiri/kanan -10s/+10s
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleOsd()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                val xRatio = e.x / width.toFloat()
                when {
                    xRatio < 0.35f -> seekRelative(-10)
                    xRatio > 0.65f -> seekRelative(10)
                    else -> togglePlayPause()
                }
                return true
            }
        })

        // 4. Antara Muka Kawalan Sentuh Pemain (OSD)
        osdContainer = buildTouchMinimalistOsd()
        root.addView(osdContainer)

        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        setContentView(root)

        startStreamResolution(pageUrl, initialEmbedUrl, currentServerName)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildTouchMinimalistOsd(): FrameLayout {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Sentuh ruang kosong tengah OSD untuk sembunyikan OSD
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }

        // BAR ATAS: KEMBALI, Tajuk Cerita, EPISOD, SERVER
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#EB031117"), Color.TRANSPARENT)
            )
            setPadding(dp(20), dp(14), dp(20), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val backBtn = createOsdButton("KEMBALI", isAccent = false) { finish() }

        titleTextView = TextView(this).apply {
            text = currentTitle.uppercase()
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        episodeListBtn = createOsdButton("EPISOD", isAccent = false) {
            showEpisodeSwitchDialog()
        }.apply {
            visibility = View.GONE
        }

        serverBadgeBtn = createOsdButton("SERVER: P2P", isAccent = true) {
            showServerSwitchDialog()
        }

        topBar.addView(backBtn)
        topBar.addView(titleTextView)
        topBar.addView(episodeListBtn)
        topBar.addView(serverBadgeBtn)
        container.addView(topBar)

        // PANEL BAWAH: SeekBar & Butang Sentuhan Minimalis (Tanpa Emoji)
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.parseColor("#F2031117"),
                    Color.parseColor("#CC071F29"),
                    Color.TRANSPARENT
                )
            )
            setPadding(dp(20), dp(18), dp(20), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val timelineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        timeCurrentView = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#00D4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        }

        seekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && currentDurationSec > 0) {
                        val targetSec = (progress.toLong() * currentDurationSec / 1000L).toInt()
                        timeCurrentView.text = formatTime(targetSec)
                        rescheduleAutoHide()
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isUserSeeking = false
                    if (currentDurationSec > 0 && sb != null) {
                        val targetSec = (sb.progress.toLong() * currentDurationSec / 1000L).toInt()
                        seekToSeconds(targetSec)
                    }
                }
            })
        }

        timeTotalView = TextView(this).apply {
            text = "--:--"
            setTextColor(Color.parseColor("#99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
        }

        timelineRow.addView(timeCurrentView)
        timelineRow.addView(seekBar)
        timelineRow.addView(timeTotalView)
        bottomPanel.addView(timelineRow)

        val controlsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        playPauseBtn = createOsdButton("PAUSE", isAccent = true) { togglePlayPause() }
        val rw10Btn = createOsdButton("-10s", isAccent = false) { seekRelative(-10) }
        val ff10Btn = createOsdButton("+10s", isAccent = false) { seekRelative(10) }
        val rw30Btn = createOsdButton("-30s", isAccent = false) { seekRelative(-30) }
        val ff30Btn = createOsdButton("+30s", isAccent = false) { seekRelative(30) }

        val volDownBtn = createOsdButton("VOL -", isAccent = false) { adjustVolume(-1) }
        volumeStatusBtn = createOsdButton("VOL ${getCurrentVolumePercent()}%", isAccent = false) { toggleMute() }
        val volUpBtn = createOsdButton("VOL +", isAccent = false) { adjustVolume(1) }

        speedBtn = createOsdButton("1.0x", isAccent = false) { cyclePlaybackSpeed() }
        aspectBtn = createOsdButton("FIT", isAccent = false) { cycleAspectRatio() }
        val hideOsdBtn = createOsdButton("TUTUP", isAccent = false) {
            osdContainer.visibility = View.GONE
            enterImmersiveFullscreen()
        }

        controlsRow.addView(playPauseBtn)
        controlsRow.addView(rw10Btn)
        controlsRow.addView(ff10Btn)
        controlsRow.addView(rw30Btn)
        controlsRow.addView(ff30Btn)
        controlsRow.addView(volDownBtn)
        controlsRow.addView(volumeStatusBtn)
        controlsRow.addView(volUpBtn)
        controlsRow.addView(speedBtn)
        controlsRow.addView(aspectBtn)
        controlsRow.addView(hideOsdBtn)

        controlsScroll.addView(controlsRow)
        bottomPanel.addView(controlsScroll)
        container.addView(bottomPanel)

        return container
    }

    private fun startStreamResolution(targetPageUrl: String, targetEmbedUrl: String, serverLabel: String) {
        loadingOverlay.visibility = View.VISIBLE
        loadingStatusText.text = "MEMUATKAN ($serverLabel)..."
        serverBadgeBtn.text = "SERVER: $serverLabel"

        executor.execute {
            try {
                var embedToResolve = targetEmbedUrl
                var effectiveLabel = serverLabel
                var usedWatch32Fallback = false

                if (embedToResolve.isBlank() && targetPageUrl.isNotBlank()) {
                    if (Watch32Scraper.isWatch32Uri(targetPageUrl)) {
                        val detail = Watch32Scraper.fetchDetail(targetPageUrl)
                        currentTitle = detail.title
                        if (posterUrl.isBlank()) posterUrl = detail.posterUrl
                        if (currentGenre.isBlank()) currentGenre = detail.genre
                        mirrors.clear()
                        mirrors.addAll(detail.mirrors)
                        episodes.clear()
                        episodes.addAll(detail.episodes)

                        if (detail.mainEmbedUrl.isNullOrBlank() && detail.episodes.isNotEmpty()) {
                            val firstEp = detail.episodes.first()
                            val epDetail = Watch32Scraper.fetchDetail(firstEp.url)
                            pageUrl = firstEp.url
                            currentTitle = epDetail.title
                            currentEpisodeLabel = firstEp.label
                            mirrors.clear()
                            mirrors.addAll(epDetail.mirrors)
                            embedToResolve = epDetail.mainEmbedUrl ?: epDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                        } else {
                            embedToResolve = detail.mainEmbedUrl ?: detail.mirrors.firstOrNull()?.embedUrl ?: ""
                        }
                        effectiveLabel = mirrors.firstOrNull()?.name ?: "W32 • Cinezo (No Ads)"
                    } else {
                        val lkDetail = try {
                            LK21Scraper.fetchDetail(baseUrl, targetPageUrl)
                        } catch (_: Exception) {
                            null
                        }

                        if (lkDetail != null && (!lkDetail.mainEmbedUrl.isNullOrBlank() || lkDetail.mirrors.isNotEmpty() || lkDetail.episodes.isNotEmpty())) {
                            currentTitle = lkDetail.title
                            if (posterUrl.isBlank()) posterUrl = lkDetail.posterUrl
                            if (currentGenre.isBlank()) currentGenre = lkDetail.genre
                            mirrors.clear()
                            mirrors.addAll(lkDetail.mirrors)
                            episodes.clear()
                            episodes.addAll(lkDetail.episodes)

                            if (lkDetail.mainEmbedUrl.isNullOrBlank() && lkDetail.episodes.isNotEmpty()) {
                                val firstEp = lkDetail.episodes.first()
                                val epDetail = LK21Scraper.fetchDetail(baseUrl, firstEp.url)
                                currentTitle = "${lkDetail.title} • ${firstEp.label}"
                                currentEpisodeLabel = firstEp.label
                                mirrors.clear()
                                mirrors.addAll(epDetail.mirrors)
                                embedToResolve = epDetail.mainEmbedUrl ?: epDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                            } else {
                                embedToResolve = lkDetail.mainEmbedUrl ?: lkDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                            }

                            val backupMirrors = Watch32Scraper.findBackupMirrorsForLk21(
                                rawTitle = currentTitle,
                                episodeLabel = currentEpisodeLabel,
                                isSeries = episodes.isNotEmpty() || currentEpisodeLabel.isNotBlank()
                            )
                            backupMirrors.forEach { bm ->
                                if (mirrors.none { it.embedUrl == bm.embedUrl }) {
                                    mirrors.add(bm)
                                }
                            }
                        }

                        if (embedToResolve.isBlank()) {
                            val fbDetail = Watch32Scraper.findFallbackDetail(
                                rawTitle = currentTitle,
                                episodeLabel = currentEpisodeLabel,
                                preferSeries = currentEpisodeLabel.isNotBlank()
                            )
                            if (fbDetail != null) {
                                usedWatch32Fallback = true
                                if (posterUrl.isBlank()) posterUrl = fbDetail.posterUrl
                                if (currentGenre.isBlank()) currentGenre = fbDetail.genre
                                mirrors.clear()
                                mirrors.addAll(fbDetail.mirrors)
                                if (episodes.isEmpty()) {
                                    episodes.addAll(fbDetail.episodes)
                                }
                                embedToResolve = fbDetail.mainEmbedUrl ?: fbDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                                effectiveLabel = fbDetail.mirrors.firstOrNull()?.name ?: "W32 • Cinezo (No Ads)"
                            }
                        }
                    }
                }

                if (embedToResolve.isBlank()) {
                    throw IllegalStateException("Tiada pelayan aktif ditemui")
                }

                currentEmbedUrl = embedToResolve
                currentServerName = effectiveLabel
                val resolved = LK21Scraper.resolveStream(embedToResolve, effectiveLabel)

                UserRecommendationManager.recordView(this@PlayerActivity, currentGenre, targetPageUrl, weightBoost = 3)

                mainHandler.post {
                    titleTextView.text = currentTitle.uppercase()
                    serverBadgeBtn.text = "SERVER: ${resolved.serverName}"
                    episodeListBtn.visibility = if (episodes.isNotEmpty()) View.VISIBLE else View.GONE
                    if (usedWatch32Fallback) {
                        showCenterToast("AUTO-FALLBACK: SERVER WATCH32 AKTIF")
                    }

                    when {
                        resolved.isDirectHls -> loadDirectHlsPlayer(resolved.streamUrl, resolved.referer)
                        resolved.isDirectWebEmbed -> loadDirectWebEmbedPlayer(resolved.streamUrl, resolved.referer)
                        else -> loadProtectedEmbedPlayer(resolved.streamUrl, resolved.referer)
                    }
                    rescheduleAutoHide()
                }
            } catch (_: Exception) {
                mainHandler.post {
                    loadingOverlay.visibility = View.GONE
                    showCenterToast("RALAT PELAYAN. SILA TUKAR SERVER.")
                }
            }
        }
    }

    private fun loadDirectHlsPlayer(m3u8Url: String, refererOrigin: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: 100vw;
                        height: 100vh;
                        background: #000000;
                        overflow: hidden;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    video {
                        width: 100vw;
                        height: 100vh;
                        background: #000000;
                        object-fit: contain;
                        outline: none;
                    }
                </style>
                <script src="https://playcdn.de/js/hls-0.14.17.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js"></script>
            </head>
            <body>
                <video id="tv-video" autoplay playsinline></video>
                <script>
                    var video = document.getElementById('tv-video');
                    var streamUrl = "$m3u8Url";

                    function startPlayback() {
                        if (window.Hls && Hls.isSupported()) {
                            var hls = new Hls({
                                maxBufferLength: 60,
                                maxMaxBufferLength: 120,
                                enableWorker: true
                            });
                            hls.loadSource(streamUrl);
                            hls.attachMedia(video);
                            hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                video.play().catch(function(){});
                                if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                            });
                            hls.on(Hls.Events.ERROR, function(event, data) {
                                if (data.fatal) {
                                    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                                        hls.startLoad();
                                    } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                                        hls.recoverMediaError();
                                    }
                                }
                            });
                        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                            video.src = streamUrl;
                            video.addEventListener('loadedmetadata', function() {
                                video.play().catch(function(){});
                                if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                            });
                        }
                    }

                    video.addEventListener('playing', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(true);
                    });
                    video.addEventListener('pause', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(false);
                    });
                    video.addEventListener('timeupdate', function() {
                        if (window.AndroidPlayer) {
                            window.AndroidPlayer.onTimeUpdate(
                                Math.floor(video.currentTime || 0),
                                Math.floor(video.duration || 0)
                            );
                        }
                    });

                    window.playerControl = {
                        togglePlay: function() {
                            if (video.paused) { video.play(); return true; }
                            else { video.pause(); return false; }
                        },
                        seekBy: function(deltaSec) {
                            video.currentTime = Math.max(0, (video.currentTime || 0) + deltaSec);
                        },
                        seekTo: function(targetSec) {
                            video.currentTime = Math.max(0, targetSec);
                        },
                        setSpeed: function(rate) {
                            video.playbackRate = rate;
                        },
                        setFit: function(mode) {
                            video.style.objectFit = mode;
                        },
                        setMuted: function(mute) {
                            video.muted = mute;
                        }
                    };

                    startPlayback();
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(refererOrigin, html, "text/html", "UTF-8", null)
    }

    private fun loadProtectedEmbedPlayer(embedUrl: String, refererOrigin: String) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background: #000; overflow: hidden; }
                    html, body, iframe { width: 100vw; height: 100vh; border: 0; display: block; }
                </style>
            </head>
            <body>
                <iframe id="embed-frame" src="$embedUrl" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="autoplay; fullscreen; picture-in-picture; encrypted-media"></iframe>
                <script>
                    var f = document.getElementById('embed-frame');
                    f.addEventListener('load', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(refererOrigin, html, "text/html", "UTF-8", null)
    }

    private fun loadDirectWebEmbedPlayer(embedUrl: String, refererOrigin: String) {
        val headers = mapOf(
            "Referer" to refererOrigin,
            "Origin" to refererOrigin.trimEnd('/')
        )
        webView.loadUrl(embedUrl, headers)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/125.0.0.0 Mobile Safari/537.36"

        webView.addJavascriptInterface(PlayerJsBridge(), "AndroidPlayer")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null && AdBlocker.shouldBlockRequest(request)) {
                    return AdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                if (!AdBlocker.isAllowedPlayerNavigation(url)) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(AdBlocker.CLEAN_EMBED_JS, null)
                view?.evaluateJavascript(
                    """
                    (function() {
                        if (window.__citeralokaBridgeActive) return;
                        window.__citeralokaBridgeActive = true;
                        function bindVideoTag() {
                            var v = document.querySelector('video');
                            if (v && !v.__clBound) {
                                v.__clBound = true;
                                if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                                v.addEventListener('loadeddata', function() {
                                    if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                                });
                                v.addEventListener('playing', function() {
                                    if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(true);
                                });
                                v.addEventListener('pause', function() {
                                    if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(false);
                                });
                                v.addEventListener('timeupdate', function() {
                                    if (window.AndroidPlayer && v.duration > 0) {
                                        window.AndroidPlayer.onTimeUpdate(
                                            Math.floor(v.currentTime || 0),
                                            Math.floor(v.duration || 0)
                                        );
                                    }
                                });
                            }
                        }
                        setInterval(bindVideoTag, 1000);
                        setTimeout(function() {
                            if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                        }, 2200);
                    })();
                    """.trimIndent(),
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return false
            }
        }
    }

    inner class PlayerJsBridge {
        @JavascriptInterface
        fun onVideoReady() {
            mainHandler.post {
                loadingOverlay.visibility = View.GONE
                if (resumeSec > 0) {
                    seekToSeconds(resumeSec)
                    showCenterToast("SAMBUNG: ${formatTime(resumeSec)}")
                    resumeSec = 0
                }
            }
        }

        @JavascriptInterface
        fun onPlayStateChanged(playing: Boolean) {
            mainHandler.post {
                isPlaying = playing
                playPauseBtn.text = if (playing) "PAUSE" else "PLAY"
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSec: Int, durationSec: Int) {
            mainHandler.post {
                if (durationSec > 0) {
                    currentDurationSec = durationSec
                    currentPositionSec = currentSec
                    timeTotalView.text = formatTime(durationSec)
                    if (!isUserSeeking) {
                        timeCurrentView.text = formatTime(currentSec)
                        val progress = ((currentSec.toLong() * 1000L) / durationSec).toInt()
                        seekBar.progress = progress.coerceIn(0, 1000)
                    }

                    if (Math.abs(currentSec - lastSavedSec) >= 5) {
                        lastSavedSec = currentSec
                        saveCurrentProgress()
                    }
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        if (currentPositionSec > 5 && currentDurationSec > 0) {
            ContinueWatchingManager.saveProgress(
                context = this,
                pageUrl = pageUrl,
                embedUrl = currentEmbedUrl,
                title = currentTitle,
                posterUrl = posterUrl,
                currentSec = currentPositionSec,
                durationSec = currentDurationSec,
                episodeLabel = currentEpisodeLabel,
                serverName = currentServerName,
                genre = currentGenre
            )
        }
    }

    private fun togglePlayPause() {
        rescheduleAutoHide()
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl) return window.playerControl.togglePlay();
                var v = document.querySelector('video');
                if (v) {
                    if (v.paused) { v.play(); return true; }
                    else { v.pause(); return false; }
                }
                return true;
            })();
            """.trimIndent()
        ) { res ->
            val nowPlaying = res?.contains("true") == true
            isPlaying = nowPlaying
            playPauseBtn.text = if (nowPlaying) "PAUSE" else "PLAY"
            showCenterToast(if (nowPlaying) "PLAY" else "PAUSE")
        }
    }

    private fun seekRelative(deltaSeconds: Int) {
        rescheduleAutoHide()
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl) { window.playerControl.seekBy($deltaSeconds); return; }
                var v = document.querySelector('video');
                if (v) v.currentTime = Math.max(0, (v.currentTime || 0) + $deltaSeconds);
            })();
            """.trimIndent(),
            null
        )
        val label = if (deltaSeconds > 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
        showCenterToast(label)
    }

    private fun seekToSeconds(targetSeconds: Int) {
        rescheduleAutoHide()
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl) { window.playerControl.seekTo($targetSeconds); return; }
                var v = document.querySelector('video');
                if (v) v.currentTime = $targetSeconds;
            })();
            """.trimIndent(),
            null
        )
        showCenterToast(formatTime(targetSeconds))
    }

    private fun adjustVolume(direction: Int) {
        rescheduleAutoHide()
        val flag = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
        isMuted = false
        webView.evaluateJavascript("if(window.playerControl) window.playerControl.setMuted(false);", null)
        val pct = getCurrentVolumePercent()
        volumeStatusBtn.text = "VOL $pct%"
        showCenterToast("VOL: $pct%")
    }

    private fun toggleMute() {
        rescheduleAutoHide()
        isMuted = !isMuted
        webView.evaluateJavascript("if(window.playerControl) window.playerControl.setMuted($isMuted);", null)
        if (isMuted) {
            volumeStatusBtn.text = "MUTED"
            showCenterToast("MUTED")
        } else {
            val pct = getCurrentVolumePercent()
            volumeStatusBtn.text = "VOL $pct%"
            showCenterToast("VOL: $pct%")
        }
    }

    private fun getCurrentVolumePercent(): Int {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return ((cur * 100f) / max).toInt()
    }

    private fun cyclePlaybackSpeed() {
        rescheduleAutoHide()
        speedIndex = (speedIndex + 1) % speeds.size
        val rate = speeds[speedIndex]
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl) window.playerControl.setSpeed($rate);
                var v = document.querySelector('video');
                if (v) v.playbackRate = $rate;
            })();
            """.trimIndent(),
            null
        )
        speedBtn.text = "${rate}x"
        showCenterToast("SPEED: ${rate}x")
    }

    private fun cycleAspectRatio() {
        rescheduleAutoHide()
        fitIndex = (fitIndex + 1) % fitModes.size
        val (cssFit, label) = fitModes[fitIndex]
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl) window.playerControl.setFit('$cssFit');
                var v = document.querySelector('video');
                if (v) v.style.objectFit = '$cssFit';
            })();
            """.trimIndent(),
            null
        )
        aspectBtn.text = label
        showCenterToast("SKRIN: $label")
    }

    private fun showServerSwitchDialog() {
        if (mirrors.isEmpty()) {
            showCenterToast("TIADA SERVER ALTERNATIF")
            return
        }
        val names = mirrors.map { "SERVER: ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("TUKAR SERVER")
            .setItems(names) { _, which ->
                val chosen = mirrors[which]
                startStreamResolution(pageUrl, chosen.embedUrl, chosen.name)
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun showEpisodeSwitchDialog() {
        if (episodes.isEmpty()) return
        val names = episodes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("PILIH EPISOD")
            .setItems(names) { _, which ->
                val ep = episodes[which]
                pageUrl = ep.url
                currentEpisodeLabel = ep.label
                currentTitle = "${currentTitle.substringBefore(" • ")} • ${ep.label}"
                val defaultLabel = if (Watch32Scraper.isWatch32Uri(ep.url)) {
                    "W32 • Cinezo (No Ads)"
                } else {
                    "P2P Direct"
                }
                startStreamResolution(ep.url, "", defaultLabel)
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun toggleOsd() {
        if (osdContainer.visibility == View.VISIBLE) {
            osdContainer.visibility = View.GONE
            mainHandler.removeCallbacks(autoHideRunnable)
            enterImmersiveFullscreen()
        } else {
            osdContainer.visibility = View.VISIBLE
            rescheduleAutoHide()
        }
    }

    private fun rescheduleAutoHide() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, 4500)
    }

    private fun showCenterToast(message: String) {
        centerToastView.text = message
        centerToastView.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideToastRunnable)
        mainHandler.postDelayed(hideToastRunnable, 1500)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mainHandler.postDelayed({
                volumeStatusBtn.text = "VOL ${getCurrentVolumePercent()}%"
            }, 120)
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun createOsdButton(label: String, isAccent: Boolean, onClick: () -> Unit): Button {
        val normalBg = if (isAccent) "#0D9488" else "#0B2B38"
        val normalBorder = if (isAccent) "#00D4B2" else "#134556"
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(13), dp(8), dp(13), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(7)
            }
            setOnClickListener { onClick() }
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

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        mainHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
