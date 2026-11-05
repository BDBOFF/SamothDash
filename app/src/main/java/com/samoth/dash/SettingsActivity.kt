package com.samoth.dash

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var url: EditText
    private lateinit var name: EditText
    private lateinit var status: TextView
    private lateinit var updateBtn: Button
    private var update: UpdateInfo? = null
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            status.text = "Dernier envoi : " + prefs.lastSend.ifBlank { "aucun" } +
                "\nConnecté à HA : " + (if (prefs.refreshToken != null) "oui" else "non") +
                "\nVersion : " + BuildConfig.VERSION_NAME
            main.postDelayed(this, 2000)
        }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.LTGRAY)
        setPadding(0, 16.dp(), 0, 4.dp())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }
        col.addView(TextView(this).apply {
            text = "Samoth Dash"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        col.addView(label("Adresse du tableau de bord"))
        url = EditText(this).apply {
            hint = "http://192.168.1.10:8123/lovelace/0"
            setText(prefs.dashboardUrl)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        col.addView(url)
        col.addView(label("Nom de la tablette dans Home Assistant (avant la 1re connexion)"))
        name = EditText(this).apply {
            setText(prefs.deviceName.ifBlank { "Tablette" })
            setTextColor(Color.WHITE)
            isSingleLine = true
        }
        col.addView(name)
        val sidebar = check("Masquer la barre de gauche", prefs.hideSidebar) { prefs.hideSidebar = it }
        val header = check("Masquer la barre du haut", prefs.hideHeader) { prefs.hideHeader = it }
        val keep = check("Garder l'écran allumé", prefs.keepScreenOn) { prefs.keepScreenOn = it }
        col.addView(sidebar); col.addView(header); col.addView(keep)

        col.addView(button("Enregistrer et afficher") { saveAndClose() })
        updateBtn = button("Mettre à jour") { installUpdate() }.apply { visibility = View.GONE }
        col.addView(updateBtn)
        col.addView(button("Autoriser l'envoi en veille") { askBatteryExemption() })
        col.addView(button("Choisir comme écran d'accueil (démarrage auto)") {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        col.addView(button("Se déconnecter de Home Assistant") { logout() })
        col.addView(button("Paramètres de la tablette") { startActivity(Intent(Settings.ACTION_SETTINGS)) })
        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setPadding(0, 16.dp(), 0, 0)
        }
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col); fitsSystemWindows = true })
        checkUpdate()
    }

    override fun onResume() {
        super.onResume()
        main.post(refresh)
    }

    override fun onPause() {
        main.removeCallbacks(refresh)
        super.onPause()
    }

    private fun check(text: String, value: Boolean, set: (Boolean) -> Unit) = CheckBox(this).apply {
        this.text = text
        isChecked = value
        setTextColor(Color.WHITE)
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
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
        val first = !getSharedPreferences("dash", MODE_PRIVATE).getBoolean("askedExempt", false)
        if (first) {
            getSharedPreferences("dash", MODE_PRIVATE).edit().putBoolean("askedExempt", true).apply()
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
                updateBtn.text = "Mettre à jour (version ${info.version})"
                updateBtn.visibility = View.VISIBLE
            }
        }
    }

    private fun installUpdate() {
        val info = update ?: return
        updateBtn.isEnabled = false
        thread {
            try {
                val file = runBlocking { Updater.download(this@SettingsActivity, info) { p ->
                    main.post { updateBtn.text = "Téléchargement ${(p * 100).toInt()} %" }
                } }
                main.post {
                    updateBtn.isEnabled = true
                    updateBtn.text = "Installer la version ${info.version}"
                    Updater.install(this, file)
                }
            } catch (e: Exception) {
                main.post {
                    updateBtn.isEnabled = true
                    updateBtn.text = "Réessayer la mise à jour"
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
