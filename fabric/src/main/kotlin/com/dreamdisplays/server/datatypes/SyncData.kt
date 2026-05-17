package com.dreamdisplays.server.datatypes

import java.util.*

/**
 * Synchronization data for a display.
 *
 * `Fabric server` implementation.
 *
 * @param id Unique identifier for the display.
 * @param isSync Boolean indicating if the display is synchronized.
 * @param currentState Current playback state (e.g. playing or paused).
 * @param currentTime Current playback time.
 * @param limitTime Limit time for the playback.
 *
 */
data class SyncData(
    // Paper start
    val id: UUID?,
    val isSync: Boolean,
    val currentState: Boolean,
    val currentTime: Long,
    val limitTime: Long,
    // Paper end
)
