package com.faboit.friendsystem.listener;

import com.faboit.friendsystem.api.event.FriendJoinEvent;
import com.faboit.friendsystem.api.event.FriendQuitEvent;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.Events;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.service.ToastService;
import com.faboit.friendsystem.service.UnreadTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    /** Lets other plugins announce the join to whichever friends are around. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinNotifyFriends(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final Set<UUID> friends = this.store.friendsOf(player.getUniqueId());
        final List<Player> online = this.online(friends);
        if (!online.isEmpty()) {
            Events.call(new FriendJoinEvent(player, friends, online));
        }
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
        final Player player = event.getPlayer();
        final Set<UUID> friends = this.store.friendsOf(player.getUniqueId());
        final List<Player> online = this.online(friends);
        if (!online.isEmpty()) {
            Events.call(new FriendQuitEvent(player, friends, online));
        }
        this.store.markSeen(player.getUniqueId());
        this.sessions.clear(player.getUniqueId());
    }

    /** The friends who are online right now, as an immutable list. */
    private List<Player> online(final Set<UUID> friends) {
        if (friends.isEmpty()) {
            return List.of();
        }
        final List<Player> online = new ArrayList<>(friends.size());
        for (final UUID uuid : friends) {
            final Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                online.add(friend);
            }
        }
        return List.copyOf(online);
    }
}
