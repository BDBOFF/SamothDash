package com.samoth.dash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: Int, val url: String)

/**
 * Mise à jour intégrée : chaque compilation sur GitHub publie une version « build-N ».
 * L'appli compare N à sa propre version, télécharge l'APK et lance l'installation.
 */
object Updater {
    private const val REPO = "BDBOFF/SamothDash"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("https://api.github.com/repos/$REPO/releases/latest")
            val o = JSONObject(body)
            val version = o.optString("tag_name").substringAfter("build-").toIntOrNull() ?: return@runCatching null
            val assets = o.optJSONArray("assets") ?: return@runCatching null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
            }
            if (url != null && version > BuildConfig.VERSION_CODE) UpdateInfo(version, url) else null
        }.getOrNull()
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "SamothDash-${info.version}.apk")
            val conn = open(info.url)
            try {
                if (conn.responseCode !in 200..299) throw IOException("Téléchargement impossible (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0 && done - last > 200 * 1024) { last = done; onProgress(done.toFloat() / total) }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            onProgress(1f)
            file
        }

    /** Renvoie false si la tablette demande d'abord d'autoriser l'installation (l'écran est ouvert). */
    fun install(context: Context, file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: ActivityNotFoundException) {
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SamothDash/${BuildConfig.VERSION_CODE}")
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: return c
                c.disconnect()
                current = URL(URL(current), loc).toString()
            } else return c
        }
        throw IOException("Trop de redirections")
    }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
