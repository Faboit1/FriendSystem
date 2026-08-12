package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.MessageService;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.PlayerLookup;
import com.faboit.friendsystem.service.Session;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.ui.Navigator;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /message <player> [text]} — opens a conversation, or sends a message straight away. */
public final class MessageCommand implements CommandExecutor, TabCompleter {

    private final DataStore store;
    private final SessionManager sessions;
    private final Navigator navigator;
    private final MessageService messages;
    private final Notifier notifier;
    private final PlayerLookup lookup;

    public MessageCommand(final DataStore store, final SessionManager sessions, final Navigator navigator,
                          final MessageService messages, final Notifier notifier, final PlayerLookup lookup) {
        this.store = store;
        this.sessions = sessions;
        this.navigator = navigator;
        this.messages = messages;
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
            this.navigator.friends(player);
            return true;
        }
        final PlayerLookup.Resolved target = this.lookup.resolve(args[0]);
        if (target == null) {
            this.notifier.feedback(player, "<red>No player named '" + args[0] + "' has joined this server.</red>");
            return true;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            this.notifier.feedback(player, "<yellow>You can't message yourself.</yellow>");
            return true;
        }
        this.store.rememberName(target.uuid(), target.name());

        if (args.length == 1) {
            this.sessions.of(player).chatOrigin(Session.ORIGIN_DIRECT);
            this.navigator.chat(player, target.uuid());
            return true;
        }
        final String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final MessageService.Delivery result = this.messages.deliver(player, target.uuid(), text);
        if (result == MessageService.Delivery.EMPTY) {
            this.sessions.of(player).chatOrigin(Session.ORIGIN_DIRECT);
            this.navigator.chat(player, target.uuid());
        } else {
            this.messages.feedback(player, result, target.name());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return args.length == 1 ? FriendsCommand.filter(FriendsCommand.onlineNames(), args[0]) : List.of();
    }
}
