package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.utils.net.ServerPacketHandler
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.MessageUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object OnCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>, targetName: String? = null): Int {
        val self = ctx.source.entity as? ServerPlayer
        val config = Server.config

        val target: ServerPlayer = if (targetName == null) {
            self ?: run {
                ctx.source.sendFailure(Component.literal("This command must be used in-game or with a player argument."))
                return 0
            }
        } else {
            ctx.source.server.playerList.getPlayerByName(targetName) ?: run {
                val msg = config.getMessageForPlayer(self, "displayTargetNotFound") as? String ?: "Player not found: %s"
                if (self != null) MessageUtil.sendColoredMessage(self, String.format(msg, targetName))
                else ctx.source.sendFailure(Component.literal(String.format(msg, targetName)))
                return 0
            }
        }

        val selfTarget = self?.uuid == target.uuid

        if (!selfTarget && (self == null || !ServerPacketHandler.isOpLevel2(self))) {
            if (self != null) MessageUtil.sendMessage(self, "displayCommandMissingPermission")
            else ctx.source.sendFailure(Component.literal("Missing permission."))
            return 0
        }

        if (PlayerManager.isDisplaysEnabled(target)) {
            MessageUtil.sendMessage(target, "display.already-enabled")
            if (!selfTarget) {
                val msg = config.getMessageForPlayer(self, "display.already-enabled.target") as? String
                if (msg != null) MessageUtil.sendColoredMessage(self, String.format(msg, target.name.string))
            }
            return 1
        }

        PlayerManager.setDisplaysEnabled(target, true)
        PacketUtil.sendDisplayEnabled(target, true)
        MessageUtil.sendMessage(target, "display.enabled")
        if (!selfTarget) {
            val msg = config.getMessageForPlayer(self, "display.enabled.target") as? String
            if (msg != null) MessageUtil.sendColoredMessage(self, String.format(msg, target.name.string))
        }
        return 1
    }
}
