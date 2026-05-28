package com.dreamdisplays.meta

import com.dreamdisplays.utils.GeneralUtil
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.inotsleep.utils.logging.LoggingManager
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/** Checks mod updates against the latest stable GitHub release. **/
object UpdateCheck {
    private const val API = "https://api.github.com/repos/arsmotorin/dreamdisplays/releases/latest"

    @Volatile private var checked = false
    @Volatile private var updateAvailable = false
    @Volatile private var latestVersion: String? = null

    /** Returns true if a newer stable release exists; skipped entirely for DEV / SNAPSHOT builds. */
    fun isUpdateAvailable(): Boolean {
        if (isPreRelease(GeneralUtil.getModVersion())) return false
        if (!checked) startCheck()
        return updateAvailable
    }

    /**
     * Returns true if the UI update arrow should be shown.
     * Suppressed on DEV / SNAPSHOT builds and when the current version is already newer than the latest stable.
     */
    fun shouldShowArrow(): Boolean {
        if (isPreRelease(GeneralUtil.getModVersion())) return false
        if (!checked) startCheck()
        val latest = latestVersion ?: return false
        return compareVersions(latest, GeneralUtil.getModVersion()) > 0
    }

    /** Returns the latest stable release version string, or the installed version if the check hasn't completed. */
    fun latestVersion(): String = latestVersion ?: GeneralUtil.getModVersion()

    /** Returns true if [version] is a DEV or SNAPSHOT build. */
    fun isPreRelease(version: String): Boolean =
        version.contains("-DEV", ignoreCase = true) || version.contains("-SNAPSHOT", ignoreCase = true)

    /** Starts the background update check exactly once; subsequent calls are no-ops. */
    @Synchronized private fun startCheck() {
        if (checked) return
        checked = true
        Thread(::doCheck, "dreamdisplays-update-check").apply { isDaemon = true }.start()
    }

    /** Queries the GitHub releases API and sets [latestVersion] and [updateAvailable]. */
    private fun doCheck() {
        var conn: HttpURLConnection? = null
        try {
            conn = (URI.create(API).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 8_000
                setRequestProperty(
                    "User-Agent",
                    "DreamDisplays/${GeneralUtil.getModVersion()} (+github.com/arsmotorin/dreamdisplays)"
                )
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return
            val body = conn.inputStream.use { it.readAllBytes().toString(StandardCharsets.UTF_8) }
            val root = JsonParser.parseString(body)
            val rawTag: String = when {
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    optString(obj, "tag_name") ?: optString(obj, "name")
                }
                root.isJsonArray -> {
                    val arr = root.asJsonArray
                    if (!arr.isEmpty && arr[0].isJsonObject) optString(arr[0].asJsonObject, "tag_name") else null
                }
                else -> null
            } ?: return
            val tag = rawTag.trimStart('v', 'V')
            latestVersion = tag
            if (compareVersions(tag, GeneralUtil.getModVersion()) > 0) {
                updateAvailable = true
            }
        } catch (e: Exception) {
            LoggingManager.warn("[UpdateChecker] Update check failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    /** Returns the string value of [key] in [obj], or null if absent or null. */
    private fun optString(obj: JsonObject, key: String): String? {
        if (!obj.has(key) || obj.get(key).isJsonNull) return null
        return runCatching { obj.get(key).asString }.getOrNull()
    }

    /**
     * Compares two version strings following semver pre-release rules:
     * - Base versions (major.minor.patch) are compared numerically.
     * - A release is always newer than a pre-release with the same base (e.g., 1.7.0 > 1.7.0-SNAPSHOT.1).
     * - Returns positive if [a] is newer than [b].
     */
    internal fun compareVersions(a: String, b: String): Int = runCatching {
        val (aBase, aPre) = splitVersion(a)
        val (bBase, bPre) = splitVersion(b)

        val baseCmp = compareBase(aBase, bBase)
        if (baseCmp != 0) return baseCmp

        // Same base: release -> pre-release
        return when {
            aPre == null && bPre == null -> 0
            aPre == null -> 1 // a is release, b is pre-release
            bPre == null -> -1 // a is pre-release, b is release
            else -> comparePreRelease(aPre, bPre)
        }
    }.getOrElse { a.compareTo(b) }

    /** Splits "1.7.0-SNAPSHOT.1" into ("1.7.0", "SNAPSHOT.1"), or ("1.7.0", null) for a plain release. */
    private fun splitVersion(v: String): Pair<String, String?> {
        val hyphen = v.indexOf('-')
        return if (hyphen < 0) v to null else v.substring(0, hyphen) to v.substring(hyphen + 1)
    }

    private fun compareBase(a: String, b: String): Int {
        val aa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val bb = b.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aa.size, bb.size)
        for (i in 0 until maxLen) {
            val cmp = (aa.getOrElse(i) { 0 }).compareTo(bb.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    /** Compares pre-release identifiers dot-by-dot; numeric identifiers compared numerically. */
    private fun comparePreRelease(a: String, b: String): Int {
        val aa = a.split('.')
        val bb = b.split('.')
        val maxLen = maxOf(aa.size, bb.size)
        for (i in 0 until maxLen) {
            val x = aa.getOrElse(i) { return -1 }
            val y = bb.getOrElse(i) { return 1 }
            if (x == y) continue
            val xi = x.toIntOrNull()
            val yi = y.toIntOrNull()
            val cmp = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
