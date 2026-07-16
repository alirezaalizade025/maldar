package com.personalfinance.tracker.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Checks GitHub Releases for a newer version of the app.
 * Compares the release's [versionCode] (parsed from the tag when possible, else
 * falls back to a semver compare of [versionName]) against the installed build.
 * A "skip this version" choice is persisted so a dismissed update doesn't nag.
 */
object UpdateChecker {

    // Must match versionName / versionCode in app/build.gradle.kts
    const val CURRENT_VERSION_NAME = "1.1"
    const val CURRENT_VERSION_CODE = 2

    private const val RELEASES_URL = "https://api.github.com/repos/alirezaalizade025/maldar/releases"
    private const val TAG = "UpdateChecker"
    private const val PREFS = "update_checker"
    private const val KEY_SKIPPED = "skipped_version_tag"

    data class UpdateInfo(
        val tag: String,             // full tag, e.g. "v1.2"
        val version: String,         // tag without leading "v"
        val versionCode: Int,        // parsed from tag, or 0 if unknown
        val name: String,            // release title
        val downloadUrl: String?,    // APK asset browser/download url
        val notes: String
    )

    private val prefs: SharedPreferences?
        get() = AppContextProvider.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markSkipped(tag: String) {
        prefs?.edit()?.putString(KEY_SKIPPED, tag)?.apply()
    }

    fun clearSkipped() {
        prefs?.edit()?.remove(KEY_SKIPPED)?.apply()
    }

    /**
     * Returns the newest release that has an APK asset and is newer than the
     * installed version (and not the skipped one), or null if none found / on error.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            val skipped = prefs?.getString(KEY_SKIPPED, null)

            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val tag = r.optString("tag_name", "")
                if (tag == skipped) continue
                val version = tag.removePrefix("v")
                val code = parseVersionCode(version)
                if (!isNewer(code, version)) continue

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
                    tag = tag,
                    version = version,
                    versionCode = code,
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

    private fun parseVersionCode(version: String): Int {
        // Accept explicit ".cN" suffix, e.g. "1.2.c3" -> 3
        val suffix = version.substringAfterLast(".c", "")
        if (suffix.isNotBlank()) suffix.toIntOrNull()?.let { return it }
        // Fall back: map a semver to a comparable int (1.2.3 -> 10203). Not exact
        // versionCode parity, but enough to order named releases.
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return (parts.getOrElse(0) { 0 } * 10000) + (parts.getOrElse(1) { 0 } * 100) + parts.getOrElse(2) { 0 }
    }

    private fun isNewer(candidateCode: Int, candidateVersion: String): Boolean {
        if (candidateCode > 0 && CURRENT_VERSION_CODE > 0) return candidateCode > CURRENT_VERSION_CODE
        return compareVersions(candidateVersion, CURRENT_VERSION_NAME) > 0
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
