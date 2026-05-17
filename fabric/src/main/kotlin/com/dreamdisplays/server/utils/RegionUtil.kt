package com.dreamdisplays.server.utils

import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel

/**
 * Different from `Paper` implementation.
 *
 * Utilities for world / region resolution on the server.
 *
 * `Fabric server` implementation.
 */
object RegionUtil {
    /**
     * Resolves a [ServerLevel] from a world key string like "minecraft:overworld".
     */
    fun getLevelByKey(server: MinecraftServer, worldKey: String): ServerLevel? {
        val rl = runCatching { Identifier.parse(worldKey) }.getOrNull() ?: return null
        val key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl)
        return server.getLevel(key)
    }

    /**
     * Returns the dimension key string (e.g. "minecraft:overworld") for a given [ServerLevel].
     */
    fun getLevelKey(level: ServerLevel): String {
        return level.dimension().identifier().toString()
    }
}
