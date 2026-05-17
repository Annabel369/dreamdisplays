package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.datatypes.DisplayData
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.utils.MessageUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.*
import kotlin.math.max

// TODO: for removing in stable 1.7.0
object ListCommand {
    private const val PAGE_SIZE = 10
    private const val FILTER_MINE = "mine"
    private const val FILTER_WORLD = "world"
    private const val FILTER_OWNER = "owner"
    private const val FILTER_SYNC = "sync"

    fun execute(ctx: CommandContext<CommandSourceStack>, filter: String? = null, value: String? = null, pageStr: String? = null): Int {
        val player = ctx.source.entity as? ServerPlayer
        val config = Server.config
        val server = ctx.source.server

        val displays = sortedDisplays()
        if (displays.isEmpty()) {
            sendMsg(ctx, player, "noDisplaysFound")
            return 1
        }

        val ownerNameCache = mutableMapOf<UUID, String?>()

        fun getOwnerName(ownerId: UUID): String? =
            ownerNameCache.getOrPut(ownerId) {
                server.playerList.players.find { it.uuid == ownerId }?.name?.string
            }

        val filtered: List<DisplayData> = when (filter?.lowercase()) {
            null -> displays
            FILTER_MINE -> if (player != null) displays.filter { it.ownerId == player.uuid } else displays
            FILTER_WORLD -> value?.let { wn -> displays.filter { it.worldKey.endsWith(wn, ignoreCase = true) } } ?: displays
            FILTER_OWNER -> value?.let { on ->
                displays.filter { getOwnerName(it.ownerId)?.equals(on, ignoreCase = true) == true || it.ownerId.toString().equals(on, ignoreCase = true) }
            } ?: displays
            FILTER_SYNC -> displays.filter { it.isSync }
            else -> {
                val pageNum = filter.toIntOrNull()
                if (pageNum != null) {
                    sendPage(ctx, player, displays, ownerNameCache, pageNum, config)
                    return 1
                }
                displays
            }
        }

        if (filtered.isEmpty()) {
            sendMsg(ctx, player, "noDisplaysFound")
            return 1
        }

        val page = (pageStr?.toIntOrNull() ?: 1)
        sendPage(ctx, player, filtered, ownerNameCache, page, config)
        return 1
    }

    private fun sendPage(
        ctx: CommandContext<CommandSourceStack>,
        player: ServerPlayer?,
        displays: List<DisplayData>,
        ownerNameCache: MutableMap<UUID, String?>,
        page: Int,
        config: com.dreamdisplays.server.Config,
    ) {
        val server = ctx.source.server
        fun getOwnerName(ownerId: UUID): String? =
            ownerNameCache.getOrPut(ownerId) {
                server.playerList.players.find { it.uuid == ownerId }?.name?.string
            }

        fun msg(key: String): String = config.getMessageForPlayer(player, key) as? String ?: key
        fun msgf(key: String, vararg args: String): String {
            var t = msg(key)
            args.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
            return t
        }

        val totalPages = max(1, (displays.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val p = page.coerceIn(1, totalPages)
        val startIndex = (p - 1) * PAGE_SIZE
        val endExclusive = minOf(startIndex + PAGE_SIZE, displays.size)
        val pageDisplays = displays.subList(startIndex, endExclusive)

        sendMsg(ctx, player, "displayListHeader")
        sendColoredMsg(ctx, player, msgf("displayListPageLine", p.toString(), totalPages.toString(), displays.size.toString()))

        pageDisplays.forEachIndexed { localIndex, d ->
            val index = startIndex + localIndex + 1
            val owner = getOwnerName(d.ownerId) ?: msg("displayListUnknownOwner")
            val worldName = d.worldKey.substringAfterLast(':')
            val idShort = d.id.toString().substring(0, 8)
            val url = d.url.ifBlank { msg("displayListUnavailableUrl") }
            val baseLine = msgf("displayListEntry", index.toString(), owner, d.minX.toString(), d.minY.toString(), d.minZ.toString(), url)
            val details = msgf("displayListDetails", worldName, d.width.toString(), d.height.toString(), d.isSync.toString(), idShort)
            sendColoredMsg(ctx, player, baseLine + details)
        }
    }

    private fun sortedDisplays(): List<DisplayData> =
        DisplayManager.getDisplays().sortedWith(
            compareBy(
                { it.worldKey },
                { it.minX },
                { it.minY },
                { it.minZ },
                { it.id.toString() }
            )
        )

    private fun sendMsg(ctx: CommandContext<CommandSourceStack>, player: ServerPlayer?, key: String) {
        val config = Server.config
        val msg = config.getMessageForPlayer(player, key)
        if (player != null) {
            MessageUtil.sendColoredMessage(player, msg)
        } else {
            ctx.source.sendSystemMessage(Component.literal(msg?.toString() ?: key))
        }
    }

    private fun sendColoredMsg(ctx: CommandContext<CommandSourceStack>, player: ServerPlayer?, msg: String) {
        if (player != null) {
            MessageUtil.sendColoredMessage(player, msg)
        } else {
            ctx.source.sendSystemMessage(Component.literal(msg))
        }
    }
}
