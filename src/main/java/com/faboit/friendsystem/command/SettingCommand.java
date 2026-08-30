package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.Notifier;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Shared plumbing for the one-command-per-preference family that replaced the
 * settings dialog: resolve the sender, apply the change, save it and confirm it.
 */
abstract class SettingCommand implements CommandExecutor, TabCompleter {

    protected final DataStore store;
    protected final Notifier notifier;

    protected SettingCommand(final DataStore store, final Notifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    @Override
    public final boolean onCommand(final CommandSender sender, final Command command, final String label,
                                   final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        this.run(player, args);
        return true;
    }

    protected abstract void run(Player player, String[] args);

    protected PlayerSettings settings(final Player player) {
        return this.store.settings(player.getUniqueId());
    }

    /** Mutates the player's settings, writes them through and plays the click sound. */
    protected void apply(final Player player, final Consumer<PlayerSettings> change) {
        change.accept(this.settings(player));
        this.store.persistPlayer(player.getUniqueId());
        this.notifier.click(player);
    }

    protected void say(final Player player, final String miniMessage) {
        this.notifier.chat(player, miniMessage);
    }
}
