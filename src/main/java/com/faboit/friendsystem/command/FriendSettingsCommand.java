package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.ui.Colors;
import com.faboit.friendsystem.ui.Icons;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /friendsettings} — a read-only overview of every preference and the command
 * that changes it, which is what the old settings dialog used to show at a glance.
 */
public final class FriendSettingsCommand extends SettingCommand {

    public FriendSettingsCommand(final DataStore store, final Notifier notifier) {
        super(store, notifier);
    }

    @Override
    protected void run(final Player player, final String[] args) {
        final PlayerSettings settings = this.settings(player);
        this.say(player, "<dark_gray><st>                    </st></dark_gray> <white><b>Friend Settings</b></white> "
            + "<dark_gray><st>                    </st></dark_gray>");
        this.line(player, "Friend list view", settings.cards() ? "Cards (heads)" : "Buttons", "/friendlistview buttons|cards");
        this.line(player, "Toasts", Icons.onOff(settings.toasts()), "/showfriendtoasts on|off");
        this.line(player, "Sound effects", Icons.onOff(settings.sounds()), "/friendsounds on|off");
        this.line(player, "Action bar", Icons.onOff(settings.actionBar()), "/friendactionbar on|off");
        this.line(player, "Unread reminder", Icons.onOff(settings.reminder()), "/friendreminders on|off");
        this.line(player, "Who can message me", settings.privacyDisplayName(),
            "/whocanmessageme everyone|friendsoffriends|friends|nobody");
        this.line(player, "GUI scale", String.valueOf(settings.guiScale()), "/setfriendguiscale 1-4");
        this.line(player, "Message colour", Colors.wrap(settings.color(), false, settings.color()), "/setfriendcolor [colour]");
    }

    private void line(final Player player, final String name, final String value, final String command) {
        this.say(player, "<gray>" + name + ": <white>" + value + "</white> <dark_gray>— " + command + "</dark_gray></gray>");
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        return List.of();
    }
}
