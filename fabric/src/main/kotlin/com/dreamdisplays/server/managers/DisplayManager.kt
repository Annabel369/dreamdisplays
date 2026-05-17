package com.dreamdisplays.server.managers

import com.dreamdisplays.Server
import com.dreamdisplays.server.datatypes.DisplayData
import com.dreamdisplays.server.datatypes.SelectionData
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.RegionUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all displays in the server.
 *
 * `Fabric server` implementation.
 */
object DisplayManager {
    // Paper start
    private val displays: MutableMap<UUID, DisplayData> = ConcurrentHashMap()
    private val reportTime: MutableMap<UUID, Long> = ConcurrentHashMap()

    fun getDisplayData(id: UUID?): DisplayData? = displays[id]

    fun getDisplays(): List<DisplayData> = displays.values.toList()

    fun register(data: DisplayData) {
        displays[data.id] = data
    }

    fun register(list: List<DisplayData>) {
        list.forEach { displays[it.id] = it }
    }

    fun delete(id: UUID) {
        val data = displays.remove(id) ?: return
        // Fabric server start
        // Storage accessed via singleton instead of plugin instance
        Server.storage?.deleteDisplay(data)
    }
    // Fabric server end

    fun delete(data: DisplayData) {
        displays.remove(data.id)
        // Fabric server start
        Server.storage?.deleteDisplay(data)
        // Fabric server end
    }
    // Paper end

    // Fabric server start
    fun isContains(worldKey: String, blockPos: net.minecraft.core.BlockPos): DisplayData? {
        return displays.values.firstOrNull { display ->
            display.worldKey == worldKey &&
                    display.box.contains(blockPos.x.toDouble() + 0.5, blockPos.y.toDouble() + 0.5, blockPos.z.toDouble() + 0.5)
        }
    }

    fun isOverlaps(sel: SelectionData): Boolean {
        val selBox = sel.selectionBox() ?: return false
        val wk = sel.worldKey ?: return false
        return displays.values.any { display ->
            display.worldKey == wk && display.box.intersects(selBox)
        }
    }

    fun getReceivers(display: DisplayData, server: MinecraftServer): List<ServerPlayer> {
        return server.playerList.players.filter { player ->
            getWorldKey(player) == display.worldKey && player.blockPosition().isInRange(display)
        }
    }
    // Fabric server end

    // Paper start
    private fun net.minecraft.core.BlockPos.isInRange(display: DisplayData): Boolean {
        val maxRender = Server.config.settings.maxRenderDistance
        val clampedX = x.coerceIn(display.minX, display.maxX)
        val clampedY = y.coerceIn(display.minY, display.maxY)
        val clampedZ = z.coerceIn(display.minZ, display.maxZ)
        val dx = x - clampedX
        val dy = y - clampedY
        val dz = z - clampedZ
        return dx * dx + dy * dy + dz * dz <= maxRender * maxRender
    }

    fun sendUpdate(display: DisplayData, players: List<ServerPlayer>) {
        PacketUtil.sendDisplayInfo(players, display)
    }

    fun updateAllDisplays(server: MinecraftServer) {
        displays.values.forEach { display ->
            val receivers = getReceivers(display, server)
            if (receivers.isNotEmpty()) {
                sendUpdate(display, receivers)
            }
        }
    }

    fun validateAndCleanup(server: MinecraftServer): List<UUID> {
        val config = Server.config
        // Fabric server start
        val baseMaterialKey = config.settings.baseMaterial
        // Fabric server end
        val invalidDisplays = mutableListOf<DisplayData>()

        displays.values.forEach { display ->
            // Fabric server start
            val level = RegionUtil.getLevelByKey(server, display.worldKey) ?: run {
                invalidDisplays.add(display)
                return@forEach
            }
            // Fabric server end

            var hasBaseMaterial = false
            outerLoop@ for (x in display.minX until display.maxX + 1) {
                for (y in display.minY until display.maxY + 1) {
                    for (z in display.minZ until display.maxZ + 1) {
                        // Fabric server start
                        val state = level.getBlockState(net.minecraft.core.BlockPos(x, y, z))
                        val regName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).toString()
                        // Fabric server end
                        if (regName == baseMaterialKey) {
                            hasBaseMaterial = true
                            break@outerLoop
                        }
                    }
                }
            }

            if (!hasBaseMaterial) {
                invalidDisplays.add(display)
            }
        }

        val removedUuids = mutableListOf<UUID>()
        invalidDisplays.forEach { display ->
            displays.remove(display.id)
            Server.storage?.deleteDisplay(display)
            removedUuids.add(display.id)
        }

        return removedUuids
    }

    fun report(id: UUID, player: ServerPlayer, server: MinecraftServer) {
        val displayData = displays[id] ?: return
        val lastReport = reportTime.getOrPut(id) { 0L }
        val config = Server.config

        if (System.currentTimeMillis() - lastReport < config.settings.reportCooldown) {
            com.dreamdisplays.server.utils.MessageUtil.sendMessage(player, "reportTooQuickly")
            return
        }

        reportTime[id] = System.currentTimeMillis()

        if (config.settings.webhookUrl.isEmpty()) return

        // Fabric server start
        val ownerName = server.playerList.players.find { it.uuid == displayData.ownerId }?.name?.string ?: "Unknown"
        val worldName = displayData.worldKey
        val locationStr = "$worldName (x=${displayData.minX}, y=${displayData.minY}, z=${displayData.minZ})"
        // Fabric server end

        java.util.concurrent.CompletableFuture.runAsync {
            runCatching {
                com.dreamdisplays.server.utils.ReporterUtil.sendReport(
                    locationStr,
                    displayData.url,
                    displayData.id,
                    player.name.string,
                    ownerName,
                    config.settings.webhookUrl
                )
                server.execute {
                    com.dreamdisplays.server.utils.MessageUtil.sendMessage(player, "reportSent")
                }
            }.onFailure { e ->
                server.execute {
                    com.dreamdisplays.server.utils.MessageUtil.sendMessage(player, "reportFailed")
                }
            }
        }
    }

    fun save(saveDisplay: (DisplayData) -> Unit) {
        displays.values.forEach(saveDisplay)
    }
    // Paper end

    // Fabric server start
    private fun getWorldKey(player: ServerPlayer): String {
        return player.level().dimension().identifier().toString()
    }
    // Fabric server end
}
