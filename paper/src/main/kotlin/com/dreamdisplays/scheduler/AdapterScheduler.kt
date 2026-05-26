package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import org.jspecify.annotations.NullMarked

/**
 * An adapter interface for scheduling asynchronous tasks for different server implementations.
 *
 * `Paper` implementation.
 */
@NullMarked interface AdapterScheduler {
    /** Schedules [task] to run repeatedly on an async thread with the given delay and interval (in ticks). */
    fun runRepeatingAsync(plugin: Plugin, delayTicks: Long, intervalTicks: Long, task: Runnable)
}
