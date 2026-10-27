package com.lk21tv.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class PlayerActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var topOverlayBar: LinearLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.getStringExtra("TARGET_URL") ?: "https://tv12.lk21official.cc"
        val title = intent.getStringExtra("TITLE") ?: "Pemain TV"
        val baseUrl = intent.getStringExtra("BASE_URL") ?: "https://tv12.lk21official.cc"
        val isDirectWeb = intent.getBooleanExtra("IS_DIRECT_WEB", false)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Bar Kawalan Atas (Boleh disembunyikan semasa menonton)
        topOverlayBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC0F172A"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val backBtn = createOverlayButton("⬅️ Kembali") { finish() }
        val reloadBtn = createOverlayButton("🔄 Muat Semula") { webView.reload() }
        val focusPlayerBtn = createOverlayButton("📺 Fokus Video Penuh") {
            isolateVideoPlayerJs()
            topOverlayBar.visibility = View.GONE
        }
        val hideBarBtn = createOverlayButton("✖️ Sorok Bar") {
            topOverlayBar.visibility = View.GONE
        }

        val titleView = TextView(this).apply {
            text = "$title (AdBlock Aktif 🛡️)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        topOverlayBar.addView(backBtn)
        topOverlayBar.addView(reloadBtn)
        topOverlayBar.addView(focusPlayerBtn)
        topOverlayBar.addView(titleView)
        topOverlayBar.addView(hideBarBtn)

        configureWebView(baseUrl, isDirectWeb)

        root.addView(webView)
        root.addView(fullscreenContainer)
        root.addView(topOverlayBar)

        setContentView(root)

        val headers = mapOf("Referer" to baseUrl)
        webView.loadUrl(targetUrl, headers)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(baseUrl: String, isDirectWeb: Boolean) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true) // Tangkap & matikan semua cubaan pop-up
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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
                // Sekat redirect keluar ke laman iklan / judi
                if (!AdBlocker.isAllowedNavigation(url, baseUrl)) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Suntik CSS & JS AdBlocker
                view?.evaluateJavascript(AdBlocker.CLEAN_PAGE_JS, null)
                if (!isDirectWeb) {
                    isolateVideoPlayerJs()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Sekat 100% semua tetingkap pop-up baru
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return false
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                topOverlayBar.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                fullscreenContainer.removeView(view)
                fullscreenContainer.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
            }
        }
    }

    private fun isolateVideoPlayerJs() {
        val script = """
            (function() {
                ${AdBlocker.CLEAN_PAGE_JS}
                var player = document.querySelector('#player-iframe, .player-embed iframe, #loadProviders iframe, iframe[src*="player"], iframe[src*="embed"], video');
                if (player) {
                    player.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            topOverlayBar.visibility =
                if (topOverlayBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.webChromeClient?.onHideCustomView()
                return true
            }
            if (topOverlayBar.visibility == View.GONE) {
                topOverlayBar.visibility = View.VISIBLE
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun createOverlayButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.parseColor("#E11D48"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
            setOnClickListener { onClick() }
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
        webView.destroy()
        super.onDestroy()
    }
}
