package com.personalfinance.tracker.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Checks GitHub Releases for a newer version of the app.
 * Compares the release tag (e.g. "v1.2") against [CURRENT_VERSION].
 */
object UpdateChecker {

    // Must match versionName in app/build.gradle.kts
    const val CURRENT_VERSION = "1.0"

    private const val RELEASES_URL = "https://api.github.com/repos/alirezaalizade025/maldar/releases"
    private const val TAG = "UpdateChecker"

    data class UpdateInfo(
        val version: String,        // tag without leading "v"
        val name: String,           // release title
        val downloadUrl: String?,    // APK asset browser/download url
        val notes: String
    )

    /**
     * Returns the newest release that has an APK asset, or null if none found / on error.
     * Only considers releases whose version is newer than [CURRENT_VERSION].
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            val releases = JSONArray(body)

            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val tag = r.optString("tag_name", "")
                val version = tag.removePrefix("v")
                if (!isNewer(version)) continue

                var apkUrl: String? = null
                val assets = r.optJSONArray("assets")
                if (assets != null) {
                    for (a in 0 until assets.length()) {
                        val asset = assets.getJSONObject(a)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }
                val htmlUrl = r.optString("html_url", null)
                return@withContext UpdateInfo(
                    version = version,
                    name = r.optString("name", tag),
                    downloadUrl = apkUrl ?: htmlUrl,
                    notes = r.optString("body", "")
                )
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "update check failed", e)
            null
        }
    }

    private fun isNewer(candidate: String): Boolean {
        return compareVersions(candidate, CURRENT_VERSION) > 0
    }

    // Simple semantic-ish compare of "1.2.3" style strings.
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
