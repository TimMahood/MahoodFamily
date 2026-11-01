package com.mahood.family

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks the GitHub repo's latest release (works when the repo is public). */
object UpdateChecker {
    data class Update(val version: String, val apkUrl: String)

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return@withContext null
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = o.getString("tag_name") // e.g. v0.1.42
            val build = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
            if (build <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = o.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            Update(tag.removePrefix("v"), apk.getString("browser_download_url"))
        } catch (e: Exception) {
            null
        }
    }
}
