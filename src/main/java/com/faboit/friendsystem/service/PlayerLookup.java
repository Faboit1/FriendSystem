package com.faboit.friendsystem.service;

import com.faboit.friendsystem.data.DataStore;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Resolves usernames to UUIDs without ever blocking a region thread.
 *
 * <p>The Skript called {@code Bukkit.getOfflinePlayer(name)}, which can hit Mojang's
 * API on an unknown name. Here the plugin's own name cache is checked first, then the
 * online players, then the server's profile cache — all of which are local lookups.</p>
 */
public final class PlayerLookup {

    /** A player that has been seen on this server before. */
    public record Resolved(UUID uuid, String name) {
    }

    private final DataStore store;

    public PlayerLookup(final DataStore store) {
        this.store = store;
    }

    /** Returns the player with that name, or {@code null} if they never joined this server. */
    public Resolved resolve(final String rawName) {
        if (rawName == null) {
            return null;
        }
        final String name = rawName.trim();
        if (name.isEmpty()) {
            return null;
        }
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Resolved(online.getUniqueId(), online.getName());
        }
        final UUID cached = this.store.lookupByName(name);
        if (cached != null) {
            return new Resolved(cached, this.store.name(cached));
        }
        final OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
            final String known = offline.getName() == null ? name : offline.getName();
            this.store.rememberName(offline.getUniqueId(), known);
            return new Resolved(offline.getUniqueId(), known);
        }
        return null;
    }

    /** The online player behind a UUID, or {@code null} when they are offline. */
    public static Player online(final UUID uuid) {
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    public static boolean isOnline(final UUID uuid) {
        return online(uuid) != null;
    }
}
