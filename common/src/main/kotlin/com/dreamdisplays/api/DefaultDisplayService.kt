package com.dreamdisplays.api

import com.dreamdisplays.display.DisplayManager
import com.dreamdisplays.display.toDisplay

/**
 * Default [DisplayService] backed by [DisplayManager].
 * Events are dispatched via the [DisplayManager] listener bus wired in [DisplayManager.addListener].
 */
class DefaultDisplayService : DisplayService {

    override fun getDisplay(id: DisplayId): Display? =
        DisplayManager.screens[id.uuid]?.toDisplay()

    override fun listDisplays(): List<Display> =
        DisplayManager.getScreens().map { it.toDisplay() }

    override fun updateSettings(id: DisplayId, settings: DisplaySettings) {
        val screen = DisplayManager.screens[id.uuid] ?: return
        screen.volume = settings.volume
        screen.quality = settings.quality
        screen.brightness = settings.brightness
        screen.mute(settings.muted)
        screen.setPaused(settings.paused)
        screen.renderDistance = settings.renderDistance
        screen.isSync = settings.syncEnabled
        val override = settings.urlOverride
        if (override != null) screen.loadVideo(override, settings.audioTrackName ?: "")
    }

    override fun setUrl(id: DisplayId, url: String?) {
        val screen = DisplayManager.screens[id.uuid] ?: return
        if (url.isNullOrBlank()) return
        screen.loadVideo(url, screen.lang ?: "")
    }

    override fun on(listener: (DisplayEvent) -> Unit): AutoCloseable =
        DisplayManager.addListener(listener)
}
