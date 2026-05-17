package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.managers.SelectionManager
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import kotlin.math.abs

// TODO: for removing in stable 1.7.0
object CreateCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.entity as? ServerPlayer
            ?: return sendConsoleError(ctx, "This command can only be used by a player.")
        val config = Server.config

        val sel = SelectionManager.selectionPoints[player.uuid]
            ?: return MessageUtil.sendMessage(player, "noDisplayTerritories").let { 0 }

        if (!sel.isReady || sel.pos1 == null || sel.pos2 == null) {
            MessageUtil.sendMessage(player, "noDisplayTerritories")
            return 0
        }

        // Validate region
        val region = sel.region() ?: run {
            MessageUtil.sendMessage(player, "noDisplayTerritories")
            return 0
        }

        val facing = sel.facing
        val deltaX = region.deltaX
        val deltaZ = region.deltaZ

        // Check that depth is 1 (the face must align with one axis being 1 block deep)
        val faceModX = if (facing == net.minecraft.core.Direction.EAST || facing == net.minecraft.core.Direction.WEST) 1 else 0
        val faceModZ = if (facing == net.minecraft.core.Direction.NORTH || facing == net.minecraft.core.Direction.SOUTH) 1 else 0

        if (deltaX != abs(faceModX) && deltaZ != abs(faceModZ)) {
            MessageUtil.sendMessage(player, "structureWrongDepth")
            return 0
        }

        val width = region.width
        val height = region.height

        if (height < config.settings.minHeight || width < config.settings.minWidth) {
            MessageUtil.sendMessage(player, "structureTooSmall")
            return 0
        }
        if (height > config.settings.maxHeight || width > config.settings.maxWidth) {
            MessageUtil.sendMessage(player, "structureTooLarge")
            return 0
        }

        if (region.maxY > 2047) {
            MessageUtil.sendMessage(player, "displayTooHigh")
            return 0
        }
        if (region.minY < -2048) {
            MessageUtil.sendMessage(player, "displayTooLow")
            return 0
        }

        // Verify all blocks are the base material
        val worldKey = sel.worldKey ?: run {
            MessageUtil.sendMessage(player, "noDisplay")
            return 0
        }
        val level = RegionUtil.getLevelByKey(ctx.source.server, worldKey)
        if (level != null) {
            val baseMaterialKey = config.settings.baseMaterial
            run {
                var allCorrect = true
                outer@ for (x in region.minX..region.maxX) {
                    for (y in region.minY..region.maxY) {
                        for (z in region.minZ..region.maxZ) {
                            val blockState = level.getBlockState(net.minecraft.core.BlockPos(x, y, z))
                            val blockKey = BuiltInRegistries.BLOCK.getKey(blockState.block)?.toString()
                            if (blockKey != baseMaterialKey) {
                                allCorrect = false
                                break@outer
                            }
                        }
                    }
                }
                if (!allCorrect) {
                    MessageUtil.sendMessage(player, "wrongStructure")
                    return 0
                }
            }
        }

        // Check overlap
        if (DisplayManager.isOverlaps(sel)) {
            MessageUtil.sendMessage(player, "displayOverlap")
            return 0
        }

        val displayData = sel.generateDisplayData(player.uuid)
        SelectionManager.selectionPoints.remove(player.uuid)

        DisplayManager.register(displayData)
        Server.storage?.saveDisplay(displayData)

        val receivers = DisplayManager.getReceivers(displayData, ctx.source.server)
        PacketUtil.sendDisplayInfo(receivers, displayData)

        MessageUtil.sendMessage(player, "successfulCreation")
        return 1
    }

    private fun sendConsoleError(ctx: CommandContext<CommandSourceStack>, msg: String): Int {
        ctx.source.sendFailure(net.minecraft.network.chat.Component.literal(msg))
        return 0
    }
}
