package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.utils.MessageUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object HelpCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
        val config = Server.config

        fun line(key: String) {
            val msg = config.getMessageForPlayer(player, key)
            MessageUtil.sendColoredMessage(player ?: return, msg)
        }

        val header = config.getMessageForPlayer(player, "displayHelpHeader")
        MessageUtil.sendColoredMessage(player ?: run {
            ctx.source.sendSystemMessage(net.minecraft.network.chat.Component.literal("D | Help"))
            return 1
        }, header)

        line("displayHelpCreate")
        line("displayHelpVideo")
        line("displayHelpInfo")
        line("displayHelpDelete")
        line("displayHelpList")
        line("displayHelpStats")
        line("displayHelpReload")
        line("displayHelpOn")
        line("displayHelpOff")
        line("displayHelpHelp")
        return 1
    }
}
