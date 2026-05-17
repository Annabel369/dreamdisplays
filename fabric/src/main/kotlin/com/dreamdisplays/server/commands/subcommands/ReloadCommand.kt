package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.utils.MessageUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object ReloadCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer

        try {
            Server.config.reload()
            if (player != null) {
                MessageUtil.sendMessage(player, "configReloaded")
                MessageUtil.sendMessage(player, "configReloadSummary")
            } else {
                ctx.source.sendSystemMessage(Component.literal("DreamDisplays config reloaded."))
            }
        } catch (e: Exception) {
            if (player != null) {
                MessageUtil.sendMessage(player, "configReloadFailed")
            } else {
                ctx.source.sendFailure(Component.literal("Failed to reload config: ${e.message}"))
            }
        }
        return 1
    }
}
