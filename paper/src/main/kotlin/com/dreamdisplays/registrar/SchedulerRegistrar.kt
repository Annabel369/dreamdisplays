package com.dreamdisplays.registrar

import com.dreamdisplays.Main
import com.dreamdisplays.managers.DisplayManager
import com.dreamdisplays.managers.StateManager
import com.dreamdisplays.meta.Updater.checkForUpdates
import com.dreamdisplays.scheduler.ProviderScheduler

/**
 * Manages the registration of scheduled tasks.
 *
 * `Paper` implementation.
 */
object SchedulerRegistrar {
    private const val TICKS_PER_SECOND = 20L
    private const val DISPLAY_UPDATE_INTERVAL_TICKS = 1L * TICKS_PER_SECOND
    private const val UPDATE_CHECK_INTERVAL_TICKS = 60L * 60L * TICKS_PER_SECOND

    /** Schedules the periodic display update tick and, when enabled, the hourly update check. */
    fun runRepeatingTasks(plugin: Main) {
        ProviderScheduler.adapter.runRepeatingAsync(
            plugin,
            DISPLAY_UPDATE_INTERVAL_TICKS,
            DISPLAY_UPDATE_INTERVAL_TICKS
        ) {
            DisplayManager.updateAllDisplays()
            StateManager.tickBroadcast()
        }
        val settings = Main.config.settings
        if (settings.updatesEnabled) {
            ProviderScheduler.adapter.runRepeatingAsync(
                plugin,
                TICKS_PER_SECOND,
                UPDATE_CHECK_INTERVAL_TICKS
            ) {
                checkForUpdates(
                    settings.repoOwner,
                    settings.repoName
                )
            }
        }
    }
}
