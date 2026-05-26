package com.dreamdisplays.listeners

import com.dreamdisplays.managers.DisplayManager.isContains
import com.dreamdisplays.managers.PlayerManager
import com.dreamdisplays.managers.SelectionManager.isLocationSelected
import com.dreamdisplays.utils.MessageUtil.sendMessage
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent

/**
 * Listener for protecting display areas from modifications.
 * Handles block breaking, explosions, and piston movements.
 *
 * `Paper` implementation.
 */
@Suppress("UNUSED")
class ProtectionListener : Listener {
    /**
     * Handles block breaking events, checking if the block is protected and canceling if necessary.
     */
    @EventHandler fun onBlockBreak(event: BlockBreakEvent) {
        val loc = event.block.location
        if (isContains(loc) != null && PlayerManager.getVersion(event.player) == null) {
            sendMessage(event.player, "displayBlockBreak")
        }
        cancelIfProtected(loc, event)
    }

    /**
     * Handles explosion events, removing protected blocks from the list of blocks to be destroyed.
     */
    @EventHandler fun onExplosion(event: EntityExplodeEvent) {
        event.blockList().removeIf { isLocationProtected(it.location) }
    }

    /**
     * Handles piston movements, checking if the piston is moving blocks protected by displays.
     */
    @EventHandler fun onPistonExtend(event: BlockPistonExtendEvent) = handlePiston(event.blocks, event)

    /**
     * Handles piston retraction, checking if the piston is moving blocks protected by displays.
     */
    @EventHandler fun onPistonRetract(event: BlockPistonRetractEvent) = handlePiston(event.blocks, event)

    /**
     * Handles piston movements, checking if the piston is moving blocks protected by displays.
     */
    private fun handlePiston(blocks: List<Block>, event: Cancellable) {
        if (event.isCancelled) return
        if (blocks.any { isLocationProtected(it.location) }) event.isCancelled = true
    }

    /**
     * Handles piston movements, checking if the piston is moving blocks protected by displays.
     */
    private fun cancelIfProtected(loc: Location, event: Cancellable) {
        if (isLocationProtected(loc)) event.isCancelled = true
    }

    /**
     * Checks if a location is in a protected area, considering both existing displays and current selections.
     */
    private fun isLocationProtected(loc: Location): Boolean = isContains(loc) != null || isLocationSelected(loc)
}
