package com.dreamdisplays.server.utils

import com.dreamdisplays.Server
import com.google.gson.Gson
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.level.ServerPlayer

/**
 * Different from `Paper` implementation.
 *
 * Utility for sending formatted messages to players server-side.
 *
 * `Fabric server` implementation.
 */
object MessageUtil {
    private val gson = Gson()

    fun sendMessage(player: ServerPlayer?, key: String) {
        val config = Server.config
        val message = config.getMessageForPlayer(player, key)
        sendColoredMessage(player, message)
    }

    fun sendColoredMessage(player: ServerPlayer?, message: Any?) {
        if (player == null || message == null) return
        val component = toComponent(message)
        player.sendSystemMessage(component)
    }

    private fun toComponent(message: Any): Component {
        return when (message) {
            is String -> parseAmpersandLegacy(message)
            is Map<*, *> -> {
                runCatching {
                    val jsonElement = gson.toJsonTree(message)
                    ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, jsonElement)
                        .result()
                        .orElse(null)
                        ?: parseAmpersandLegacy(message.toString())
                }.getOrElse { parseAmpersandLegacy(message.toString()) }
            }
            else -> parseAmpersandLegacy(message.toString())
        }
    }

    /** Converts `&` color codes to vanilla text components. */
    private fun parseAmpersandLegacy(text: String): Component {
        return Component.literal(stripColorCodes(text))
    }

    private fun stripColorCodes(text: String): String {
        return text.replace(Regex("&[0-9a-fA-FrRlLoOnNmMkK]"), "")
    }
}
