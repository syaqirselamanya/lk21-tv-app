package com.citeraloka.phone

import android.annotation.SuppressLint
import android.annotation.TargetApi
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
import com.citeraloka.phone.domains.DomainManager
import com.citeraloka.phone.domains.lk21.Lk21Domain
import com.citeraloka.phone.domains.watch32.Watch32Domain
import java.util.concurrent.Executors

class PlayerActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var webView: WebView
    private lateinit var osdContainer: FrameLayout
    private lateinit var showTopBarPillBtn: Button
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
    private var isStreamReady = false
    private var fallbackBaseSec = 0
    private var sessionWatchedSec = 0

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
        showTopBarPillBtn.visibility = View.VISIBLE
        enterImmersiveFullscreen()
    }

    private val hideToastRunnable = Runnable {
        centerToastView.visibility = View.GONE
    }

    private val sessionProgressTicker = object : Runnable {
        override fun run() {
            if (isStreamReady && isPlaying) {
                sessionWatchedSec++
                if (currentDurationSec <= 0) {
                    val estSec = fallbackBaseSec + sessionWatchedSec
                    if (!isUserSeeking) {
                        timeCurrentView.text = formatTime(estSec)
                    }
                    if (sessionWatchedSec >= 3 && sessionWatchedSec % 5 == 0) {
                        saveCurrentProgress()
                    }
                }
            }
            mainHandler.postDelayed(this, 1000L)
        }
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
        fallbackBaseSec = resumeSec

        val defaultFitPref = getSharedPreferences("citeraloka_phone_prefs", Context.MODE_PRIVATE)
            .getString("default_aspect_mode", "contain") ?: "contain"
        fitIndex = fitModes.indexOfFirst { it.first == defaultFitPref }.takeIf { it >= 0 } ?: 0

        val mirrorNames = intent.getStringArrayListExtra("MIRROR_NAMES") ?: arrayListOf()
        val mirrorUrls = intent.getStringArrayListExtra("MIRROR_URLS") ?: arrayListOf()
        for (i in 0 until minOf(mirrorNames.size, mirrorUrls.size)) {
            mirrors.add(MirrorItem(mirrorNames[i], mirrorUrls[i]))
        }

        val epLabels = intent.getStringArrayListExtra("EPISODE_LABELS") ?: arrayListOf()
        val epUrls = intent.getStringArrayListExtra("EPISODE_URLS") ?: arrayListOf()
        for (i in 0 until minOf(epLabels.size, epUrls.size)) {
            episodes.add(EpisodeItem(label = epLabels[i], url = epUrls[i]))
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
            text = "LOADING HD STREAM..."
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

        // 4. Antara Muka Bar Atas Minimalis (Kawalan Bawah & Scrolling Timer menggunakan Domain Player secara terus)
        osdContainer = buildTouchMinimalistOsd()
        root.addView(osdContainer)

        showTopBarPillBtn = Button(this).apply {
            text = "MENU • SERVER"
            isAllCaps = true
            setTextColor(Color.parseColor("#CC99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg("#99031117", "#6600D4B2", 1, 20)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = dp(16)
                topMargin = dp(14)
            }
            setOnClickListener {
                toggleOsd()
            }
        }
        root.addView(showTopBarPillBtn)

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
        // Hanya Bar Atas Minimalis (WRAP_CONTENT di atas) supaya Kawalan Asal Domain Player & Scrolling Timer di bawah berfungsi 100% secara terus!
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        // BAR ATAS: BACK, Tajuk Cerita, EPISODES, SERVER, ASPECT, HIDE BAR
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

        val backBtn = createOsdButton("BACK", isAccent = false) { finish() }

        titleTextView = TextView(this).apply {
            text = currentTitle.uppercase()
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.03f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        episodeListBtn = createOsdButton("EPISODES", isAccent = false) {
            showEpisodeSwitchDialog()
        }.apply {
            visibility = View.GONE
        }

        serverBadgeBtn = createOsdButton("SERVER: P2P", isAccent = true) {
            showServerSwitchDialog()
        }

        aspectBtn = createOsdButton(fitModes[fitIndex].second, isAccent = false) {
            cycleAspectRatio()
        }

        val hideTopBarBtn = createOsdButton("HIDE BAR", isAccent = false) {
            osdContainer.visibility = View.GONE
            showTopBarPillBtn.visibility = View.VISIBLE
            enterImmersiveFullscreen()
        }

        topBar.addView(backBtn)
        topBar.addView(titleTextView)
        topBar.addView(episodeListBtn)
        topBar.addView(serverBadgeBtn)
        topBar.addView(aspectBtn)
        topBar.addView(hideTopBarBtn)
        container.addView(topBar)

        // Elemen dalaman disimpan dalam keadaan tersembunyi (GONE) supaya keseluruhan kawalan bawah menggunakan Domain Player secara terus
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
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

        playPauseBtn = createOsdButton("PAUSE", isAccent = true) { togglePlayPause() }
        volumeStatusBtn = createOsdButton("VOL ${getCurrentVolumePercent()}%", isAccent = false) { toggleMute() }
        speedBtn = createOsdButton("1.0x", isAccent = false) { cyclePlaybackSpeed() }

        bottomPanel.addView(playPauseBtn)
        bottomPanel.addView(volumeStatusBtn)
        bottomPanel.addView(speedBtn)
        container.addView(bottomPanel)

        return container
    }

    private fun startStreamResolution(targetPageUrl: String, targetEmbedUrl: String, serverLabel: String) {
        loadingOverlay.visibility = View.VISIBLE
        loadingStatusText.text = "LOADING ($serverLabel)..."
        serverBadgeBtn.text = "SERVER: $serverLabel"

        executor.execute {
            try {
                var embedToResolve = targetEmbedUrl
                var effectiveLabel = serverLabel

                if (embedToResolve.isBlank() && targetPageUrl.isNotBlank()) {
                    if (Watch32Domain.isWatch32Uri(targetPageUrl)) {
                        val detail = Watch32Domain.fetchDetail(targetPageUrl)
                            ?: throw IllegalStateException("Watch32 detail unavailable")
                        currentTitle = detail.title
                        if (posterUrl.isBlank()) posterUrl = detail.posterUrl
                        if (currentGenre.isBlank()) currentGenre = detail.genre
                        if (mirrors.isEmpty()) {
                            mirrors.addAll(detail.mirrors)
                        }
                        if (episodes.isEmpty()) {
                            episodes.addAll(detail.episodes)
                        }

                        if (detail.mainEmbedUrl.isNullOrBlank() && detail.episodes.isNotEmpty()) {
                            val firstEp = detail.episodes.first()
                            val epDetail = Watch32Domain.fetchDetail(firstEp.url)
                                ?: throw IllegalStateException("Episode unavailable")
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
                        val lkDetail = Lk21Domain.fetchDetail(baseUrl, targetPageUrl, enrichWithTmdb = false)
                        if (lkDetail != null) {
                            currentTitle = lkDetail.title
                            if (posterUrl.isBlank()) posterUrl = lkDetail.posterUrl
                            if (currentGenre.isBlank()) currentGenre = lkDetail.genre
                            if (mirrors.isEmpty()) {
                                mirrors.addAll(lkDetail.mirrors)
                            }
                            if (episodes.isEmpty()) {
                                episodes.addAll(lkDetail.episodes)
                            }

                            if (lkDetail.mainEmbedUrl.isNullOrBlank() && lkDetail.episodes.isNotEmpty()) {
                                val firstEp = lkDetail.episodes.first()
                                val epDetail = Lk21Domain.fetchDetail(baseUrl, firstEp.url, enrichWithTmdb = false)
                                if (epDetail != null) {
                                    currentTitle = "${lkDetail.title} • ${firstEp.label}"
                                    currentEpisodeLabel = firstEp.label
                                    mirrors.clear()
                                    mirrors.addAll(epDetail.mirrors)
                                    embedToResolve = epDetail.mainEmbedUrl ?: epDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                                }
                            } else {
                                embedToResolve = lkDetail.mainEmbedUrl ?: lkDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                            }

                            if (DomainManager.isWatch32Enabled(this@PlayerActivity)) {
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
                        }

                        if (embedToResolve.isBlank() && DomainManager.isWatch32Enabled(this@PlayerActivity)) {
                            val fbDetail = Watch32Domain.findMatchingDetail(
                                rawTitle = currentTitle,
                                episodeLabel = currentEpisodeLabel,
                                preferSeries = currentEpisodeLabel.isNotBlank()
                            )
                            if (fbDetail != null) {
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
                    throw IllegalStateException("No active stream server found")
                }

                currentEmbedUrl = embedToResolve
                currentServerName = effectiveLabel
                val resolved = DomainManager.resolveStream(embedToResolve, effectiveLabel)

                UserRecommendationManager.recordView(this@PlayerActivity, currentGenre, targetPageUrl, weightBoost = 3)

                mainHandler.post {
                    titleTextView.text = currentTitle.uppercase()
                    serverBadgeBtn.text = "SERVER: ${resolved.serverName}"
                    episodeListBtn.visibility = if (episodes.isNotEmpty()) View.VISIBLE else View.GONE

                    when {
                        resolved.isDirectHls -> loadDirectHlsPlayer(resolved.streamUrl, resolved.referer)
                        resolved.isDirectWebEmbed -> loadDirectWebEmbedPlayer(resolved.streamUrl, resolved.referer)
                        else -> loadProtectedEmbedPlayer(resolved.streamUrl, resolved.referer)
                    }
                    if (sessionWatchedSec < 5) sessionWatchedSec = 5
                    saveCurrentProgress()
                    mainHandler.postDelayed({
                        loadingOverlay.visibility = View.GONE
                        if (!isStreamReady) {
                            isStreamReady = true
                            mainHandler.removeCallbacks(sessionProgressTicker)
                            mainHandler.postDelayed(sessionProgressTicker, 1000L)
                        }
                    }, 3000L)
                    rescheduleAutoHide()
                }
            } catch (_: Exception) {
                mainHandler.post {
                    val currentIndex = mirrors.indexOfFirst { it.embedUrl == currentEmbedUrl }
                    if (currentIndex in 0 until mirrors.size - 1) {
                        val nextMirror = mirrors[currentIndex + 1]
                        showCenterToast("FAILOVER TO ${nextMirror.name}...")
                        startStreamResolution(pageUrl, nextMirror.embedUrl, nextMirror.name)
                    } else {
                        loadingOverlay.visibility = View.GONE
                        showCenterToast("STREAM ERROR. PLEASE SWITCH SERVER.")
                    }
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
                <video id="tv-video" controls autoplay playsinline></video>
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
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background: #000; overflow: hidden; }
                    html, body, iframe { width: 100vw; height: 100vh; border: 0; display: block; }
                </style>
            </head>
            <body>
                <iframe id="embed-frame" src="$embedUrl" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="autoplay; fullscreen; picture-in-picture; encrypted-media; clipboard-write; gyroscope; accelerometer"></iframe>
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
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/125.0.0.0 Mobile Safari/537.36"

        webView.addJavascriptInterface(PlayerJsBridge(), "AndroidPlayer")

        webView.webViewClient = object : WebViewClient() {
            @TargetApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                mainHandler.post {
                    val currentIndex = mirrors.indexOfFirst { it.embedUrl == currentEmbedUrl }
                    if (currentIndex in 0 until mirrors.size - 1) {
                        val nextMirror = mirrors[currentIndex + 1]
                        showCenterToast("SWITCHING TO ${nextMirror.name}...")
                        startStreamResolution(pageUrl, nextMirror.embedUrl, nextMirror.name)
                    } else {
                        showCenterToast("RECOVERING PLAYER...")
                    }
                }
                return true
            }

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
                val isForMainFrame = request.isForMainFrame
                // Allow sub-frame navigation inside the video player iframe
                if (!isForMainFrame) {
                    return false
                }
                if (!AdBlocker.isAllowedPlayerNavigation(url)) {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(UNIVERSAL_PLAYER_BRIDGE_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(AdBlocker.CLEAN_EMBED_JS, null)
                view?.evaluateJavascript(UNIVERSAL_PLAYER_BRIDGE_JS, null)
                val defaultCssFit = fitModes[fitIndex].first
                view?.evaluateJavascript(
                    "if(window.playerControl) window.playerControl.setFit('$defaultCssFit');",
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
                if (!isStreamReady) {
                    isStreamReady = true
                    mainHandler.removeCallbacks(sessionProgressTicker)
                    mainHandler.postDelayed(sessionProgressTicker, 1000L)
                }
                if (resumeSec > 0) {
                    seekToSeconds(resumeSec)
                    showCenterToast("RESUMED AT ${formatTime(resumeSec)}")
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
        val secToSave = if (currentPositionSec > 0) currentPositionSec else (fallbackBaseSec + sessionWatchedSec)
        val durToSave = when {
            currentDurationSec > 0 -> currentDurationSec
            currentEpisodeLabel.isNotBlank() || episodes.isNotEmpty() -> 2700
            else -> 6600
        }
        if (pageUrl.isNotBlank() && secToSave >= 2 && durToSave > 0) {
            ContinueWatchingManager.saveProgress(
                context = this,
                pageUrl = pageUrl,
                embedUrl = currentEmbedUrl,
                title = currentTitle,
                posterUrl = posterUrl,
                currentSec = secToSave,
                durationSec = durToSave,
                episodeLabel = currentEpisodeLabel,
                serverName = currentServerName,
                genre = currentGenre
            )
        }
    }

    private fun togglePlayPause() {
        rescheduleAutoHide()
        val optimisticNext = !isPlaying
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl && window.playerControl.togglePlay) {
                    return window.playerControl.togglePlay();
                }
                var v = window.__clActiveVideo || document.querySelector('video');
                if (v) {
                    if (v.paused) { v.play().catch(function(){}); return true; }
                    else { v.pause(); return false; }
                }
                return $optimisticNext;
            })();
            """.trimIndent()
        ) { res ->
            val nowPlaying = when {
                res?.contains("true") == true -> true
                res?.contains("false") == true -> false
                else -> optimisticNext
            }
            isPlaying = nowPlaying
            playPauseBtn.text = if (nowPlaying) "PAUSE" else "PLAY"
            showCenterToast(if (nowPlaying) "PLAY" else "PAUSE")
        }
    }

    private fun seekRelative(deltaSeconds: Int) {
        rescheduleAutoHide()
        val basePos = if (currentPositionSec > 0) currentPositionSec else (fallbackBaseSec + sessionWatchedSec)
        val effDur = if (currentDurationSec > 0) currentDurationSec else 0
        val newPos = if (effDur > 0) {
            (basePos + deltaSeconds).coerceIn(0, effDur)
        } else {
            (basePos + deltaSeconds).coerceAtLeast(0)
        }
        currentPositionSec = newPos
        fallbackBaseSec = newPos
        sessionWatchedSec = 0
        timeCurrentView.text = formatTime(newPos)
        if (effDur > 0) {
            seekBar.progress = ((newPos.toLong() * 1000L) / effDur).toInt().coerceIn(0, 1000)
        }
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl && window.playerControl.seekBy) {
                    window.playerControl.seekBy($deltaSeconds);
                    return;
                }
                var v = window.__clActiveVideo || document.querySelector('video');
                if (v) v.currentTime = Math.max(0, (v.currentTime || 0) + $deltaSeconds);
            })();
            """.trimIndent(),
            null
        )
        val label = if (deltaSeconds > 0) "+${deltaSeconds}s  •  ${formatTime(newPos)}" else "${deltaSeconds}s  •  ${formatTime(newPos)}"
        showCenterToast(label)
    }

    private fun seekToSeconds(targetSeconds: Int) {
        rescheduleAutoHide()
        val safeTarget = targetSeconds.coerceAtLeast(0)
        currentPositionSec = safeTarget
        fallbackBaseSec = safeTarget
        sessionWatchedSec = 0
        timeCurrentView.text = formatTime(safeTarget)
        if (currentDurationSec > 0) {
            seekBar.progress = ((safeTarget.toLong() * 1000L) / currentDurationSec).toInt().coerceIn(0, 1000)
        }
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl && window.playerControl.seekTo) {
                    window.playerControl.seekTo($safeTarget);
                    return;
                }
                var v = window.__clActiveVideo || document.querySelector('video');
                if (v) v.currentTime = $safeTarget;
            })();
            """.trimIndent(),
            null
        )
        showCenterToast("SEEK  •  ${formatTime(safeTarget)}")
    }

    private fun seekToRatio(ratio: Double) {
        rescheduleAutoHide()
        val clamped = ratio.coerceIn(0.0, 1.0)
        webView.evaluateJavascript(
            """
            (function() {
                if (window.playerControl && window.playerControl.seekToRatio) {
                    return window.playerControl.seekToRatio($clamped);
                }
                var v = window.__clActiveVideo || document.querySelector('video');
                if (v && v.duration > 0) {
                    v.currentTime = Math.max(0, v.duration * $clamped);
                    return Math.floor(v.currentTime);
                }
                return -1;
            })();
            """.trimIndent()
        ) { res ->
            val sec = res?.trim()?.toIntOrNull() ?: -1
            if (sec >= 0) {
                currentPositionSec = sec
                timeCurrentView.text = formatTime(sec)
                showCenterToast("SEEK  •  ${formatTime(sec)}")
            }
        }
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
        showCenterToast("ASPECT: $label")
    }

    private fun showServerSwitchDialog() {
        if (mirrors.isEmpty()) {
            showCenterToast("NO ALTERNATE SERVERS")
            return
        }
        val names = mirrors.map { "SERVER: ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("SWITCH SERVER")
            .setItems(names) { _, which ->
                val chosen = mirrors[which]
                startStreamResolution(pageUrl, chosen.embedUrl, chosen.name)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showEpisodeSwitchDialog() {
        if (episodes.isEmpty()) return
        val names = episodes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("SELECT EPISODE")
            .setItems(names) { _, which ->
                saveCurrentProgress()
                val ep = episodes[which]
                pageUrl = ep.url
                currentEpisodeLabel = ep.label
                currentTitle = "${currentTitle.substringBefore(" • ")} • ${ep.label}"
                currentDurationSec = 0
                currentPositionSec = 0
                fallbackBaseSec = 0
                sessionWatchedSec = 0
                lastSavedSec = 0
                isStreamReady = false
                val defaultLabel = if (Watch32Domain.isWatch32Uri(ep.url)) {
                    "W32 • Cinezo (No Ads)"
                } else {
                    "P2P Direct"
                }
                startStreamResolution(ep.url, "", defaultLabel)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun toggleOsd() {
        if (osdContainer.visibility == View.VISIBLE) {
            osdContainer.visibility = View.GONE
            showTopBarPillBtn.visibility = View.VISIBLE
            mainHandler.removeCallbacks(autoHideRunnable)
            enterImmersiveFullscreen()
        } else {
            osdContainer.visibility = View.VISIBLE
            showTopBarPillBtn.visibility = View.GONE
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
        val normalBg = if (isAccent) "#2400D4B2" else "#1F0B2B38"
        val normalBorder = if (isAccent) "#00D4B2" else "#00000000"
        val normalStroke = if (isAccent) 1 else 0
        return Button(this).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor(if (isAccent) "#F0FDFA" else "#99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = if (isAccent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            letterSpacing = 0.04f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            background = createRoundedBg(normalBg, normalBorder, normalStroke, 24)
            setPadding(dp(14), dp(8), dp(14), dp(8))
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

    companion object {
        private val UNIVERSAL_PLAYER_BRIDGE_JS = """
            (function() {
                if (!window.__clMediaHooked) {
                    window.__clMediaHooked = true;
                    try {
                        var origPlay = HTMLMediaElement.prototype.play;
                        HTMLMediaElement.prototype.play = function() {
                            if (this.tagName === 'VIDEO') {
                                window.__clActiveVideo = this;
                                if (window.__clBindVideoElement) window.__clBindVideoElement(this);
                            }
                            return origPlay.apply(this, arguments);
                        };
                        var origPause = HTMLMediaElement.prototype.pause;
                        HTMLMediaElement.prototype.pause = function() {
                            if (this.tagName === 'VIDEO') {
                                window.__clActiveVideo = this;
                                if (window.__clBindVideoElement) window.__clBindVideoElement(this);
                            }
                            return origPause.apply(this, arguments);
                        };
                    } catch (e) {}
                }

                function findActiveVideo() {
                    if (window.__clActiveVideo && window.__clActiveVideo.readyState >= 0) {
                        return window.__clActiveVideo;
                    }
                    var direct = document.querySelector('video');
                    if (direct) {
                        window.__clActiveVideo = direct;
                        return direct;
                    }
                    var allNodes = document.querySelectorAll('*');
                    for (var i = 0; i < allNodes.length; i++) {
                        var sr = allNodes[i].shadowRoot;
                        if (sr) {
                            var sv = sr.querySelector('video');
                            if (sv) {
                                window.__clActiveVideo = sv;
                                return sv;
                            }
                        }
                    }
                    var frames = document.querySelectorAll('iframe');
                    for (var j = 0; j < frames.length; j++) {
                        try {
                            var doc = frames[j].contentDocument || (frames[j].contentWindow && frames[j].contentWindow.document);
                            if (doc) {
                                var fv = doc.querySelector('video');
                                if (fv) {
                                    window.__clActiveVideo = fv;
                                    return fv;
                                }
                            }
                        } catch (err) {}
                    }
                    return null;
                }

                function getEffectiveDuration(v) {
                    if (!v) return 0;
                    var d = v.duration;
                    if (d && isFinite(d) && d > 0) return d;
                    try {
                        if (v.seekable && v.seekable.length > 0) {
                            var end = v.seekable.end(v.seekable.length - 1);
                            if (end && isFinite(end) && end > 0) return end;
                        }
                    } catch (e) {}
                    return 0;
                }

                window.__clBindVideoElement = function(v) {
                    if (!v || v.__clBound) return;
                    v.__clBound = true;
                    window.__clActiveVideo = v;
                    try {
                        v.setAttribute('playsinline', 'true');
                        v.style.width = '100vw';
                        v.style.height = '100vh';
                        if (!v.style.objectFit) v.style.objectFit = 'contain';
                    } catch (e) {}
                    if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                    v.addEventListener('loadedmetadata', function() {
                        if (window.AndroidPlayer) {
                            window.AndroidPlayer.onVideoReady();
                            var dur = getEffectiveDuration(v);
                            if (dur > 0) {
                                window.AndroidPlayer.onTimeUpdate(Math.floor(v.currentTime || 0), Math.floor(dur));
                            }
                        }
                    });
                    v.addEventListener('playing', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(true);
                    });
                    v.addEventListener('play', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(true);
                    });
                    v.addEventListener('pause', function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(false);
                    });
                    v.addEventListener('timeupdate', function() {
                        var dur = getEffectiveDuration(v);
                        if (window.AndroidPlayer && dur > 0) {
                            window.AndroidPlayer.onTimeUpdate(
                                Math.floor(v.currentTime || 0),
                                Math.floor(dur)
                            );
                        }
                    });
                };

                window.playerControl = {
                    togglePlay: function() {
                        var v = findActiveVideo();
                        if (v) {
                            window.__clBindVideoElement(v);
                            if (v.paused) {
                                v.play().catch(function(){});
                                if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(true);
                                return true;
                            } else {
                                v.pause();
                                if (window.AndroidPlayer) window.AndroidPlayer.onPlayStateChanged(false);
                                return false;
                            }
                        }
                        var playBtn = document.querySelector('.jw-icon-playback, .vjs-play-control, .plyr__control[data-plyr="play"], button[aria-label*="Play"], button[aria-label*="Pause"]');
                        if (playBtn) {
                            playBtn.click();
                        }
                        return true;
                    },
                    seekBy: function(deltaSec) {
                        var v = findActiveVideo();
                        if (v) {
                            window.__clBindVideoElement(v);
                            var dur = getEffectiveDuration(v);
                            var target = Math.max(0, (v.currentTime || 0) + deltaSec);
                            if (dur > 0) target = Math.min(dur, target);
                            v.currentTime = target;
                            if (window.AndroidPlayer && dur > 0) {
                                window.AndroidPlayer.onTimeUpdate(Math.floor(target), Math.floor(dur));
                            }
                            return Math.floor(target);
                        }
                        return -1;
                    },
                    seekTo: function(targetSec) {
                        var v = findActiveVideo();
                        if (v) {
                            window.__clBindVideoElement(v);
                            var dur = getEffectiveDuration(v);
                            var target = Math.max(0, targetSec);
                            if (dur > 0) target = Math.min(dur, target);
                            v.currentTime = target;
                            if (window.AndroidPlayer && dur > 0) {
                                window.AndroidPlayer.onTimeUpdate(Math.floor(target), Math.floor(dur));
                            }
                            return Math.floor(target);
                        }
                        return -1;
                    },
                    seekToRatio: function(ratio) {
                        var v = findActiveVideo();
                        if (v) {
                            window.__clBindVideoElement(v);
                            var dur = getEffectiveDuration(v);
                            if (dur > 0) {
                                var target = Math.max(0, Math.min(dur, dur * ratio));
                                v.currentTime = target;
                                if (window.AndroidPlayer) {
                                    window.AndroidPlayer.onTimeUpdate(Math.floor(target), Math.floor(dur));
                                }
                                return Math.floor(target);
                            }
                        }
                        return -1;
                    },
                    setSpeed: function(rate) {
                        var v = findActiveVideo();
                        if (v) v.playbackRate = rate;
                    },
                    setFit: function(mode) {
                        var v = findActiveVideo();
                        if (v) v.style.objectFit = mode;
                    },
                    setMuted: function(mute) {
                        var v = findActiveVideo();
                        if (v) v.muted = mute;
                    }
                };

                if (!window.__citeralokaBridgeActive) {
                    window.__citeralokaBridgeActive = true;
                    setInterval(function() {
                        var v = findActiveVideo();
                        if (v) {
                            window.__clBindVideoElement(v);
                            var dur = getEffectiveDuration(v);
                            if (window.AndroidPlayer && dur > 0) {
                                window.AndroidPlayer.onTimeUpdate(
                                    Math.floor(v.currentTime || 0),
                                    Math.floor(dur)
                                );
                            }
                        }
                    }, 750);
                    setTimeout(function() {
                        if (window.AndroidPlayer) window.AndroidPlayer.onVideoReady();
                    }, 2200);
                }
            })();
        """.trimIndent()
    }
}
