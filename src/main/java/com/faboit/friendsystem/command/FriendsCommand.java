package com.faboit.friendsystem.command;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.FriendService;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.PlayerLookup;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.ui.Navigator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /friends} — opens the menu, or performs a quick action without the GUI. */
public final class FriendsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "add", "remove", "block", "unblock", "accept", "deny", "requests", "blocked", "settings");

    private final DataStore store;
    private final FriendConfig config;
    private final SessionManager sessions;
    private final Navigator navigator;
    private final FriendService friends;
    private final Notifier notifier;
    private final PlayerLookup lookup;

    public FriendsCommand(final DataStore store, final FriendConfig config, final SessionManager sessions,
                          final Navigator navigator, final FriendService friends, final Notifier notifier,
                          final PlayerLookup lookup) {
        this.store = store;
        this.config = config;
        this.sessions = sessions;
        this.navigator = navigator;
        this.friends = friends;
        this.notifier = notifier;
        this.lookup = lookup;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length == 0) {
            this.sessions.of(player).backCommand(null);
            this.navigator.menu(player);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (this.requireName(player, args, "/friends add <name>")) {
                    final FriendService.AddResult result = this.friends.requestFriend(player, args[1]);
                    this.friends.feedback(player, result, args[1]);
                }
            }
            case "remove", "unfriend" -> this.withResolved(player, args, "/friends remove <name>", target -> {
                if (this.store.areFriends(player.getUniqueId(), target)) {
                    this.friends.unfriend(player, target);
                } else {
                    this.notifier.feedback(player, "<yellow>You're not friends with that player.</yellow>");
                }
            });
            case "block" -> this.withResolved(player, args, "/friends block <name>", target -> {
                if (target.equals(player.getUniqueId())) {
                    this.notifier.feedback(player, "<yellow>You can't block yourself.</yellow>");
                } else {
                    this.friends.block(player, target);
                }
            });
            case "unblock" -> this.withResolved(player, args, "/friends unblock <name>", target -> {
                this.friends.unblock(player, target);
                this.notifier.feedback(player, "<green>Unblocked " + this.store.name(target) + ".</green>");
            });
            case "accept" -> this.withResolved(player, args, "/friends accept <name>", target -> {
                if (this.store.hasRequest(player.getUniqueId(), target)) {
                    this.friends.accept(player, target);
                } else {
                    this.notifier.feedback(player, "<yellow>You have no friend request from that player.</yellow>");
                }
            });
            case "deny", "decline" -> this.withResolved(player, args, "/friends deny <name>", target -> {
                this.friends.decline(player, target);
                this.notifier.feedback(player, "<gray>Declined " + this.store.name(target) + "'s friend request.</gray>");
            });
            case "requests" -> {
                this.sessions.of(player).backCommand(null);
                this.navigator.requests(player);
            }
            case "blocked" -> {
                this.sessions.of(player).backCommand(null);
                this.navigator.blocked(player);
            }
            case "settings" -> {
                // Opened without a menu behind it, so Back can be pointed at the server's
                // own settings menu instead of a menu the player never came from.
                final FriendConfig.Integration back = this.config.settingsBack();
                this.sessions.of(player).backCommand(back.enabled() ? back.command() : null);
                this.navigator.settings(player);
            }
            default -> this.notifier.feedback(player, "<red>Unknown /friends subcommand. Try: "
                + String.join(", ", SUBCOMMANDS) + ".</red>");
        }
        return true;
    }

    private boolean requireName(final Player player, final String[] args, final String usage) {
        if (args.length < 2 || args[1].isBlank()) {
            this.notifier.feedback(player, "<red>Usage: " + usage + "</red>");
            return false;
        }
        return true;
    }

    /** Resolves {@code args[1]} to a player that has joined before, then runs the action. */
    private void withResolved(final Player player, final String[] args, final String usage,
                              final java.util.function.Consumer<UUID> action) {
        if (!this.requireName(player, args, usage)) {
            return;
        }
        final PlayerLookup.Resolved resolved = this.lookup.resolve(args[1]);
        if (resolved == null) {
            this.notifier.feedback(player, "<red>No player named '" + args[1] + "' has joined this server.</red>");
            return;
        }
        this.store.rememberName(resolved.uuid(), resolved.name());
        action.accept(resolved.uuid());
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length != 2) {
            return List.of();
        }
        final UUID me = player.getUniqueId();
        final List<String> options = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add", "block" -> onlineNames();
            case "remove", "unfriend" -> this.store.namesOf(this.store.friendsOf(me));
            case "unblock" -> this.store.namesOf(this.store.blocked(me));
            case "accept", "deny", "decline" -> this.store.namesOf(this.store.requests(me));
            default -> List.of();
        };
        return filter(options, args[1]);
    }

    static List<String> onlineNames() {
        final List<String> names = new ArrayList<>();
        for (final Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    static List<String> filter(final List<String> options, final String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
