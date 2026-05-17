package com.dreamdisplays.server.meta

import com.dreamdisplays.Server
import com.github.zafarkhaja.semver.Version
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Pattern

/**
 * Checks for updates of the plugin and mod from `GitHub` releases.
 *
 * `Fabric server` implementation.
 */
object Updater {
    // Fabric server start
    private val logger = LoggerFactory.getLogger("DreamDisplays/Updater")
    private val gson = Gson()
    private val client: HttpClient = HttpClient.newHttpClient()
    // Fabric server end
    // Paper start
    private val tailPattern = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")

    fun checkForUpdates(repoOwner: String, repoName: String) {
        try {
            val releases = fetchReleases(repoOwner, repoName)
            if (releases.isEmpty()) {
                logger.warn("[Updater] No releases found on GitHub.")
                return
            }

            // Fabric server start
            Server.modLatestVersion = releases
                .mapNotNull { parseVersion(it.tagName) }
                .filter { !it.toString().contains("-SNAPSHOT") }
                .maxOrNull()

            Server.pluginLatestVersion = releases
                .filter {
                    it.tagName.contains("spigot", ignoreCase = true) ||
                            it.tagName.contains("plugin", ignoreCase = true)
                }
                .mapNotNull { parseVersion(it.tagName)?.toString() }
                .filter { !it.contains("-SNAPSHOT") }
                .maxOrNull() ?: Server.modLatestVersion?.toString()
            // Fabric server end

        } catch (_: UnknownHostException) {
            logger.warn("[Updater] Cannot reach GitHub (DNS resolution failed).")
        } catch (_: ConnectException) {
            logger.warn("[Updater] Cannot connect to GitHub.")
        } catch (_: SocketTimeoutException) {
            logger.warn("[Updater] GitHub connection timed out.")
        } catch (e: Exception) {
            logger.warn("[Updater] Unable to load versions from GitHub: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
    // Paper end

    // Fabric server start
    private fun fetchReleases(owner: String, repo: String): List<Release> {
        val url = "https://api.github.com/repos/$owner/$repo/releases"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "DreamDisplays-Updater")
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return emptyList()

        return gson.fromJson(
            response.body(),
            object : TypeToken<List<Release>>() {}.type
        ) ?: emptyList()
    }
    // Fabric server end

    // Paper start
    private fun parseVersion(tag: String): Version? {
        val matcher = tailPattern.matcher(tag)
        return if (matcher.find()) runCatching { Version.parse(matcher.group()) }.getOrNull()
        else null
    }

    data class Release(
        @field:SerializedName("tag_name") val tagName: String,
        @field:SerializedName("name") val name: String,
    )
    // Paper end
}
