package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main.Companion.config
import com.dreamdisplays.utils.MessageUtil.sendMessage
import org.bukkit.command.CommandSender

// TODO: for removing in stable 1.7.0
class ReloadCommand : SubCommand {
    override val name = "reload"
    override val permission = config.permissions.reload

    /** Reloads `config.yml` from disk; replies with success or failure message. */
    override fun execute(sender: CommandSender, args: Array<String?>) {
        try {
            config.reload()
            sendMessage(sender, "configReloaded")
            sendMessage(sender, "configReloadSummary")
        } catch (_: Exception) {
            sendMessage(sender, "configReloadFailed")
        }
    }
}
