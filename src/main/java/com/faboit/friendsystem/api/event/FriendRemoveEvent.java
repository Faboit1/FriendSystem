package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a friendship ends through unfriending. Cancelling it keeps the two
 * players friends.
 *
 * <p>Blocking someone also ends the friendship, but that path fires
 * {@link FriendBlockEvent} instead — check {@link FriendBlockEvent#wereFriends()} there
 * if you need to catch every way a friendship can end.</p>
 */
public final class FriendRemoveEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID friend;
    private boolean cancelled;

    public FriendRemoveEvent(final Player player, final UUID friend) {
        this.player = player;
        this.friend = friend;
    }

    /** The player doing the unfriending. */
    public Player getPlayer() {
        return this.player;
    }

    /** The friend being removed; they may be offline. */
    public UUID getFriend() {
        return this.friend;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
