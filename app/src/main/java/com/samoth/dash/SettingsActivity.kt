package com.samoth.dash

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/** Réglages, aux couleurs de Samoth Home : noir, rouge, tuiles de verre. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var url: EditText
    private lateinit var name: EditText
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var infoText: TextView
    private lateinit var updateTile: View
    private lateinit var updateLabel: TextView
    private lateinit var motionToggle: View
    private var update: UpdateInfo? = null
    private var updating = false
    private val main = Handler(Looper.getMainLooper())

    private val title by lazy { font("unbounded", 900, Typeface.DEFAULT_BOLD) }
    private val regular by lazy { font("inter", 400, Typeface.DEFAULT) }
    private val medium by lazy { font("inter", 600, Typeface.DEFAULT_BOLD) }

    private val refresh = object : Runnable {
        override fun run() {
            val connected = prefs.refreshToken != null
            val failed = prefs.lastSend.startsWith("Échec")
            statusText.text = if (connected) "Home Assistant connecté" else "Non connecté"
            statusDot.background = round(if (connected && !failed) GREEN else RED, 99)
            infoText.text = "Dernier envoi : ${prefs.lastSend.ifBlank { "aucun" }}   ·   Version ${BuildConfig.VERSION_NAME}"
            main.postDelayed(this, 2000)
        }
    }

    private fun font(file: String, weight: Int, fallback: Typeface): Typeface = runCatching {
        Typeface.Builder(assets, "fonts/$file.ttf")
            .setFontVariationSettings("'wght' $weight")
            .build() ?: fallback
    }.getOrDefault(fallback)

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radius: Int, line: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.dp().toFloat()
        if (line != null) setStroke(1.dp(), line)
    }

    /** Fond de verre, avec un retour visuel au toucher. */
    private fun glass(radius: Int = 16, clickable: Boolean = false, color: Int = GLASS, line: Int = GLASS_LINE) =
        if (clickable) RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(color, radius, line), null)
        else round(color, radius, line)

    private fun text(s: String, size: Float, color: Int = TEXT, face: Typeface = regular) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = face
    }

    private fun label(s: String) = text(s, 13f, MUTED, medium).apply { setPadding(0, 14.dp(), 0, 6.dp()) }

    private fun field(value: String, hintText: String = "") = EditText(this).apply {
        setText(value)
        hint = hintText
        textSize = 15f
        typeface = regular
        setTextColor(TEXT)
        setHintTextColor(DIM)
        isSingleLine = true
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        background = round(FIELD, 12, GLASS_LINE)
        setOnFocusChangeListener { v, focus ->
            v.background = round(FIELD, 12, if (focus) RED else GLASS_LINE)
        }
    }

    private fun toggle(s: String, value: Boolean, set: (Boolean) -> Unit): View {
        val sw = Switch(this).apply {
            isChecked = value
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(RED, Color.rgb(190, 190, 198)))
            trackTintList = ColorStateList(states, intArrayOf(0x77D10A20, 0x33FFFFFF))
            setOnCheckedChangeListener { _, v -> set(v) }
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 8.dp())
            addView(text(s, 15f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
    }

    /** Choix en pastilles, ex. 1 min / 5 min. */
    private fun chips(s: String, options: List<Pair<Int, String>>, value: Int, set: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(s, 14f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        var selected = value
        val views = ArrayList<Pair<Int, TextView>>()
        fun paint() = views.forEach { (v, t) ->
            t.background = if (v == selected) round(RED, 99) else glass(99)
            t.setTextColor(if (v == selected) Color.WHITE else MUTED)
        }
        options.forEach { (v, label) ->
            val t = text(label, 13f, MUTED, medium).apply {
                setPadding(12.dp(), 7.dp(), 12.dp(), 7.dp())
                setOnClickListener {
                    selected = v
                    set(v)
                    paint()
                }
            }
            views.add(v to t)
            row.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 6.dp() })
        }
        paint()
        return row.apply { setPadding(0, 4.dp(), 0, 8.dp()) }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass(22)
        setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
    }

    private fun tile(icon: Int, s: String, danger: Boolean = false, action: () -> Unit): LinearLayout {
        val tint = if (danger) DANGER else TEXT
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(16, clickable = true)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            minimumHeight = 92.dp()
            isClickable = true
            setOnClickListener { action() }
            addView(ImageView(this@SettingsActivity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(tint)
            }, LinearLayout.LayoutParams(26.dp(), 26.dp()))
            addView(text(s, 14f, tint, medium).apply { setPadding(0, 12.dp(), 0, 0) })
        }
        return t
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val wide = resources.configuration.screenWidthDp >= 720

        // En-tête
        statusDot = View(this)
        statusText = text("", 13f, MUTED, medium)
        val pill = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = glass(99)
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            addView(statusDot, LinearLayout.LayoutParams(9.dp(), 9.dp()).apply { marginEnd = 8.dp() })
            addView(statusText)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("SAMOTH DASH", 26f, TEXT, title))
                addView(text("Réglages de la tablette", 13f, MUTED))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(pill)
        }

        // Colonne gauche : configuration
        url = field(prefs.dashboardUrl, "http://192.168.1.10:8123/lovelace/0").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        name = field(prefs.deviceName.ifBlank { "Tablette" })
        val config = card().apply {
            addView(text("Tableau de bord", 16f, TEXT, medium))
            addView(label("Adresse"))
            addView(url)
            addView(label("Nom dans Home Assistant (à la 1re connexion)"))
            addView(name)
            addView(View(this@SettingsActivity).apply { setBackgroundColor(GLASS_LINE) },
                LinearLayout.LayoutParams(-1, 1.dp()).apply { topMargin = 16.dp(); bottomMargin = 8.dp() })
            addView(toggle("Masquer la barre de gauche", prefs.hideSidebar) { prefs.hideSidebar = it })
            addView(toggle("Masquer la barre du haut", prefs.hideHeader) { prefs.hideHeader = it })
            addView(toggle("Garder l'écran allumé", prefs.keepScreenOn) { prefs.keepScreenOn = it })
        }

        val monitor = card().apply {
            addView(text("Surveillance", 16f, TEXT, medium))
            addView(toggle("Relancer les flux figés", prefs.watchdog) { prefs.watchdog = it })
            addView(chips("Recharger aussi toutes les", listOf(0 to "Jamais", 15 to "15 min", 30 to "30 min", 60 to "1 h"), prefs.reloadMinutes) { prefs.reloadMinutes = it })
            addView(toggle("Alerte plein écran si personne détectée", prefs.personAlert) { prefs.personAlert = it })
            motionToggle = toggle("Veille et réveil à l'approche", prefs.motionWake) { on ->
                prefs.motionWake = on
                if (on && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
                }
            }
            addView(motionToggle)
            addView(chips("Mettre en veille après", listOf(1 to "1 min", 2 to "2 min", 5 to "5 min", 10 to "10 min"), prefs.sleepMinutes) { prefs.sleepMinutes = it })
        }

        // Colonne droite : bouton principal et tuiles
        val primary = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = glass(16, clickable = true, color = RED, line = RED)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            isClickable = true
            setOnClickListener { saveAndClose() }
            addView(ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_play)
            }, LinearLayout.LayoutParams(24.dp(), 24.dp()).apply { marginEnd = 8.dp() })
            addView(text("Enregistrer et afficher", 16f, Color.WHITE, medium))
        }
        updateTile = tile(R.drawable.ic_update, "Mise à jour") { installUpdate() }
        updateLabel = (updateTile as LinearLayout).getChildAt(1) as TextView
        updateTile.visibility = View.GONE
        val grid = GridLayout(this).apply {
            columnCount = 2
            val tiles = listOf(
                tile(R.drawable.ic_tablet, "Paramètres Android") { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                tile(R.drawable.ic_battery, "Envoi en veille") { askBatteryExemption() },
                tile(R.drawable.ic_home, "Écran d'accueil") {
                    runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                },
                tile(R.drawable.ic_logout, "Se déconnecter", danger = true) { logout() },
                // En dernier : masquée sans mise à jour, elle ne laisse aucun trou
                updateTile,
            )
            tiles.forEach { t ->
                addView(t, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                ).apply {
                    width = 0
                    setMargins(5.dp(), 5.dp(), 5.dp(), 5.dp())
                })
            }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(primary)
            addView(monitor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
            addView(grid, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 7.dp()
                marginStart = (-5).dp()
                marginEnd = (-5).dp()
            })
        }

        val columns = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(config, LinearLayout.LayoutParams(0, -2, 1.15f).apply { marginEnd = 18.dp() })
                addView(actions, LinearLayout.LayoutParams(0, -2, 1f))
            } else {
                addView(config)
                addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16.dp() })
            }
        }
        infoText = text("", 12f, DIM)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dp(), 28.dp(), 32.dp(), 24.dp())
            addView(header)
            addView(columns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 22.dp() })
            addView(infoText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16.dp() })
            addView(text("Pour revenir ici : glisser du bord gauche de l'écran vers la droite", 12f, DIM))
        }

        // Grande signature transparente en fond, comme sur Samoth Home
        val watermark = text("SAMOTH", 118f, 0x0DFFFFFF, title).apply { isSingleLine = true }
        setContentView(FrameLayout(this).apply {
            addView(watermark, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = (-14).dp()
                marginEnd = 12.dp()
            })
            addView(ScrollView(this@SettingsActivity).apply {
                isFillViewport = true
                addView(content)
            })
        })
        checkUpdate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            ((motionToggle as LinearLayout).getChildAt(1) as Switch).isChecked = false
            Toast.makeText(this, "Sans la caméra, le réveil à l'approche est impossible", Toast.LENGTH_LONG).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        main.post(refresh)
    }

    override fun onPause() {
        main.removeCallbacks(refresh)
        super.onPause()
    }

    private fun saveAndClose() {
        var u = url.text.toString().trim()
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        val parsed = Uri.parse(u)
        if (parsed.host.isNullOrEmpty()) {
            Toast.makeText(this, "Adresse invalide", Toast.LENGTH_LONG).show()
            return
        }
        val serverChanged = prefs.dashboardUrl.isNotBlank() &&
            Uri.parse(prefs.dashboardUrl).encodedAuthority != parsed.encodedAuthority
        prefs.dashboardUrl = u
        prefs.deviceName = name.text.toString().trim()
        if (serverChanged) {
            prefs.clearAuth()
            prefs.webhookId = null
        }
        val p = getSharedPreferences("dash", MODE_PRIVATE)
        if (!p.getBoolean("askedExempt", false)) {
            p.edit().putBoolean("askedExempt", true).apply()
            askBatteryExemption()
        }
        finish()
    }

    private fun askBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Déjà autorisé", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    private fun logout() {
        prefs.clearAuth()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        Toast.makeText(this, "Déconnecté", Toast.LENGTH_SHORT).show()
    }

    private fun checkUpdate() {
        thread {
            val info = runBlocking { Updater.check() } ?: return@thread
            main.post {
                update = info
                updateLabel.text = "Mise à jour ${info.version}"
                updateTile.visibility = View.VISIBLE
            }
        }
    }

    private fun installUpdate() {
        val info = update ?: return
        if (updating) return
        updating = true
        thread {
            try {
                val file = runBlocking {
                    Updater.download(this@SettingsActivity, info) { p ->
                        main.post { updateLabel.text = "Téléchargement ${(p * 100).toInt()} %" }
                    }
                }
                main.post {
                    updating = false
                    updateLabel.text = "Installer la ${info.version}"
                    Updater.install(this, file)
                }
            } catch (e: Exception) {
                main.post {
                    updating = false
                    updateLabel.text = "Réessayer"
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        const val TEXT = 0xFFF5F5F7.toInt()
        const val MUTED = 0xFFA3A3B2.toInt()
        const val DIM = 0xFF8E8E93.toInt()
        const val RED = 0xFFD10A20.toInt()
        const val DANGER = 0xFFFF6B6B.toInt()
        const val GREEN = 0xFF34C759.toInt()
        const val GLASS = 0x0FFFFFFF
        const val GLASS_LINE = 0x1FFFFFFF
        const val FIELD = 0x14FFFFFF
    }
}
