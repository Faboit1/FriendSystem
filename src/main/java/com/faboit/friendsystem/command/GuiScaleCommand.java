package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.Notifier;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /setfriendguiscale <1-4>} — how many friends and chat lines fit on a page.
 * It should match the client's own GUI Scale video setting.
 */
public final class GuiScaleCommand extends SettingCommand {

    private static final List<String> CHOICES = List.of("1", "2", "3", "4");

    public GuiScaleCommand(final DataStore store, final Notifier notifier) {
        super(store, notifier);
    }

    @Override
    protected void run(final Player player, final String[] args) {
        if (args.length == 0) {
            this.say(player, "<gray>Your friend GUI scale is <white>" + this.settings(player).guiScale()
                + "</white>. <dark_gray>/setfriendguiscale 1-4</dark_gray></gray>");
            return;
        }
        final int scale = parseScale(args[0]);
        if (scale < 1) {
            this.say(player, "<red>Usage: /setfriendguiscale 1-4 — match your Options ▸ Video Settings ▸ "
                + "GUI Scale, or use 4 for Auto.</red>");
            return;
        }
        this.apply(player, settings -> settings.guiScale(scale));
        this.say(player, "<gray>Friend GUI scale set to <white>" + scale + "</white> ("
            + this.settings(player).friendsPerPage() + " friends and "
            + this.settings(player).maxMessages() + " messages per page).</gray>");
    }

    private static int parseScale(final String raw) {
        try {
            final int value = Integer.parseInt(raw);
            return value >= 1 && value <= 4 ? value : -1;
        } catch (final NumberFormatException ignored) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        return args.length == 1 ? FriendsCommand.filter(CHOICES, args[0]) : List.of();
    }
}
