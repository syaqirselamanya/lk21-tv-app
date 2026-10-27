package com.lk21tv.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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

    // Elemen Kawalan Pemain (OSD)
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
    private var isPlaying = true
    private var isMuted = false
    private var currentDurationSec = 0
    private var isUserSeeking = false

    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1

    private val fitModes = listOf("contain" to "Fit (Asal)", "cover" to "Zoom Penuh", "fill" to "Regang (Fill)")
    private var fitIndex = 0

    private var pageUrl = ""
    private var baseUrl = "https://tv12.lk21official.cc"
    private var currentTitle = "Pemain Video"
    private var mirrors = mutableListOf<MirrorItem>()
    private var episodes = mutableListOf<EpisodeItem>()

    private val autoHideRunnable = Runnable {
        osdContainer.visibility = View.GONE
    }

    private val hideToastRunnable = Runnable {
        centerToastView.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        pageUrl = intent.getStringExtra("PAGE_URL") ?: ""
        val initialEmbedUrl = intent.getStringExtra("EMBED_URL") ?: ""
        currentTitle = intent.getStringExtra("TITLE") ?: "Pemain Video LK21"
        baseUrl = intent.getStringExtra("BASE_URL") ?: "https://tv12.lk21official.cc"
        val initialServer = intent.getStringExtra("SERVER_NAME") ?: "P2P Direct"

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

        // 1. Enjin Video WebView Skrin Penuh
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        configureWebView()
        root.addView(webView)

        // 2. Lapisan Loading Gaya Netflix Dark Teal
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
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                bottomMargin = dp(16)
            }
        }
        loadingStatusText = TextView(this).apply {
            text = "Menyediakan Strim Video HD..."
            setTextColor(Color.parseColor("#00D4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }
        loadingOverlay.addView(spinner)
        loadingOverlay.addView(loadingStatusText)
        root.addView(loadingOverlay)

        // 3. Toast Penunjuk Tengah (Untuk Fast Forward, Suara, Kelajuan)
        centerToastView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedBg("#D9071F29", "#00D4B2", 2, 14)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        root.addView(centerToastView)

        // 4. Antara Muka Kawalan Pemain (OSD) Gaya Netflix Dark Teal
        osdContainer = buildNetflixDarkTealOsd()
        root.addView(osdContainer)

        // Klik pada skrin untuk papar/sorok OSD
        webView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                toggleOsd()
            }
            false
        }

        setContentView(root)

        startStreamResolution(pageUrl, initialEmbedUrl, initialServer)
    }

    private fun buildNetflixDarkTealOsd(): FrameLayout {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // BAR ATAS: Kembali, Tajuk Cerita, Pilihan Episod & Server
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#E6031117"), Color.TRANSPARENT)
            )
            setPadding(dp(24), dp(18), dp(24), dp(28))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val backBtn = createOsdButton("⬅  Kembali", isAccent = false) { finish() }

        titleTextView = TextView(this).apply {
            text = currentTitle
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        episodeListBtn = createOsdButton("📺 Episod", isAccent = false) {
            showEpisodeSwitchDialog()
        }.apply {
            visibility = View.GONE
        }

        serverBadgeBtn = createOsdButton("🖥️ Server: P2P", isAccent = true) {
            showServerSwitchDialog()
        }

        topBar.addView(backBtn)
        topBar.addView(titleTextView)
        topBar.addView(episodeListBtn)
        topBar.addView(serverBadgeBtn)
        container.addView(topBar)

        // PANEL BAWAH: SeekBar, Kawalan Main/Pause/FastForward, & Setting Standard (Suara, Kelajuan, Skrin)
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    Color.parseColor("#F2031117"),
                    Color.parseColor("#CC071F29"),
                    Color.TRANSPARENT
                )
            )
            setPadding(dp(28), dp(28), dp(28), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        // Baris 1: Garis Masa (Timeline SeekBar)
        val timelineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        timeCurrentView = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#00D4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }

        seekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }

        timelineRow.addView(timeCurrentView)
        timelineRow.addView(seekBar)
        timelineRow.addView(timeTotalView)
        bottomPanel.addView(timelineRow)

        // Baris 2: Butang Kawalan Utama (Play/Pause, Fast Forward, Rewind) & Setting Standard (Suara, Kelajuan, Skrin)
        val controlsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Kawalan Masa & Main
        val rw30Btn = createOsdButton("⏮ -30s", isAccent = false) { seekRelative(-30) }
        val rw10Btn = createOsdButton("⏪ -10s", isAccent = false) { seekRelative(-10) }

        playPauseBtn = createOsdButton("⏸  Pause", isAccent = true) {
            togglePlayPause()
        }

        val ff10Btn = createOsdButton("⏩ +10s", isAccent = false) { seekRelative(10) }
        val ff30Btn = createOsdButton("⏭ +30s", isAccent = false) { seekRelative(30) }

        // Kawalan Suara (Volume Standard)
        val volDownBtn = createOsdButton("🔉 Suara -", isAccent = false) { adjustVolume(-1) }
        volumeStatusBtn = createOsdButton("🔊 ${getCurrentVolumePercent()}%", isAccent = false) {
            toggleMute()
        }
        val volUpBtn = createOsdButton("🔊 Suara +", isAccent = false) { adjustVolume(1) }

        // Kawalan Kelajuan (Speed) & Nisbah Skrin (Aspect Ratio)
        speedBtn = createOsdButton("⚡ 1.0x", isAccent = false) { cyclePlaybackSpeed() }
        aspectBtn = createOsdButton("📐 Fit (Asal)", isAccent = false) { cycleAspectRatio() }

        val hideOsdBtn = createOsdButton("✖ Sorok", isAccent = false) {
            osdContainer.visibility = View.GONE
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
        loadingStatusText.text = "Menyediakan Strim Video HD ($serverLabel)..."
        serverBadgeBtn.text = "🖥️ Server: $serverLabel"

        executor.execute {
            try {
                var embedToResolve = targetEmbedUrl

                // Jika dipanggil terus dari kad cerita (tanpa dialog), tarik senarai mirror & episod dahulu
                if (embedToResolve.isBlank() && targetPageUrl.isNotBlank()) {
                    val detail = LK21Scraper.fetchDetail(baseUrl, targetPageUrl)
                    currentTitle = detail.title
                    mirrors.clear()
                    mirrors.addAll(detail.mirrors)
                    episodes.clear()
                    episodes.addAll(detail.episodes)

                    // Jika ini Series dan belum pilih episod, ambil episod pertama secara automatik
                    if (detail.mainEmbedUrl.isNullOrBlank() && detail.episodes.isNotEmpty()) {
                        val firstEp = detail.episodes.first()
                        val epDetail = LK21Scraper.fetchDetail(baseUrl, firstEp.url)
                        currentTitle = "${detail.title} • ${firstEp.label}"
                        mirrors.clear()
                        mirrors.addAll(epDetail.mirrors)
                        embedToResolve = epDetail.mainEmbedUrl ?: epDetail.mirrors.firstOrNull()?.embedUrl ?: ""
                    } else {
                        embedToResolve = detail.mainEmbedUrl ?: detail.mirrors.firstOrNull()?.embedUrl ?: ""
                    }
                }

                val resolved = LK21Scraper.resolveStream(embedToResolve, serverLabel)

                mainHandler.post {
                    titleTextView.text = currentTitle
                    serverBadgeBtn.text = "🖥️ Server: ${resolved.serverName}"
                    episodeListBtn.visibility = if (episodes.isNotEmpty()) View.VISIBLE else View.GONE

                    if (resolved.isDirectHls) {
                        loadDirectHlsPlayer(resolved.streamUrl, resolved.referer)
                    } else {
                        loadProtectedEmbedPlayer(resolved.streamUrl, resolved.referer)
                    }
                    playPauseBtn.requestFocus()
                    rescheduleAutoHide()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loadingOverlay.visibility = View.GONE
                    showCenterToast("Gagal memuatkan server ini. Cuba tukar Server di penjuru kanan atas.")
                }
            }
        }
    }

    /**
     * Pemain Video HTML5 + Hls.js Tulen (100% Tanpa Laman Web, Sokong Kawalan Penuh Suara/Seek/Speed)
     */
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

    /**
     * Mod Iframe Terlindung untuk pelayan alternatif (CAST / HYDRAX) supaya window.self !== window.top
     * dan tidak sesekali redirect ke laman web lk21.de!
     */
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
            "Mozilla/5.0 (Linux; Android 13; SHIELD Android TV Build/RQ1A.210105.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile Safari/537.36"

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
                // Sekat sebarang cubaan redirect ke laman web lk21.de atau iklan
                if (!AdBlocker.isAllowedPlayerNavigation(url)) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(AdBlocker.CLEAN_EMBED_JS, null)
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
            }
        }

        @JavascriptInterface
        fun onPlayStateChanged(playing: Boolean) {
            mainHandler.post {
                isPlaying = playing
                playPauseBtn.text = if (playing) "⏸  Pause" else "▶  Play"
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSec: Int, durationSec: Int) {
            mainHandler.post {
                if (durationSec > 0) {
                    currentDurationSec = durationSec
                    timeTotalView.text = formatTime(durationSec)
                    if (!isUserSeeking) {
                        timeCurrentView.text = formatTime(currentSec)
                        val progress = ((currentSec.toLong() * 1000L) / durationSec).toInt()
                        seekBar.progress = progress.coerceIn(0, 1000)
                    }
                }
            }
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
            playPauseBtn.text = if (nowPlaying) "⏸  Pause" else "▶  Play"
            showCenterToast(if (nowPlaying) "▶  Main" else "⏸  Jeda (Pause)")
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
        val label = if (deltaSeconds > 0) "⏩  +${deltaSeconds}s" else "⏪  ${deltaSeconds}s"
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
        showCenterToast("⏱️  ${formatTime(targetSeconds)}")
    }

    private fun adjustVolume(direction: Int) {
        rescheduleAutoHide()
        val flag = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
        isMuted = false
        webView.evaluateJavascript("if(window.playerControl) window.playerControl.setMuted(false);", null)
        val pct = getCurrentVolumePercent()
        volumeStatusBtn.text = "🔊 $pct%"
        showCenterToast("🔊  Suara: $pct%")
    }

    private fun toggleMute() {
        rescheduleAutoHide()
        isMuted = !isMuted
        webView.evaluateJavascript("if(window.playerControl) window.playerControl.setMuted($isMuted);", null)
        if (isMuted) {
            volumeStatusBtn.text = "🔇 Mute"
            showCenterToast("🔇  Suara Disenyapkan (Mute)")
        } else {
            val pct = getCurrentVolumePercent()
            volumeStatusBtn.text = "🔊 $pct%"
            showCenterToast("🔊  Suara: $pct%")
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
        speedBtn.text = "⚡ ${rate}x"
        showCenterToast("⚡  Kelajuan Video: ${rate}x")
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
        aspectBtn.text = "📐 $label"
        showCenterToast("📐  Nisbah Skrin: $label")
    }

    private fun showServerSwitchDialog() {
        if (mirrors.isEmpty()) {
            showCenterToast("Tiada pelayan alternatif untuk cerita ini.")
            return
        }
        val names = mirrors.map { "🖥️  Server ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Tukar Pelayan Video (Server)")
            .setItems(names) { _, which ->
                val chosen = mirrors[which]
                startStreamResolution(pageUrl, chosen.embedUrl, chosen.name)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEpisodeSwitchDialog() {
        if (episodes.isEmpty()) return
        val names = episodes.map { "▶  ${it.label}" }.toTypedArray()
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Pilih Episod")
            .setItems(names) { _, which ->
                val ep = episodes[which]
                pageUrl = ep.url
                currentTitle = "${currentTitle.substringBefore(" • ")} • ${ep.label}"
                startStreamResolution(ep.url, "", "P2P Direct")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toggleOsd() {
        if (osdContainer.visibility == View.VISIBLE) {
            osdContainer.visibility = View.GONE
            mainHandler.removeCallbacks(autoHideRunnable)
        } else {
            osdContainer.visibility = View.VISIBLE
            playPauseBtn.requestFocus()
            rescheduleAutoHide()
        }
    }

    private fun rescheduleAutoHide() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, 6000)
    }

    private fun showCenterToast(message: String) {
        centerToastView.text = message
        centerToastView.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideToastRunnable)
        mainHandler.postDelayed(hideToastRunnable, 1800)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val osdVisible = osdContainer.visibility == View.VISIBLE

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekRelative(10)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekRelative(-10)
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                toggleOsd()
                return true
            }
        }

        // Apabila OSD sedang tersembunyi, anak panah Remote TV terus mengawal Fast Forward, Rewind & Play/Pause!
        if (!osdVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    seekRelative(10)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    seekRelative(-10)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    adjustVolume(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    adjustVolume(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    osdContainer.visibility = View.VISIBLE
                    playPauseBtn.requestFocus()
                    rescheduleAutoHide()
                    return true
                }
            }
        } else {
            rescheduleAutoHide()
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                osdContainer.visibility = View.GONE
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun createOsdButton(label: String, isAccent: Boolean, onClick: () -> Unit): Button {
        val normalBg = if (isAccent) "#0D9488" else "#0B2B38"
        val normalBorder = if (isAccent) "#00D4B2" else "#134556"
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, 1, 8)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor("#031117"))
                    v.background = createRoundedBg("#00D4B2", "#FFFFFF", 2, 8)
                    v.scaleX = 1.06f
                    v.scaleY = 1.06f
                } else {
                    (v as Button).setTextColor(Color.parseColor("#F0FDFA"))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 8)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
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

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
