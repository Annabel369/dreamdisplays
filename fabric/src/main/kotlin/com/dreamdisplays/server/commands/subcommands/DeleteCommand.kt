package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.net.ServerPacketHandler
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

// TODO: for removing in stable 1.7.0
object DeleteCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
            ?: return ctx.source.sendFailure(net.minecraft.network.chat.Component.literal("Players only.")).let { 0 }

        val config = Server.config
        val baseMaterialKey = config.settings.baseMaterial
        val baseMaterial = runCatching {
            BuiltInRegistries.BLOCK.get(Identifier.parse(baseMaterialKey))
        }.getOrNull()

        val targetPos = getTargetBlockPos(player) ?: run {
            MessageUtil.sendMessage(player, "noDisplay")
            return 0
        }

        val worldKey = RegionUtil.getLevelKey(player.level())

        // Optionally verify block type
        if (baseMaterial != null) {
            val blockAtTarget = player.level().getBlockState(targetPos)
            if (blockAtTarget.block != baseMaterial) {
                MessageUtil.sendMessage(player, "noDisplay")
                return 0
            }
        }

        val data = DisplayManager.isContains(worldKey, targetPos)
            ?: return MessageUtil.sendMessage(player, "noDisplay").let { 0 }

        if (data.ownerId != player.uuid && !ServerPacketHandler.isOpLevel2(player)) {
            MessageUtil.sendMessage(player, "displayCommandMissingPermission")
            return 0
        }

        val receivers = DisplayManager.getReceivers(data, ctx.source.server)
        DisplayManager.delete(data)
        PacketUtil.sendDelete(receivers, data.id)
        MessageUtil.sendMessage(player, "displayDeleted")
        return 1
    }

    private fun getTargetBlockPos(player: ServerPlayer): net.minecraft.core.BlockPos? {
        val level = player.level()
        val eyePos = player.eyePosition
        val lookVec = player.lookAngle

        // Ray cast up to 32 blocks
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
