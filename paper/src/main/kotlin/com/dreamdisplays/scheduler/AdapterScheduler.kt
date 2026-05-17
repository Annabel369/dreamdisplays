package com.dreamdisplays.scheduler

import org.bukkit.plugin.Plugin
import org.jspecify.annotations.NullMarked

/**
 * An adapter interface for scheduling asynchronous tasks for different server implementations.
 *
 * `Paper` implementation.
 */
@NullMarked
interface AdapterScheduler {
    fun runRepeatingAsync(plugin: Plugin, delayTicks: Long, intervalTicks: Long, task: Runnable)
}
