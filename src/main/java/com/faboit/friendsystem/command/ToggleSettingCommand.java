package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.ui.Icons;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * An on/off preference driven by its own command — {@code /showfriendtoasts},
 * {@code /friendsounds} and friends. With no argument the setting flips, which is
 * what the old settings buttons did.
 */
public final class ToggleSettingCommand extends SettingCommand {

    private static final List<String> CHOICES = List.of("on", "off", "toggle");

    private final String usage;
    private final String label;
    private final Predicate<PlayerSettings> getter;
    private final BiConsumer<PlayerSettings, Boolean> setter;

    public ToggleSettingCommand(final DataStore store, final Notifier notifier, final String usage,
                                final String label, final Predicate<PlayerSettings> getter,
                                final BiConsumer<PlayerSettings, Boolean> setter) {
        super(store, notifier);
        this.usage = usage;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void run(final Player player, final String[] args) {
        final boolean current = this.getter.test(this.settings(player));
        final Boolean wanted = parse(args.length == 0 ? "toggle" : args[0], current);
        if (wanted == null) {
            this.say(player, "<red>Usage: /" + this.usage + " [on|off]</red>");
            return;
        }
        this.apply(player, settings -> this.setter.accept(settings, wanted));
        this.say(player, "<gray>" + this.label + " is now " + Icons.onOff(wanted) + "<gray>.</gray>");
    }

    private static Boolean parse(final String raw, final boolean current) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "enable", "enabled" -> Boolean.TRUE;
            case "off", "false", "no", "disable", "disabled" -> Boolean.FALSE;
            case "toggle" -> !current;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        return args.length == 1 ? FriendsCommand.filter(CHOICES, args[0]) : List.of();
    }
}
