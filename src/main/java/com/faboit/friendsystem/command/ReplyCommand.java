package com.faboit.friendsystem.command;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.MessageService;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.Session;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.ui.Navigator;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /reply} — continues the most recent conversation. */
public final class ReplyCommand implements CommandExecutor {

    private final DataStore store;
    private final SessionManager sessions;
    private final Navigator navigator;
    private final MessageService messages;
    private final Notifier notifier;

    public ReplyCommand(final DataStore store, final SessionManager sessions, final Navigator navigator,
                        final MessageService messages, final Notifier notifier) {
        this.store = store;
        this.sessions = sessions;
        this.navigator = navigator;
        this.messages = messages;
        this.notifier = notifier;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        final UUID last = this.store.lastConvo(player.getUniqueId());
        if (last == null) {
            this.notifier.feedback(player, "<yellow>You have no one to reply to yet.</yellow>");
            return true;
        }
        if (args.length == 0) {
            this.sessions.of(player).chatOrigin(Session.ORIGIN_DIRECT);
            this.navigator.chat(player, last);
            return true;
        }
        final MessageService.Delivery result = this.messages.deliver(player, last, String.join(" ", args));
        if (result == MessageService.Delivery.EMPTY) {
            this.sessions.of(player).chatOrigin(Session.ORIGIN_DIRECT);
            this.navigator.chat(player, last);
        } else {
            this.messages.feedback(player, result, this.store.name(last));
        }
        return true;
    }
}
