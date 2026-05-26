package com.dreamdisplays.utils

import com.dreamdisplays.Main.Companion.config
import com.google.gson.Gson
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked

/**
 * Message utilities.
 *
 * `Paper` implementation.
 */
@NullMarked object MessageUtil {
    private val gson by lazy { Gson() }
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val gsonSerializer = GsonComponentSerializer.gson()

    /** Sends a localized message identified by [messageKey] to [sender], using their language when applicable. */
    fun sendMessage(sender: CommandSender?, messageKey: String) {
        val message = config.getMessageForPlayer(sender as? Player, messageKey)
        sendColoredMessage(sender, message)
    }

    /** Sends [message] to [sender], auto-detecting Component / legacy string / JSON forms. */
    fun sendColoredMessage(sender: CommandSender?, message: Any?) {
        if (sender == null || message == null) return
        when (message) {
            is Component -> sender.sendMessage(message)
            is String -> sender.sendMessage(legacySerializer.deserialize(message))
            else -> sender.sendMessage(gsonSerializer.deserialize(gson.toJson(message)))
        }
    }

    /** Sends an already-built `Adventure` [component] to [sender], silently ignoring nulls. */
    fun sendComponent(sender: CommandSender?, component: Component?) {
        if (sender == null || component == null) return
        sender.sendMessage(component)
    }
}
