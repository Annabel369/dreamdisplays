package com.dreamdisplays.listeners

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.managers.DisplayManager.getDisplays
import com.dreamdisplays.managers.PlayerManager
import com.dreamdisplays.managers.PlayerManager.hasBeenNotifiedAboutModRequired
import com.dreamdisplays.managers.PlayerManager.setModRequiredNotified
import com.dreamdisplays.meta.Scheduler
import com.dreamdisplays.utils.MessageUtil.sendMessage
import com.dreamdisplays.utils.PlatformUtil.isFolia
import com.dreamdisplays.utils.net.PacketUtil
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.jspecify.annotations.NullMarked

/**
 * Handles player join and leave events.
 *
 * `Paper` implementation.
 */
@Suppress("UNUSED")
@NullMarked class PlayerListener : Listener {
    private var hasValidatedWorld = false

    /**
     * On first join after startup, validates all stored displays once. Also schedules a delayed
     * `modRequired` message for vanilla clients when mod detection is enabled.
     */
    @EventHandler fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (!hasValidatedWorld && getDisplays().isNotEmpty()) {
            hasValidatedWorld = true
            Scheduler.runLater(40L) {
                val removedDisplayUuids = DisplayManager.validateDisplaysAndCleanup()
                if (removedDisplayUuids.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    PacketUtil.sendClearCache(Bukkit.getOnlinePlayers().toMutableList(), removedDisplayUuids)
                }
            }
        }

        if (!config.settings.modDetectionEnabled) return
        if (getDisplays().isEmpty()) return

        // TODO: implement Folia-compatible entity scheduler for delayed player tasks
        if (isFolia) return

        Scheduler.runLater(600L) {
            if (PlayerManager.getVersion(player) == null && !hasBeenNotifiedAboutModRequired(player)) {
                sendMessage(player, "modRequired")
                setModRequiredNotified(player, true)
            }
        }
    }

    /** Drops cached per-player state when a player disconnects. */
    @EventHandler fun onPlayerLeave(event: PlayerQuitEvent) {
        PlayerManager.removeVersion(event.player)
    }
}
