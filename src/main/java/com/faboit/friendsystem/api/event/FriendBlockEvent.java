package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before one player blocks another. Cancelling it stops the block, the removal of
 * the friendship and the removal of any pending requests.
 */
public final class FriendBlockEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID target;
    private final boolean wereFriends;
    private boolean cancelled;

    public FriendBlockEvent(final Player player, final UUID target, final boolean wereFriends) {
        this.player = player;
        this.target = target;
        this.wereFriends = wereFriends;
    }

    /** The player doing the blocking. */
    public Player getPlayer() {
        return this.player;
    }

    /** The player being blocked; they may be offline. */
    public UUID getTarget() {
        return this.target;
    }

    /** Whether this block also ends a friendship. */
    public boolean wereFriends() {
        return this.wereFriends;
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
