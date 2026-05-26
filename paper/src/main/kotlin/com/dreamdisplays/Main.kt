package com.dreamdisplays

import com.dreamdisplays.managers.StorageManager
import com.dreamdisplays.meta.Scheduler
import com.dreamdisplays.registrar.ChannelRegistrar.registerChannels
import com.dreamdisplays.registrar.CommandRegistrar
import com.dreamdisplays.registrar.ListenerRegistrar.registerListeners
import com.dreamdisplays.registrar.SchedulerRegistrar.runRepeatingTasks
import com.github.zafarkhaja.semver.Version
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.inotsleep.utils.logging.LoggingManager.log
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin
import org.jspecify.annotations.NullMarked

/**
 * Entry point of plugin.
 *
 * `Paper` implementation.
 */
@Suppress("UnstableApiUsage")
@NullMarked class Main : JavaPlugin() {
    lateinit var storage: StorageManager

    /** Captures the plugin instance, loads config, and registers Brigadier commands before any worlds load. */
    override fun onLoad() {
        instance = this
        Companion.config = Config(this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(CommandRegistrar.buildDisplayCommand(), "Main Dream Displays command")
        }
    }

    /** Standard Bukkit hook, delegates to [doEnable] so reload commands can reuse the logic. */
    override fun onEnable() {
        doEnable()
    }

    /** Standard Bukkit hook, delegates to [doDisable]. */
    override fun onDisable() {
        doDisable()
    }

    /** Initializes scheduler, storage, listeners, channels, and metrics. Safe to call from a reload. */
    fun doEnable() {
        @Suppress("DEPRECATION")
        log("[Dream Displays] Enabling Dream Displays ${description.version}...")

        Scheduler.init(this)

        storage = StorageManager(this)

        registerListeners(this)
        registerChannels(this)
        runRepeatingTasks(this)

        Metrics(this, 26488)
    }

    /** Persists state and tears down resources. Safe to call from a reload. */
    fun doDisable() {
        log("[Dream Displays] Disabling Dream Displays ${pluginMeta.version}...")
        storage.onDisable()
    }

    companion object {
        lateinit var config: Config
        var modVersion: Version? = null
        var pluginLatestVersion: String? = null

        /** Returns the singleton plugin instance. */
        fun getInstance(): Main = instance

        /** Forces Bukkit to disable this plugin (used when fatal startup errors occur). */
        fun disablePlugin() {
            instance.server.pluginManager.disablePlugin(instance)
        }

        private lateinit var instance: Main
    }
}
