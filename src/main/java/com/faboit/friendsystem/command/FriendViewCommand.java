package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.Notifier;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /friendlistview <buttons|cards>} — how the friends list renders each friend. */
public final class FriendViewCommand extends SettingCommand {

    private static final List<String> CHOICES = List.of("buttons", "cards");

    public FriendViewCommand(final DataStore store, final Notifier notifier) {
        super(store, notifier);
    }

    @Override
    protected void run(final Player player, final String[] args) {
        final boolean cards = this.settings(player).cards();
        final String wanted = args.length == 0
            ? (cards ? PlayerSettings.VIEW_BUTTONS : PlayerSettings.VIEW_CARDS)
            : parse(args[0]);
        if (wanted == null) {
            this.say(player, "<red>Usage: /friendlistview buttons|cards</red>");
            return;
        }
        this.apply(player, settings -> settings.viewMode(wanted));
        this.say(player, "<gray>Friend list view is now <white>"
            + (PlayerSettings.VIEW_CARDS.equals(wanted) ? "Cards (heads)" : "Buttons") + "</white>.</gray>");
    }

    private static String parse(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "cards", "card", "heads", "head" -> PlayerSettings.VIEW_CARDS;
            case "buttons", "button", "list" -> PlayerSettings.VIEW_BUTTONS;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        return args.length == 1 ? FriendsCommand.filter(CHOICES, args[0]) : List.of();
    }
}
