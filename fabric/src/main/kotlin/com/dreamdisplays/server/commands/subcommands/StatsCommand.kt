package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.utils.MessageUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object StatsCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
        val config = Server.config

        fun msg(key: String): String = config.getMessageForPlayer(player, key) as? String ?: key
        fun format(key: String, vararg values: Any): String {
            val template = msg(key)
            return runCatching { String.format(template, *values) }.getOrElse { template }
        }

        val versions = PlayerManager.getVersions()
        val counts = versions.values
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .toSortedMap()

        if (player != null) {
            MessageUtil.sendMessage(player, "displayStatsHeader")
            for ((version, count) in counts) {
                MessageUtil.sendColoredMessage(player, format("displayStatsEntry", version, count))
            }
            val total = counts.values.sum()
            MessageUtil.sendColoredMessage(player, format("displayStatsTotal", total))
        } else {
            ctx.source.sendSystemMessage(Component.literal(msg("displayStatsHeader")))
            for ((version, count) in counts) {
                ctx.source.sendSystemMessage(Component.literal(format("displayStatsEntry", version, count)))
            }
            val total = counts.values.sum()
            ctx.source.sendSystemMessage(Component.literal(format("displayStatsTotal", total)))
        }

        return 1
    }
}
