package com.dreamdisplays.client.popout

import com.dreamdisplays.api.DisplayId
import com.dreamdisplays.media.api.VideoFrameSink

interface PopoutManager {
    fun openWindow(displayId: DisplayId, config: WindowConfig = WindowConfig()): VideoFrameSink?
    fun openPip(displayId: DisplayId): VideoFrameSink?
    fun close(displayId: DisplayId)
    fun closeAll()

    fun isWindowOpen(displayId: DisplayId): Boolean
    fun isPipOpen(displayId: DisplayId): Boolean
    fun getPopoutSink(displayId: DisplayId): VideoFrameSink?

    fun on(listener: (PopoutEvent) -> Unit): AutoCloseable

    /** Publishes [event] to all [on] subscribers. Called by the popout surfaces themselves. */
    fun emit(event: PopoutEvent)
}
