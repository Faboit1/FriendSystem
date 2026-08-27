package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before a pending friend request is declined. Cancelling it leaves the request
 * pending.
 */
public final class FriendRequestDeclineEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID requester;
    private boolean cancelled;

    public FriendRequestDeclineEvent(final Player player, final UUID requester) {
        this.player = player;
        this.requester = requester;
    }

    /** The player who was asked and is now saying no. */
    public Player getPlayer() {
        return this.player;
    }

    /** The player whose request is being declined; they may be offline. */
    public UUID getRequester() {
        return this.requester;
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
