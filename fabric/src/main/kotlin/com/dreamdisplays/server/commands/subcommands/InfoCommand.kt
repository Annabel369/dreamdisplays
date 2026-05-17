package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object InfoCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
            ?: return ctx.source.sendFailure(net.minecraft.network.chat.Component.literal("Players only.")).let { 0 }

        val worldKey = RegionUtil.getLevelKey(player.level())
        val targetPos = getTargetBlockPos(player)
            ?: return MessageUtil.sendMessage(player, "noDisplay").let { 0 }

        val data = DisplayManager.isContains(worldKey, targetPos)
            ?: return MessageUtil.sendMessage(player, "noDisplay").let { 0 }

        val config = Server.config
        val server = ctx.source.server

        fun text(key: String): String =
            config.getMessageForPlayer(player, key) as? String ?: key

        fun format(key: String, vararg args: String): String {
            val template = text(key)
            var result = template
            args.forEachIndexed { index, value -> result = result.replace("{$index}", value) }
            return result
        }

        val ownerName = server.playerList.players.find { it.uuid == data.ownerId }?.name?.string ?: text("displayInfoUnknownOwner")
        val worldName = data.worldKey
        val displayUrl = data.url.ifBlank { text("displayInfoUnavailableUrl") }
        val displayLang = data.lang.ifBlank { text("displayInfoAutoLang") }
        val duration = data.duration?.toString() ?: text("displayInfoUnknownDuration")

        MessageUtil.sendColoredMessage(player, text("displayInfoHeader"))
        MessageUtil.sendColoredMessage(player, format("displayInfoOwnerLine", ownerName, data.ownerId.toString()))
        MessageUtil.sendColoredMessage(player, format("displayInfoUuidLine", data.id.toString()))
        MessageUtil.sendColoredMessage(
            player,
            format(
                "displayInfoPositionLine",
                worldName,
                data.pos1.x.toString(), data.pos1.y.toString(), data.pos1.z.toString(),
                data.pos2.x.toString(), data.pos2.y.toString(), data.pos2.z.toString()
            )
        )
        MessageUtil.sendColoredMessage(
            player,
            format(
                "displayInfoStateLine",
                data.width.toString(),
                data.height.toString(),
                data.facing.name,
                data.isSync.toString()
            )
        )
        MessageUtil.sendColoredMessage(
            player,
            format("displayInfoMediaLine", displayLang, duration, displayUrl)
        )
        return 1
    }

    private fun getTargetBlockPos(player: ServerPlayer): net.minecraft.core.BlockPos? {
        val level = player.level()
        val eyePos = player.eyePosition
        val lookVec = player.lookAngle
        for (i in 1..32) {
            val checkPos = net.minecraft.core.BlockPos.containing(
                eyePos.x + lookVec.x * i,
                eyePos.y + lookVec.y * i,
                eyePos.z + lookVec.z * i
            )
            val state = level.getBlockState(checkPos)
            if (!state.isAir) return checkPos
        }
        return null
    }
}
