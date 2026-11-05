package com.samoth.dash

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var client: HaClient
    private lateinit var root: FrameLayout
    private lateinit var message: TextView
    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private var loadedUrl: String? = null
    private var pageFailed = false
    private var cornerTaps = 0
    private var lastCornerTap = 0L
    private val retry = Runnable { openDashboard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        client = HaClient(prefs)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        message = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
            visibility = View.GONE
        }
        root.addView(message, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.attributes = window.attributes.apply {
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (prefs.dashboardUrl.isBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        BatteryService.start(this)
        if (loadedUrl != prefs.dashboardUrl || web == null || prefs.refreshToken == null) openDashboard()
        else injectKiosk()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    /** 5 appuis rapides dans le coin haut gauche : ouvre les réglages. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val size = 120 * resources.displayMetrics.density
            if (ev.x < size && ev.y < size) {
                val now = System.currentTimeMillis()
                cornerTaps = if (now - lastCornerTap < 800) cornerTaps + 1 else 1
                lastCornerTap = now
                if (cornerTaps >= 5) {
                    cornerTaps = 0
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            } else cornerTaps = 0
        }
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Kiosque : le retour navigue dans HA, mais ne quitte jamais l'appli
        web?.takeIf { it.canGoBack() }?.goBack()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        web?.destroy()
        web = null
        super.onDestroy()
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
        message.bringToFront()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webView(): WebView = web ?: WebView(this).also { w ->
        w.setBackgroundColor(Color.BLACK)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            displayZoomControls = false
            builtInZoomControls = false
        }
        w.addJavascriptInterface(Bridge(), "externalApp")
        w.webViewClient = Client()
        root.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        web = w
    }

    private fun recreateWebView() {
        web?.let { root.removeView(it); it.destroy() }
        web = null
    }

    /** Point d'entrée unique : page de connexion si besoin, sinon le tableau de bord. */
    private fun openDashboard() {
        main.removeCallbacks(retry)
        val base = prefs.baseUrl ?: return
        val w = webView()
        pageFailed = false
        loadedUrl = prefs.dashboardUrl
        if (prefs.refreshToken == null) {
            val clientId = URLEncoder.encode("$base/", "UTF-8")
            val redirect = URLEncoder.encode("$base/?auth_callback=1", "UTF-8")
            w.loadUrl("$base/auth/authorize?response_type=code&client_id=$clientId&redirect_uri=$redirect&state=samothdash")
        } else {
            // external_auth=1 : HA demande ses jetons à l'appli, la session n'expire donc jamais.
            // Ce paramètre disparaît de l'adresse une fois chargée : ne jamais utiliser web.reload().
            val u = Uri.parse(prefs.dashboardUrl).buildUpon().appendQueryParameter("external_auth", "1").build()
            w.loadUrl(u.toString())
        }
    }

    private fun failAndRetry(text: String) {
        pageFailed = true
        showMessage("$text\n\nNouvel essai automatique…\n\n(5 appuis dans le coin haut gauche pour les réglages)")
        main.removeCallbacks(retry)
        main.postDelayed(retry, RETRY_MS)
    }

    private fun onLoginCode(code: String) {
        showMessage("Connexion…")
        thread {
            try {
                client.exchangeCode(code)
                runCatching { client.registerDevice() }
                main.post {
                    BatteryService.start(this)
                    message.visibility = View.GONE
                    openDashboard()
                }
            } catch (e: Exception) {
                main.post { failAndRetry(e.message ?: "Connexion impossible") }
            }
        }
    }

    private fun injectKiosk() {
        val cfg = JSONObject().put("sidebar", prefs.hideSidebar).put("header", prefs.hideHeader)
        val js = assets.open("kiosk.js").bufferedReader().use { it.readText() }.replace("__CFG__", cfg.toString())
        web?.evaluateJavascript(js, null)
    }

    private inner class Client : WebViewClient() {
        /** Renvoie true si l'adresse est prise en charge par l'appli plutôt que chargée. */
        private fun intercept(url: Uri): Boolean {
            val base = prefs.baseUrl ?: return false
            val here = url.scheme + "://" + url.encodedAuthority
            if (here == base && url.getQueryParameter("auth_callback") != null) {
                url.getQueryParameter("code")?.let { onLoginCode(it) }
                return true
            }
            // HA a rechargé sa page sans external_auth et veut une connexion : on repart du tableau de bord
            if (here == base && url.path == "/auth/authorize" && prefs.refreshToken != null) {
                main.post { openDashboard() }
                return true
            }
            // Kiosque : on reste sur Home Assistant
            return here != base
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && intercept(request.url)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageFailed = false
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!pageFailed) message.visibility = View.GONE
            injectKiosk()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) failAndRetry("Home Assistant est injoignable\n${error.description}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame && response.statusCode >= 500) {
                failAndRetry("Home Assistant ne répond pas (HTTP ${response.statusCode})")
            }
        }

        /** Le moteur web a planté : on le recrée au lieu de laisser un écran noir. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreateWebView()
            main.postDelayed(retry, 1000)
            return true
        }
    }

    /** Protocole utilisé par l'appli officielle : window.externalApp, appelé par le frontend HA. */
    private inner class Bridge {
        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            val o = runCatching { JSONObject(payload) }.getOrNull() ?: return
            val cb = o.optString("callback")
            if (!CALLBACK.matches(cb)) return
            val force = o.optBoolean("force", false)
            thread {
                try {
                    val token = client.accessToken(force)
                    val data = JSONObject().put("access_token", token).put("expires_in", client.secondsLeft())
                    main.post { web?.evaluateJavascript("window.$cb(true,$data)", null) }
                } catch (e: AuthRevoked) {
                    main.post {
                        prefs.clearAuth()
                        openDashboard()
                    }
                } catch (e: Exception) {
                    main.post {
                        web?.evaluateJavascript("window.$cb(false)", null)
                        main.removeCallbacks(retry)
                        main.postDelayed(retry, RETRY_MS)
                    }
                }
            }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            val cb = runCatching { JSONObject(payload).optString("callback") }.getOrNull() ?: return
            main.post {
                prefs.clearAuth()
                if (CALLBACK.matches(cb)) web?.evaluateJavascript("window.$cb(true)", null)
                openDashboard()
            }
        }
    }

    companion object {
        private const val RETRY_MS = 15_000L
        private val CALLBACK = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
