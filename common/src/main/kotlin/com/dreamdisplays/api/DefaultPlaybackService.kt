package com.dreamdisplays.api

import com.dreamdisplays.display.DisplayManager
import com.dreamdisplays.display.toRuntimeState
import kotlin.time.Duration

/** Default [PlaybackService] backed by [DisplayManager] and the [com.dreamdisplays.display.DisplayScreen] API. */
class DefaultPlaybackService : PlaybackService {

    override fun play(displayId: DisplayId) {
        DisplayManager.screens[displayId.uuid]?.setPaused(false)
    }

    override fun pause(displayId: DisplayId) {
        DisplayManager.screens[displayId.uuid]?.setPaused(true)
    }

    override fun stop(displayId: DisplayId) {
        val screen = DisplayManager.screens[displayId.uuid] ?: return
        DisplayManager.unregisterScreen(screen)
    }

    override fun seek(displayId: DisplayId, position: Duration) {
        DisplayManager.screens[displayId.uuid]?.seekToMillis(position.inWholeMilliseconds)
    }

    override fun setVolume(displayId: DisplayId, volume: Float) {
        DisplayManager.screens[displayId.uuid]?.let { it.volume = volume }
    }

    override fun mute(displayId: DisplayId, muted: Boolean) {
        DisplayManager.screens[displayId.uuid]?.mute(muted)
    }

    override fun getState(displayId: DisplayId): DisplayRuntimeState =
        DisplayManager.screens[displayId.uuid]?.toRuntimeState() ?: DisplayRuntimeState.OutOfRange

    override fun restart(displayId: DisplayId) {
        val screen = DisplayManager.screens[displayId.uuid] ?: return
        val url = screen.videoUrl ?: return
        screen.loadVideo(url, screen.lang ?: "")
    }
}
