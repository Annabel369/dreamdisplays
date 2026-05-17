package com.dreamdisplays.server.listeners

import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.managers.SelectionManager
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.level.ServerLevel

/**
 * Listener for protecting display areas from modifications.
 * Handles block breaking, explosions, and piston movements.
 *
 * `Fabric server` implementation.
 */
// TODO: missing piston extend / retract and entity explosion events have no direct Fabric equivalent and were not
//  ported. Piston protection and explosion protection are absent on Fabric servers.
object ProtectionListener {
    fun register() {
        // Fabric server start
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, blockEntity ->
            val worldKey = RegionUtil.getLevelKey(world as ServerLevel)
            // Fabric server end

            // Paper start
            val isProtected = DisplayManager.isContains(worldKey, pos) != null
                    || SelectionManager.isLocationSelected(pos, worldKey)

            if (isProtected) {
                if (player is net.minecraft.server.level.ServerPlayer) {
                    if (PlayerManager.getVersion(player) == null) {
                        MessageUtil.sendMessage(player, "displayBlockBreak")
                    }
                }
                return@register false
            }
            true
        }
        // Paper end
    }
}
