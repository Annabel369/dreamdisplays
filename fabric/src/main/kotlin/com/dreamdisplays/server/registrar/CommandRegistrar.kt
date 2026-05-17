package com.dreamdisplays.server.registrar

import com.dreamdisplays.Server
import com.dreamdisplays.server.commands.subcommands.CreateCommand
import com.dreamdisplays.server.commands.subcommands.DeleteCommand
import com.dreamdisplays.server.commands.subcommands.HelpCommand
import com.dreamdisplays.server.commands.subcommands.InfoCommand
import com.dreamdisplays.server.commands.subcommands.ListCommand
import com.dreamdisplays.server.commands.subcommands.OffCommand
import com.dreamdisplays.server.commands.subcommands.OnCommand
import com.dreamdisplays.server.commands.subcommands.ReloadCommand
import com.dreamdisplays.server.commands.subcommands.ServerVideoCommand
import com.dreamdisplays.server.commands.subcommands.StatsCommand
import com.dreamdisplays.server.utils.net.ServerPacketHandler
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

// TODO: for removing in stable 1.7.0
object CommandRegistrar {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val displayCmd = Commands.literal("display")
                .executes { ctx ->
                    HelpCommand.execute(ctx)
                    Command.SINGLE_SUCCESS
                }
                .then(helpNode())
                .then(createNode())
                .then(deleteNode())
                .then(infoNode())
                .then(listNode())
                .then(statsNode())
                .then(reloadNode())
                .then(videoNode())
                .then(toggleNode("on"))
                .then(toggleNode("off"))
                .build()

            dispatcher.root.addChild(displayCmd)
        }
    }

    private fun helpNode() = Commands.literal("help")
        .executes { ctx ->
            HelpCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun createNode() = Commands.literal("create")
        .executes { ctx ->
            CreateCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun deleteNode() = Commands.literal("delete")
        .requires { source ->
            val player = source.entity as? ServerPlayer
            player == null || ServerPacketHandler.isOpLevel2(player)
        }
        .executes { ctx ->
            DeleteCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun infoNode() = Commands.literal("info")
        .executes { ctx ->
            InfoCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun statsNode() = Commands.literal("stats")
        .requires { source ->
            val player = source.entity as? ServerPlayer
            player == null || ServerPacketHandler.isOpLevel2(player)
        }
        .executes { ctx ->
            StatsCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun reloadNode() = Commands.literal("reload")
        .requires { source ->
            val player = source.entity as? ServerPlayer
            player == null || ServerPacketHandler.isOpLevel2(player)
        }
        .executes { ctx ->
            ReloadCommand.execute(ctx)
            Command.SINGLE_SUCCESS
        }

    private fun videoNode() = Commands.literal("video")
        .then(
            Commands.argument("url_and_lang", StringArgumentType.greedyString())
                .suggests { _, builder ->
                    if (builder.remaining.contains(' ')) {
                        val prefix = builder.remaining.substringAfterLast(' ')
                        getLanguageSuggestions()
                            .filter { it.startsWith(prefix, ignoreCase = true) }
                            .forEach { builder.suggest(builder.remaining.substringBeforeLast(' ') + " " + it) }
                    }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val urlAndLang = StringArgumentType.getString(ctx, "url_and_lang")
                    ServerVideoCommand.execute(ctx, urlAndLang)
                    Command.SINGLE_SUCCESS
                }
        )

    private fun listNode(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("list")
            .requires { source ->
                val player = source.entity as? ServerPlayer
                player == null || ServerPacketHandler.isOpLevel2(player)
            }
            .executes { ctx ->
                ListCommand.execute(ctx)
                Command.SINGLE_SUCCESS
            }
            .then(
                Commands.argument("filter", StringArgumentType.word())
                    .suggests { _, builder ->
                        listOf("mine", "world", "owner", "sync").forEach { builder.suggest(it) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val filter = StringArgumentType.getString(ctx, "filter")
                        ListCommand.execute(ctx, filter = filter)
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.argument("value", StringArgumentType.word())
                            .executes { ctx ->
                                val filter = StringArgumentType.getString(ctx, "filter")
                                val value = StringArgumentType.getString(ctx, "value")
                                ListCommand.execute(ctx, filter = filter, value = value)
                                Command.SINGLE_SUCCESS
                            }
                            .then(
                                Commands.argument("page", StringArgumentType.word())
                                    .executes { ctx ->
                                        val filter = StringArgumentType.getString(ctx, "filter")
                                        val value = StringArgumentType.getString(ctx, "value")
                                        val page = StringArgumentType.getString(ctx, "page")
                                        ListCommand.execute(ctx, filter = filter, value = value, pageStr = page)
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
    }

    private fun toggleNode(name: String) = Commands.literal(name)
        .executes { ctx ->
            when (name) {
                "on" -> OnCommand.execute(ctx)
                "off" -> OffCommand.execute(ctx)
            }
            Command.SINGLE_SUCCESS
        }
        .then(
            Commands.argument("player", StringArgumentType.word())
                .requires { source ->
                    val player = source.entity as? ServerPlayer
                    player == null || ServerPacketHandler.isOpLevel2(player)
                }
                .suggests { ctx, builder ->
                    ctx.source.server.playerList.players.forEach { builder.suggest(it.name.string) }
                    builder.buildFuture()
                }
                .executes { ctx ->
                    val targetName = StringArgumentType.getString(ctx, "player")
                    when (name) {
                        "on" -> OnCommand.execute(ctx, targetName)
                        "off" -> OffCommand.execute(ctx, targetName)
                    }
                    Command.SINGLE_SUCCESS
                }
        )

    private fun getLanguageSuggestions(): List<String> {
        val fromJava = Locale.getAvailableLocales()
            .map { it.language.lowercase(Locale.ROOT) }
        val fromConfig = Server.config.languages.keys
            .map { it.trim().lowercase(Locale.ROOT).substringBefore('_') }
        return (fromJava + fromConfig)
            .filter { it.matches(Regex("^[a-z]{2}$")) }
            .map { if (it == "uk") "ua" else it }
            .distinct()
            .sorted()
    }
}
