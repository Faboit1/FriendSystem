package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.Notifier;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /whocanmessageme <everyone|friendsoffriends|friends|nobody>}. */
public final class MessagePrivacyCommand extends SettingCommand {

    private static final List<String> CHOICES = List.of("everyone", "friendsoffriends", "friends", "nobody");

    public MessagePrivacyCommand(final DataStore store, final Notifier notifier) {
        super(store, notifier);
    }

    @Override
    protected void run(final Player player, final String[] args) {
        if (args.length == 0) {
            this.say(player, "<gray>Who can message you: <white>" + this.settings(player).privacyDisplayName()
                + "</white>. <dark_gray>/whocanmessageme " + String.join("|", CHOICES) + "</dark_gray></gray>");
            return;
        }
        final String value = parse(args[0]);
        if (value == null) {
            this.say(player, "<red>Usage: /whocanmessageme " + String.join("|", CHOICES) + "</red>");
            return;
        }
        this.apply(player, settings -> settings.dmPrivacy(value));
        this.say(player, "<gray>Who can message you is now <white>"
            + this.settings(player).privacyDisplayName() + "</white>.</gray>");
    }

    /** Maps the friendly command words onto the stored preference values. */
    private static String parse(final String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "everyone", "anyone", "all", "public" -> PlayerSettings.PRIVACY_ANYONE;
            case "friendsoffriends", "friends-of-friends", "fof", "mutuals" -> PlayerSettings.PRIVACY_FOF;
            case "friends", "friendsonly", "friends-only" -> PlayerSettings.PRIVACY_FRIENDS;
            case "nobody", "none", "noone", "no-one", "off" -> PlayerSettings.PRIVACY_NONE;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        return args.length == 1 ? FriendsCommand.filter(CHOICES, args[0]) : List.of();
    }
}
