package com.dreamdisplays.server.datatypes

import java.util.*

/**
 * Class to manage the state data of a display.
 *
 * `Fabric server` implementation.
 *
 * @property id The unique identifier of the display.
 * @property paused Boolean indicating if the display is paused.
 * @property lastReportedTime The last reported time of the display.
 * @property lastReportedTimestamp The timestamp of the last report.
 * @property limitTime The limit time for the display.
 *
 * @param id The unique identifier of the display.
 *
 * @throws IllegalStateException if the display data is not found for the given ID.
 *
 */
class StateData(private val id: UUID) {
    // Paper start
    private var paused = false
    private var lastReportedTime: Long = 0
    private var lastReportedTimestamp: Long = 0
    private var limitTime: Long = 0

    fun update(packet: SyncData) {
        paused = packet.currentState
        lastReportedTime = packet.currentTime
        lastReportedTimestamp = System.nanoTime()
        limitTime = packet.limitTime
    }

    // Fabric server start
    fun createPacket(display: DisplayData?): SyncData {
        val nanos = System.nanoTime()
        val currentTime = if (paused) {
            lastReportedTime
        } else {
            lastReportedTime + (nanos - lastReportedTimestamp)
        }

        if (limitTime == 0L) {
            display?.duration?.let { limitTime = it }
        }

        val time = if (limitTime > 0) currentTime % limitTime else currentTime
        return SyncData(id, true, paused, time, limitTime)
    }
    // Fabric server end
    // Paper end
}
