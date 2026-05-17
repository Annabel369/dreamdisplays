package com.dreamdisplays.server.datatypes

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import java.util.*

/**
 * Player's current selection for a feature display.
 *
 * `Fabric server` implementation.
 *
 * @property pos1 One corner of the selected area.
 * @property pos2 Opposite corner of the selected area.
 * @property isReady Boolean indicating if the selection is complete.
 *
 */
class SelectionData {
    // Paper start
    // Fabric server start
    var pos1: BlockPos? = null
    var pos2: BlockPos? = null
    // Fabric server end
    var worldKey: String? = null
    // Fabric server start
    var facing: Direction = Direction.NORTH
    // Fabric server end
    var isReady: Boolean = false

    fun reset() {
        pos1 = null
        pos2 = null
        worldKey = null
        facing = Direction.NORTH
        isReady = false
    }

    // Paper uses OutlinerUtil.showOutline(player, p1, p2) with Bukkit particles.
    // No direct Fabric server equivalent; particle support would require ServerLevel.sendParticles().
    // drawBox() is intentionally absent.

    fun selectionBox(): AABB? {
        val p1 = pos1 ?: return null
        val p2 = pos2 ?: return null
        val minX = minOf(p1.x, p2.x).toDouble()
        val minY = minOf(p1.y, p2.y).toDouble()
        val minZ = minOf(p1.z, p2.z).toDouble()
        val maxX = (maxOf(p1.x, p2.x) + 1).toDouble()
        val maxY = (maxOf(p1.y, p2.y) + 1).toDouble()
        val maxZ = (maxOf(p1.z, p2.z) + 1).toDouble()
        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun contains(pos: BlockPos): Boolean {
        val p1 = pos1 ?: return false
        val p2 = pos2 ?: return false
        val minX = minOf(p1.x, p2.x); val maxX = maxOf(p1.x, p2.x)
        val minY = minOf(p1.y, p2.y); val maxY = maxOf(p1.y, p2.y)
        val minZ = minOf(p1.z, p2.z); val maxZ = maxOf(p1.z, p2.z)
        return pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ
    }

    fun region(): RegionResult? {
        val p1 = pos1 ?: return null
        val p2 = pos2 ?: return null
        val minX = minOf(p1.x, p2.x); val maxX = maxOf(p1.x, p2.x)
        val minY = minOf(p1.y, p2.y); val maxY = maxOf(p1.y, p2.y)
        val minZ = minOf(p1.z, p2.z); val maxZ = maxOf(p1.z, p2.z)
        val deltaX = maxX - minX + 1
        val deltaZ = maxZ - minZ + 1
        val width = maxOf(deltaX, deltaZ)
        val height = maxY - minY + 1
        return RegionResult(
            minX, minY, minZ, maxX, maxY, maxZ,
            width, height, deltaX, deltaZ
        )
    }

    data class RegionResult(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val width: Int, val height: Int,
        val deltaX: Int, val deltaZ: Int,
    )

    // Fabric server start
    fun generateDisplayData(ownerId: UUID): DisplayData {
        val r = requireNotNull(region()) { "region is null" }
        val wk = requireNotNull(worldKey) { "worldKey is null" }
        return DisplayData(
            id = UUID.randomUUID(),
            ownerId = ownerId,
            worldKey = wk,
            pos1 = BlockPos(r.minX, r.minY, r.minZ),
            pos2 = BlockPos(r.maxX, r.maxY, r.maxZ),
            width = r.width,
            height = r.height,
            facing = facing,
        )
    }
    // Fabric server end
    // Paper end
}
