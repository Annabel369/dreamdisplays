package com.dreamdisplays.server.listeners

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.SelectionManager
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

/**
 * Different from `Paper` implementation.
 *
 * Listener for player interactions to manage selection points for display creation.
 *
 * Players can set selection points by interacting with blocks while holding the designated selection material.
 * Left-clicking sets the first point, right-clicking sets the second point.
 * Sneaking and right-clicking resets the selection.
 *
 * `Fabric server` implementation.
 */
object SelectionListener {
    fun register() {
        // Fabric server start
        AttackBlockCallback.EVENT.register { player, world, hand, pos, direction ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (world !is ServerLevel) return@register InteractionResult.PASS

            val config = Server.config
            // Fabric server start
            val selMaterialKey = config.settings.selectionMaterial
            val heldItem = player.mainHandItem
            val heldItemKey = BuiltInRegistries.ITEM.getKey(heldItem.item)?.toString()
            if (heldItemKey != selMaterialKey) return@register InteractionResult.PASS

            val baseMaterialKey = config.settings.baseMaterial
            val blockState = world.getBlockState(pos)
            val blockKey = BuiltInRegistries.BLOCK.getKey(blockState.block)?.toString()
            if (blockKey != baseMaterialKey) return@register InteractionResult.PASS
            // Fabric server end

            val worldKey = RegionUtil.getLevelKey(world)
            val face = horizontalFaceFromDirection(direction)
            SelectionManager.setFirstPoint(player, pos, worldKey, face)

            InteractionResult.SUCCESS
        }

        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (world !is ServerLevel) return@register InteractionResult.PASS

            val config = Server.config
            // Fabric server start
            val selMaterialKey = config.settings.selectionMaterial
            val heldItem = player.mainHandItem
            val heldItemKey = BuiltInRegistries.ITEM.getKey(heldItem.item)?.toString()
            if (heldItemKey != selMaterialKey) return@register InteractionResult.PASS
            // Fabric server end

            val pos = hitResult.blockPos
            val worldKey = RegionUtil.getLevelKey(world)

            // Paper start
            if (player.isShiftKeyDown) {
                if (SelectionManager.selectionPoints.containsKey(player.uuid)) {
                    SelectionManager.resetSelection(player)
                    MessageUtil.sendMessage(player, "selectionClear")
                }
                return@register InteractionResult.SUCCESS
            }
            // Paper end

            // Fabric server start
            val baseMaterialKey = config.settings.baseMaterial
            val blockState = world.getBlockState(pos)
            val blockKey = BuiltInRegistries.BLOCK.getKey(blockState.block)?.toString()
            if (blockKey != baseMaterialKey) return@register InteractionResult.PASS
            // Fabric server end

            SelectionManager.setSecondPoint(player, pos, worldKey)
            InteractionResult.SUCCESS
        }
    }

    private fun horizontalFaceFromDirection(dir: net.minecraft.core.Direction): net.minecraft.core.Direction {
        return when (dir) {
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
            net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST -> dir
            else -> net.minecraft.core.Direction.NORTH
        }
    }
    // Fabric server end
}
