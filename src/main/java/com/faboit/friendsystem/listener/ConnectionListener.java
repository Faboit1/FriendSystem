package com.faboit.friendsystem.listener;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.service.ToastService;
import com.faboit.friendsystem.service.UnreadTags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Keeps names, last-seen stamps and the unread tag up to date, and greets returning players. */
public final class ConnectionListener implements Listener {

    /** Two seconds, so the greeting lands after the client has finished loading in. */
    private static final long GREETING_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final DataStore store;
    private final SessionManager sessions;
    private final UnreadTags tags;
    private final Notifier notifier;

    public ConnectionListener(final Plugin plugin, final DataStore store, final SessionManager sessions,
                              final UnreadTags tags, final Notifier notifier) {
        this.plugin = plugin;
        this.store = store;
        this.sessions = sessions;
        this.tags = tags;
        this.notifier = notifier;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        this.store.rememberName(player.getUniqueId(), player.getName());
        this.tags.refresh(player.getUniqueId());
        player.getScheduler().runDelayed(this.plugin, task -> this.greet(player), null, GREETING_DELAY_TICKS);
    }

    /** Tells the player about anything that piled up while they were away. */
    private void greet(final Player player) {
        if (!player.isOnline()) {
            return;
        }
        final int requests = this.store.requestCount(player.getUniqueId());
        final int unread = this.store.totalUnread(player.getUniqueId(), true);
        if (requests > 0) {
            this.notifier.chat(player, "<aqua>👤 You have <white>" + requests
                + "</white> pending friend request(s).</aqua> <dark_gray>/friends</dark_gray>");
            this.notifier.toasts().show(player, ToastService.PENDING);
        }
        if (unread > 0) {
            this.notifier.chat(player, "<aqua>✉ You have <white>" + unread
                + "</white> unread message(s).</aqua> <dark_gray>/friends</dark_gray>");
            this.notifier.toasts().show(player, ToastService.UNREAD);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.store.markSeen(event.getPlayer().getUniqueId());
        this.sessions.clear(event.getPlayer().getUniqueId());
    }
}
