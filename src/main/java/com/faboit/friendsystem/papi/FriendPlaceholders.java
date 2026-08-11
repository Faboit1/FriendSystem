package com.faboit.friendsystem.papi;

import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.service.PlayerLookup;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * PlaceholderAPI expansion exposing the same values the Skript did:
 * {@code %friendsystem_friends%}, {@code %friendsystem_online_friends%},
 * {@code %friendsystem_offline_friends%}, {@code %friendsystem_unread_messages%} and
 * {@code %friendsystem_incoming_friend_request%}.
 */
public final class FriendPlaceholders extends PlaceholderExpansion {

    private final Plugin plugin;
    private final DataStore store;

    public FriendPlaceholders(final Plugin plugin, final DataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public String getIdentifier() {
        return "friendsystem";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", this.plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        if (player == null) {
            return "";
        }
        final UUID uuid = player.getUniqueId();
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "friends" -> String.valueOf(this.store.friendCount(uuid));
            case "online_friends" -> String.valueOf(this.countFriends(uuid, true));
            case "offline_friends" -> String.valueOf(this.countFriends(uuid, false));
            case "unread_messages" -> String.valueOf(this.store.totalUnread(uuid, false));
            case "incoming_friend_request" -> String.valueOf(this.store.requestCount(uuid));
            default -> null;
        };
    }

    private int countFriends(final UUID uuid, final boolean online) {
        int total = 0;
        for (final UUID friend : this.store.friendsOf(uuid)) {
            if (PlayerLookup.isOnline(friend) == online) {
                total++;
            }
        }
        return total;
    }
}
