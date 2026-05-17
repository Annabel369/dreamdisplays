package com.dreamdisplays.server.managers

import com.github.zafarkhaja.semver.Version
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player-specific data such as versions and notification states.
 *
 * `Fabric server` implementation.
 */
object PlayerManager {
    // Paper start
    private val versions: MutableMap<UUID, Version?> = ConcurrentHashMap()
    private val modUpdateNotified: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    private val pluginUpdateNotified: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    private val modRequiredNotified: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    private val displaysEnabled: MutableMap<UUID, Boolean> = ConcurrentHashMap()

    fun setVersion(player: ServerPlayer, version: Version?) {
        versions[player.uuid] = version
    }

    fun removePlayer(player: ServerPlayer) {
        val id = player.uuid
        versions.remove(id)
        modUpdateNotified.remove(id)
        pluginUpdateNotified.remove(id)
        modRequiredNotified.remove(id)
        displaysEnabled.remove(id)
    }

    fun getVersion(player: ServerPlayer): Version? = versions[player.uuid]

    fun hasBeenNotifiedAboutModUpdate(player: ServerPlayer): Boolean =
        modUpdateNotified[player.uuid] ?: false

    fun setModUpdateNotified(player: ServerPlayer, notified: Boolean) {
        modUpdateNotified[player.uuid] = notified
    }

    fun hasBeenNotifiedAboutPluginUpdate(player: ServerPlayer): Boolean =
        pluginUpdateNotified[player.uuid] ?: false

    fun setPluginUpdateNotified(player: ServerPlayer, notified: Boolean) {
        pluginUpdateNotified[player.uuid] = notified
    }

    fun hasBeenNotifiedAboutModRequired(player: ServerPlayer): Boolean =
        modRequiredNotified[player.uuid] ?: false

    fun setModRequiredNotified(player: ServerPlayer, notified: Boolean) {
        modRequiredNotified[player.uuid] = notified
    }

    fun setDisplaysEnabled(player: ServerPlayer, enabled: Boolean) {
        displaysEnabled[player.uuid] = enabled
    }

    fun isDisplaysEnabled(player: ServerPlayer): Boolean =
        displaysEnabled.getOrDefault(player.uuid, true)

    fun getVersions(): Map<UUID, Version?> = HashMap(versions)
    // Paper end
}
