package com.samoth.dash

import android.content.Context
import java.util.UUID

/** Réglages et jetons, stockés dans l'espace privé de l'appli. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("dash", Context.MODE_PRIVATE)

    /** Adresse complète du tableau de bord, ex. http://192.168.1.10:8123/lovelace/0 */
    var dashboardUrl: String
        get() = p.getString("url", "") ?: ""
        set(v) = p.edit().putString("url", v).apply()
    var hideSidebar: Boolean
        get() = p.getBoolean("hideSidebar", true)
        set(v) = p.edit().putBoolean("hideSidebar", v).apply()
    var hideHeader: Boolean
        get() = p.getBoolean("hideHeader", true)
        set(v) = p.edit().putBoolean("hideHeader", v).apply()
    var keepScreenOn: Boolean
        get() = p.getBoolean("keepOn", true)
        set(v) = p.edit().putBoolean("keepOn", v).apply()
    /** Recharge le dashboard si une vidéo reste figée. */
    var watchdog: Boolean
        get() = p.getBoolean("watchdog", true)
        set(v) = p.edit().putBoolean("watchdog", v).apply()
    /** Rechargement périodique en minutes, 0 = jamais. */
    var reloadMinutes: Int
        get() = p.getInt("reloadMinutes", 60)
        set(v) = p.edit().putInt("reloadMinutes", v).apply()
    /** Plein écran quand une caméra Frigate détecte une personne. */
    var personAlert: Boolean
        get() = p.getBoolean("personAlert", true)
        set(v) = p.edit().putBoolean("personAlert", v).apply()
    /** Écran assombri sans présence, réveil par la caméra frontale. */
    var motionWake: Boolean
        get() = p.getBoolean("motionWake", false)
        set(v) = p.edit().putBoolean("motionWake", v).apply()
    var sleepMinutes: Int
        get() = p.getInt("sleepMinutes", 5)
        set(v) = p.edit().putInt("sleepMinutes", v).apply()
    var deviceName: String
        get() = p.getString("deviceName", "") ?: ""
        set(v) = p.edit().putString("deviceName", v).apply()
    var refreshToken: String?
        get() = p.getString("refresh", null)
        set(v) = p.edit().putString("refresh", v).apply()
    var accessToken: String?
        get() = p.getString("access", null)
        set(v) = p.edit().putString("access", v).apply()
    var accessExpires: Long
        get() = p.getLong("accessExp", 0)
        set(v) = p.edit().putLong("accessExp", v).apply()
    var webhookId: String?
        get() = p.getString("webhook", null)
        set(v) = p.edit().putString("webhook", v).apply()
    var lastSend: String
        get() = p.getString("lastSend", "") ?: ""
        set(v) = p.edit().putString("lastSend", v).apply()

    /** Identifiant stable : évite de créer un nouvel appareil dans HA à chaque enregistrement. */
    val deviceId: String
        get() = p.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("deviceId", it).apply()
        }

    /** http://hote:port, sans chemin. */
    val baseUrl: String?
        get() = runCatching {
            val u = android.net.Uri.parse(dashboardUrl)
            val scheme = u.scheme ?: return null
            val auth = u.encodedAuthority ?: return null
            "$scheme://$auth"
        }.getOrNull()

    fun clearAuth() {
        p.edit().remove("refresh").remove("access").remove("accessExp").apply()
    }
}
