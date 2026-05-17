package com.dreamdisplays.server.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Utility object for sending reports to a Discord webhook.
 *
 * `Fabric server` implementation.
 */
// TODO: add rate limiting to prevent spam
// TODO: customize embed further (add more fields, etc.)
object ReporterUtil {
    // Paper start
    private val httpClient: HttpClient by lazy { HttpClient.newHttpClient() }
    private const val EMBED_COLOR = 0x2F3136
    private const val EMBED_TITLE = "# New report"

    fun sendReport(
        locationStr: String,
        videoLink: String?,
        displayId: UUID,
        reporterName: String,
        ownerName: String?,
        webhookUrl: String,
    ) {
        val payload = buildWebhookPayload(locationStr, videoLink, displayId, reporterName, ownerName)
        sendWebhookRequest(webhookUrl, payload)
    }

    private fun buildWebhookPayload(
        locationStr: String,
        videoLink: String?,
        displayId: UUID,
        reporterName: String,
        ownerName: String?,
    ): String {
        val embed = JsonObject().apply {
            addProperty("description", EMBED_TITLE)
            addProperty("color", EMBED_COLOR)
            addProperty("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            add("fields", JsonArray().apply {
                add(createField("Location", locationStr, false))
                add(createField("Video", videoLink, false))
                add(createField("UUID", displayId.toString(), false))
                add(createField("Reporter", reporterName, false))
                add(createField("Owner", ownerName, false))
            })
        }
        return JsonObject().apply {
            add("embeds", JsonArray().apply { add(embed) })
        }.toString()
    }

    private fun createField(name: String, value: String?, inline: Boolean) = JsonObject().apply {
        addProperty("name", name)
        addProperty("value", value ?: "N/A")
        addProperty("inline", inline)
    }

    private fun sendWebhookRequest(webhookUrl: String, payload: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() / 100 != 2) {
            throw IOException("[ReporterUtil] Discord webhook failed: ${response.statusCode()} - ${response.body()}")
        }
    }
    // Paper end
}
