package pk.cinejoy.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

class MainActivity : Activity() {

    // Siteyi değiştirmek istersen sadece bu iki satır
    private val home = "https://cinejoy.pk"
    private val homeHost = "cinejoy.pk"

    // Reklam / popup sunucuları (sadece bunlar engellenir, geri kalan her şey normal yüklenir)
    private val blocked = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google",
        "adsterra", "popads", "popcash", "propellerads", "exoclick", "juicyads", "trafficjunky",
        "onclickads", "adnxs.com", "taboola", "outbrain", "mgid.com", "clickadu", "hilltopads",
        "adcash", "profitablecpmrate", "highperformanceformat", "effectivegatecpm",
        "revenuecpmgate", "a-ads.com", "adsco.re", "popunder", "histats.com", "ad-maven",
        "admaven", "monetag", "richads", "yllix", "bidvertiser", "adspyglass"
    )

    private val hideAdsCss =
        "ins.adsbygoogle,[id^='ad-'],[class^='ad-'],[class*='popup-ad'],[id*='google_ads']{display:none!important}"

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)
        setupWebView()
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(home)
    }

    private fun setupWebView() {
        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.requestFocus()
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)   // popup'ları yakalayıp engellemek için
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val host = req.url.host ?: return null
                if (blocked.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            // Sadece cinejoy.pk içinde gezinmeye izin ver; reklam yönlendirmeleri engellenir
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val host = req.url.host ?: return true
                val scheme = req.url.scheme ?: ""
                if (scheme != "http" && scheme != "https") return true
                return !(host == homeHost || host.endsWith(".$homeHost"))
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(
                    "(function(){var s=document.createElement('style');s.innerHTML=\"$hideAdsCss\";document.head.appendChild(s);})()",
                    null
                )
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) {
                    view.loadDataWithBaseURL(
                        null,
                        "<body style='background:#14141c;color:#fff;font-family:sans-serif;text-align:center;padding-top:20%'>" +
                            "<h2>Bağlantı yok</h2><p>İnterneti kontrol edip tekrar deneyin.</p>" +
                            "<a href='$home' style='color:#e50914;font-size:1.4em'>Yeniden dene</a></body>",
                        "text/html", "utf-8", null
                    )
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // Popup pencereler tamamen engellenir
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message?) = false

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view
                customCb = cb
                web.visibility = View.GONE
                root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hideSystemBars(true)
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customCb?.onCustomViewHidden()
                customCb = null
                web.visibility = View.VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hideSystemBars(false)
                web.requestFocus()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars(hide: Boolean) {
        window.decorView.systemUiVisibility = if (hide)
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        else View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun js(code: String) = web.evaluateJavascript("(function(){$code})()", null)

    // Kumanda tuşları (Next / genel Android TV kumandası)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (customView != null) { web.webChromeClient?.onHideCustomView(); return true }
                if (web.canGoBack()) { web.goBack(); return true }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                js("var v=document.querySelector('video');if(v){v.paused?v.play():v.pause();}")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                { js("var v=document.querySelector('video');if(v)v.currentTime+=10;"); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND ->
                { js("var v=document.querySelector('video');if(v)v.currentTime-=10;"); return true }
            KeyEvent.KEYCODE_MENU -> { web.reload(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }
    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { root.removeAllViews(); web.destroy(); super.onDestroy() }
}
