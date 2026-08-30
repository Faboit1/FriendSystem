package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.ui.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /setfriendcolor <colour>} — the colour direct messages are shown in. */
public final class MessageColorCommand extends SettingCommand {

    public MessageColorCommand(final DataStore store, final Notifier notifier) {
        super(store, notifier);
    }

    @Override
    protected void run(final Player player, final String[] args) {
        if (args.length == 0) {
            this.say(player, "<gray>Your message colour is "
                + Colors.wrap(this.settings(player).color(), false, this.settings(player).color())
                + "<gray>. Available: " + this.palette(player) + "<gray>.</gray>");
            return;
        }
        final String color = args[0].toLowerCase(Locale.ROOT);
        if (!Colors.exists(color)) {
            this.say(player, "<red>Unknown colour. Available: " + this.palette(player) + "<red>.</red>");
            return;
        }
        if (!Colors.canUse(player, color)) {
            this.say(player, "<red>You don't have permission to use that colour.</red>");
            return;
        }
        this.apply(player, settings -> settings.color(color));
        this.say(player, "<gray>Message colour set to " + Colors.wrap(color, false, color) + "<gray>.</gray>");
    }

    /** The colours this player may pick, each rendered in itself. */
    private String palette(final Player player) {
        final List<String> parts = new ArrayList<>();
        for (final String color : Colors.ALL) {
            if (Colors.canUse(player, color)) {
                parts.add(Colors.wrap(color, false, color));
            }
        }
        return String.join("<gray>, </gray>", parts);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        final List<String> allowed = new ArrayList<>();
        for (final String color : Colors.ALL) {
            if (Colors.canUse(player, color)) {
                allowed.add(color);
            }
        }
        return FriendsCommand.filter(allowed, args[0]);
    }
}
