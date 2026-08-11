package com.faboit.friendsystem.service;

import com.faboit.friendsystem.data.DataStore;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Keeps the {@code unread} scoreboard tag in sync, so other plugins and command
 * blocks can select players who have something waiting for them
 * ({@code @a[tag=unread]}).
 */
public final class UnreadTags {

    private static final String TAG = "unread";

    private final Plugin plugin;
    private final DataStore store;

    public UnreadTags(final Plugin plugin, final DataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Adds or removes the tag based on unread messages and pending friend requests. */
    public void refresh(final UUID uuid) {
        final Player player = PlayerLookup.online(uuid);
        if (player == null) {
            return;
        }
        final int pending = this.store.totalUnread(uuid, true) + this.store.requestCount(uuid);
        Scheduling.onPlayer(this.plugin, player, target -> {
            if (pending > 0) {
                target.addScoreboardTag(TAG);
            } else {
                target.removeScoreboardTag(TAG);
            }
        });
    }
}
