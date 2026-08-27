package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Fired before a player is unblocked. Cancelling it keeps them blocked. */
public final class FriendUnblockEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID target;
    private boolean cancelled;

    public FriendUnblockEvent(final Player player, final UUID target) {
        this.player = player;
        this.target = target;
    }

    public Player getPlayer() {
        return this.player;
    }

    /** The player being unblocked; they may be offline. */
    public UUID getTarget() {
        return this.target;
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
