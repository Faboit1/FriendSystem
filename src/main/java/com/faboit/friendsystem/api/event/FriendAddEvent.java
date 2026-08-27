package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired just before two players become friends, whether the friendship came from
 * accepting a request in the dialog, {@code /friends accept}, or adding somebody who had
 * already added you.
 *
 * <p>Cancelling it leaves the pending request untouched.</p>
 */
public final class FriendAddEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID friend;
    private boolean cancelled;

    public FriendAddEvent(final Player player, final UUID friend) {
        this.player = player;
        this.friend = friend;
    }

    /** The player accepting the request. */
    public Player getPlayer() {
        return this.player;
    }

    /** The player who sent the request; they may be offline. */
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
