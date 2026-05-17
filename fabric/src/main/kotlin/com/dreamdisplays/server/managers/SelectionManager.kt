package com.dreamdisplays.server.managers

import com.dreamdisplays.server.datatypes.SelectionData
import com.dreamdisplays.server.utils.MessageUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * Manages player selections for display creation.
 *
 * `Fabric server` implementation.
 */
object SelectionManager {
    // Paper start
    val selectionPoints: MutableMap<UUID, SelectionData> = java.util.concurrent.ConcurrentHashMap()

    fun setFirstPoint(player: ServerPlayer, pos: BlockPos, worldKey: String, face: Direction) {
        // Fabric server start
        val sel = selectionPoints.getOrPut(player.uuid) { SelectionData() }
        if (sel.worldKey != worldKey) sel.reset()
        sel.pos1 = pos
        sel.worldKey = worldKey
        sel.facing = face
        // Fabric server end
        sel.isReady = false
        MessageUtil.sendMessage(player, "firstPointSelected")
    }

    fun setSecondPoint(player: ServerPlayer, pos: BlockPos, worldKey: String) {
        // Fabric server start
        val sel = selectionPoints[player.uuid] ?: return
        if (sel.pos1 == null || sel.worldKey != worldKey) {
        // Fabric server end
            sel.reset()
            MessageUtil.sendMessage(player, "noDisplayTerritories")
            return
        }
        // Fabric server start
        sel.pos2 = pos
        // Fabric server end
        sel.isReady = true
        MessageUtil.sendMessage(player, "secondPointSelected")
    }

    fun resetSelection(player: ServerPlayer) {
        selectionPoints.remove(player.uuid)?.reset()
    }

    // Fabric server start
    fun isLocationSelected(pos: BlockPos, worldKey: String): Boolean =
        selectionPoints.values.any { sel ->
            sel.isReady && sel.worldKey == worldKey && sel.contains(pos)
        }
    // Fabric server end
    // Paper end
}
