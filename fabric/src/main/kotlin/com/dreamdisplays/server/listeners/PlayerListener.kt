package com.dreamdisplays.server.listeners

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.net.ServerScheduler
import com.dreamdisplays.server.utils.MessageUtil
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory

/**
 * Handles player join and leave events.
 *
 * `Fabric server` implementation.
 */
object PlayerListener {
    private val logger = LoggerFactory.getLogger("DreamDisplays/PlayerListener")
    // Paper start
    private var hasValidatedWorld = false

    fun register() {
        // Fabric server start
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            // Fabric server end

            if (!hasValidatedWorld && DisplayManager.getDisplays().isNotEmpty()) {
                hasValidatedWorld = true
                // Fabric server start
                ServerScheduler.runLater(server, 40L) {
                // Fabric server end
                    val removedUuids = DisplayManager.validateAndCleanup(server)
                    if (removedUuids.isNotEmpty()) {
                        PacketUtil.sendClearCache(server.playerList.players, removedUuids)
                    }
                }
            }

            val config = Server.config
            if (!config.settings.modDetectionEnabled) return@register
            if (DisplayManager.getDisplays().isEmpty()) return@register

            // Fabric server start
            ServerScheduler.runLater(server, 600L) {
                if (player.isAlive &&
                // Fabric server end
                    PlayerManager.getVersion(player) == null &&
                    !PlayerManager.hasBeenNotifiedAboutModRequired(player)
                ) {
                    MessageUtil.sendMessage(player, "modRequired")
                    PlayerManager.setModRequiredNotified(player, true)
                }
            }
            // Paper end
        }

        // Fabric server start
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            PlayerManager.removePlayer(handler.player)
        }
        // Fabric server end
    }
}
