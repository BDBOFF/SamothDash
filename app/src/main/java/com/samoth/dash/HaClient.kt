package com.samoth.dash

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Le serveur a refusé le jeton : il faut se reconnecter. */
class AuthRevoked : IOException("Connexion refusée par Home Assistant")

/** Appels à Home Assistant. À n'utiliser que hors du fil principal. */
class HaClient(private val prefs: Prefs) {

    private data class Resp(val code: Int, val body: String)

    private fun http(url: String, body: String, type: String, bearer: String? = null): Resp {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            return Resp(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun saveTokens(body: String) {
        val o = JSONObject(body)
        prefs.accessToken = o.getString("access_token")
        prefs.accessExpires = System.currentTimeMillis() + o.optLong("expires_in", 1800) * 1000
        o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { prefs.refreshToken = it }
    }

    /** Échange le code reçu après la page de connexion contre des jetons. */
    fun exchangeCode(code: String) {
        val base = prefs.baseUrl ?: throw IOException("Adresse invalide")
        val r = http(
            "$base/auth/token",
            "grant_type=authorization_code&code=${enc(code)}&client_id=${enc("$base/")}",
            "application/x-www-form-urlencoded",
        )
        if (r.code !in 200..299) throw IOException("Connexion impossible (HTTP ${r.code})")
        saveTokens(r.body)
    }

    /** Renvoie un jeton d'accès valide, renouvelé si besoin. */
    @Synchronized
    fun accessToken(force: Boolean = false): String {
        val current = prefs.accessToken
        if (!force && current != null && System.currentTimeMillis() < prefs.accessExpires - 60_000) return current
        val base = prefs.baseUrl ?: throw IOException("Adresse invalide")
        val refresh = prefs.refreshToken ?: throw AuthRevoked()
        val r = http(
            "$base/auth/token",
            "grant_type=refresh_token&refresh_token=${enc(refresh)}&client_id=${enc("$base/")}",
            "application/x-www-form-urlencoded",
        )
        if (r.code == 400 || r.code == 401 || r.code == 403) throw AuthRevoked()
        if (r.code !in 200..299) throw IOException("HTTP ${r.code}")
        saveTokens(r.body)
        return prefs.accessToken!!
    }

    fun secondsLeft(): Long = ((prefs.accessExpires - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** Déclare la tablette comme appareil de l'intégration « Mobile App » de HA. */
    @Synchronized
    fun registerDevice() {
        if (prefs.webhookId != null) return
        val base = prefs.baseUrl ?: throw IOException("Adresse invalide")
        val name = prefs.deviceName.ifBlank { "Tablette" }
        val payload = JSONObject()
            .put("device_id", prefs.deviceId)
            .put("app_id", BuildConfig.APPLICATION_ID)
            .put("app_name", "Samoth Dash")
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("device_name", name)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("os_name", "Android")
            .put("os_version", Build.VERSION.RELEASE)
            .put("supports_encryption", false)
        val r = http("$base/api/mobile_app/registrations", payload.toString(), "application/json", accessToken())
        if (r.code == 401 || r.code == 403) throw AuthRevoked()
        if (r.code !in 200..299) throw IOException("Enregistrement impossible (HTTP ${r.code}). L'intégration mobile_app est-elle active ?")
        prefs.webhookId = JSONObject(r.body).getString("webhook_id")
    }

    /** Envoie l'état de la batterie ; déclare les capteurs si HA ne les connaît pas encore. */
    fun send(b: BatteryState, declare: Boolean) {
        if (prefs.webhookId == null) registerDevice()
        if (declare) declareSensors(b)
        val update = JSONObject().put("type", "update_sensor_states").put("data", JSONArray().also { a ->
            a.put(state(LEVEL, b.level, "sensor", b.icon))
            a.put(state(CHARGING, b.charging, "binary_sensor", b.icon))
            a.put(state(TEMP, b.temperature, "sensor", "mdi:thermometer"))
        })
        val r = webhook(update)
        val missing = runCatching {
            val o = JSONObject(r.body)
            o.keys().asSequence().any { !o.getJSONObject(it).optBoolean("success", true) }
        }.getOrDefault(false)
        if (missing && !declare) send(b, declare = true)
    }

    private fun state(id: String, value: Any, type: String, icon: String) = JSONObject()
        .put("unique_id", id).put("state", value).put("type", type).put("icon", icon)

    private fun declareSensors(b: BatteryState) {
        webhook(sensor(LEVEL, "Batterie", b.level, "sensor", b.icon, "battery", "%"))
        webhook(sensor(CHARGING, "En charge", b.charging, "binary_sensor", b.icon, "battery_charging", null))
        webhook(sensor(TEMP, "Température batterie", b.temperature, "sensor", "mdi:thermometer", "temperature", "°C"))
    }

    private fun sensor(id: String, name: String, value: Any, type: String, icon: String, cls: String, unit: String?): JSONObject {
        val d = JSONObject()
            .put("unique_id", id).put("name", name).put("state", value).put("type", type)
            .put("icon", icon).put("device_class", cls).put("entity_category", "diagnostic")
        if (unit != null) d.put("unit_of_measurement", unit).put("state_class", "measurement")
        return JSONObject().put("type", "register_sensor").put("data", d)
    }

    private fun webhook(payload: JSONObject): Resp {
        val base = prefs.baseUrl ?: throw IOException("Adresse invalide")
        val id = prefs.webhookId ?: throw IOException("Appareil non enregistré")
        val r = http("$base/api/webhook/$id", payload.toString(), "application/json")
        if (r.code == 404 || r.code == 410) {
            // Appareil supprimé dans HA : on s'enregistrera de nouveau au prochain envoi
            prefs.webhookId = null
            throw IOException("Appareil supprimé dans Home Assistant")
        }
        if (r.code !in 200..299) throw IOException("HTTP ${r.code}")
        return r
    }

    private companion object {
        const val LEVEL = "battery_level"
        const val CHARGING = "battery_charging"
        const val TEMP = "battery_temperature"
    }
}
