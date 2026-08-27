package com.faboit.friendsystem.api.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player leaves and at least one of their friends is still online — the
 * counterpart to {@link FriendJoinEvent}.
 *
 * <p>It is fired from {@code PlayerQuitEvent}, so the leaving player is on their way out:
 * read what you need from them inside the handler rather than keeping the reference.</p>
 */
public final class FriendQuitEvent extends FriendSystemEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Set<UUID> friends;
    private final List<Player> onlineFriends;

    public FriendQuitEvent(final Player player, final Set<UUID> friends, final List<Player> onlineFriends) {
        this.player = player;
        this.friends = friends;
        this.onlineFriends = onlineFriends;
    }

    /** The player who is leaving. */
    public Player getPlayer() {
        return this.player;
    }

    /** Every friend of theirs, online or not. */
    public Set<UUID> getFriends() {
        return this.friends;
    }

    /** The friends still online, who could be told about the departure. */
    public List<Player> getOnlineFriends() {
        return this.onlineFriends;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
