package com.dreamdisplays.server.managers

import com.dreamdisplays.server.datatypes.StateData
import com.dreamdisplays.server.datatypes.SyncData
import com.dreamdisplays.server.utils.net.PacketUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages sync (playback state) for displays server-side.
 *
 * `Fabric server` implementation.
 */
object StateManager {
    // Paper start
    private val playStates: MutableMap<UUID, StateData> = ConcurrentHashMap()
    private val lastSyncBroadcast: MutableMap<UUID, Long> = ConcurrentHashMap()

    private const val SYNC_MIN_INTERVAL_MS = 250L

    fun processSyncPacket(packet: SyncData, player: ServerPlayer, server: MinecraftServer) {
        val displayId = packet.id ?: return
        val data = DisplayManager.getDisplayData(displayId)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(displayId)
            lastSyncBroadcast.remove(displayId)
            return
        }

        if (data == null) {
            playStates.remove(displayId)
            return
        }

        // Fabric server start
        if (data.ownerId != player.uuid) return
        // Fabric server end

        if (packet.currentTime < 0 || packet.limitTime < 0
            || packet.currentTime > 24L * 60 * 60 * 1_000_000_000L
        ) return

        val state = playStates.computeIfAbsent(displayId) { id -> StateData(id) }
        state.update(packet)
        data.duration = packet.limitTime

        val now = System.currentTimeMillis()
        val lastBroadcast = lastSyncBroadcast[displayId] ?: 0L
        if (now - lastBroadcast < SYNC_MIN_INTERVAL_MS) return
        lastSyncBroadcast[displayId] = now

        // Fabric server start
        val receivers = DisplayManager.getReceivers(data, server)
            .filter { it.uuid != player.uuid }
        // Fabric server end

        PacketUtil.sendSync(receivers, packet.copy(id = displayId))
    }

    fun sendSyncPacket(id: UUID?, player: ServerPlayer) {
        val displayId = id ?: return
        val state = playStates[displayId] ?: return
        val display = DisplayManager.getDisplayData(displayId)
        val packet = state.createPacket(display)
        PacketUtil.sendSync(listOf(player), packet)
        // Paper end
    }
}
