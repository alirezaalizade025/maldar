package com.personalfinance.tracker.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.personalfinance.tracker.BuildConfig
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
 * Results are reported via [UpdateResult] to tell "up to date" apart from errors.
 */
object UpdateChecker {

    // Pulled from BuildConfig so it always matches the built APK's auto-bumped version.
    val CURRENT_VERSION_NAME: String = BuildConfig.VERSION_NAME
    val CURRENT_VERSION_CODE: Int = BuildConfig.VERSION_CODE

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

    sealed interface UpdateResult {
        data class Available(val info: UpdateInfo) : UpdateResult
        object UpToDate : UpdateResult
        object Error : UpdateResult
    }

    /**
     * Checks GitHub Releases for a newer version of the app.
     * Returns [UpdateResult.Available] when a newer, non-skipped release with an
     * APK is found, [UpdateResult.UpToDate] when the installed build is current,
     * and [UpdateResult.Error] when the network/parse check fails.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
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
                return@withContext UpdateResult.Available(
                    UpdateInfo(
                        tag = tag,
                        version = version,
                        versionCode = code,
                        name = r.optString("name", tag),
                        downloadUrl = apkUrl ?: htmlUrl,
                        notes = r.optString("body", "")
                    )
                )
            }
            UpdateResult.UpToDate
        } catch (e: Exception) {
            Log.e(TAG, "update check failed", e)
            UpdateResult.Error
        }
    }

    private fun parseVersionCode(version: String): Int {
        // Accept an explicit ".cN" suffix that mirrors the build's versionCode,
        // e.g. "1.2.c3" -> 3. This is the ONLY reliable code to compare against
        // BuildConfig.VERSION_CODE (which is the git commit count). Without this
        // suffix the tag is a plain semver and we must NOT synthesize a code,
        // because mapping "1.1" -> 10100 would always beat the real code (2..N).
        val suffix = version.substringAfterLast(".c", "")
        if (version.contains(".c") && suffix.isNotBlank()) {
            suffix.toIntOrNull()?.let { return it }
        }
        return -1
    }

    private fun isNewer(candidateCode: Int, candidateVersion: String): Boolean {
        // Only trust the code comparison when the candidate actually carries a
        // matching ".cN" versionCode suffix; otherwise fall back to semver.
        if (candidateCode > 0) return candidateCode > CURRENT_VERSION_CODE
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
