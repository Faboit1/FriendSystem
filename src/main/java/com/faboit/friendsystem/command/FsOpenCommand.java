package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.ui.Navigator;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /fsopen <uuid>} — internal command behind the clickable names in the "cards"
 * friend view, since chat components can only run commands. It refuses anything that
 * is not one of the caller's own friends.
 */
public final class FsOpenCommand implements CommandExecutor, TabCompleter {

    private final DataStore store;
    private final Navigator navigator;

    public FsOpenCommand(final DataStore store, final Navigator navigator) {
        this.store = store;
        this.navigator = navigator;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return true;
        }
        final UUID friend;
        try {
            friend = UUID.fromString(args[0]);
        } catch (final IllegalArgumentException ignored) {
            return true;
        }
        if (this.store.areFriends(player.getUniqueId(), friend)) {
            this.navigator.friendPage(player, friend);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return List.of();
    }
}
