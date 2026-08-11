package com.faboit.friendsystem.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/** Holds one {@link Session} per online player. */
public final class SessionManager {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session of(final UUID uuid) {
        return this.sessions.computeIfAbsent(uuid, key -> new Session());
    }

    public Session of(final Player player) {
        return this.of(player.getUniqueId());
    }

    public void clear(final UUID uuid) {
        this.sessions.remove(uuid);
    }
}
