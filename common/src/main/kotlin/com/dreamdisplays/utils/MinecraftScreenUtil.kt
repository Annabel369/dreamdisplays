package com.dreamdisplays.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object MinecraftScreenUtil {
    fun currentScreen(mc: Minecraft): Screen? {
        runCatching {
            val gui = Minecraft::class.java.getField("gui").get(mc)
            return gui.javaClass.getMethod("screen").invoke(gui) as? Screen
        }

        return runCatching {
            Minecraft::class.java.getField("screen").get(mc) as? Screen
        }.getOrNull()
    }

    fun setScreen(mc: Minecraft, screen: Screen?) {
        runCatching {
            Minecraft::class.java.getMethod("setScreenAndShow", Screen::class.java).invoke(mc, screen)
            return
        }

        Minecraft::class.java.getMethod("setScreen", Screen::class.java).invoke(mc, screen)
    }
}
