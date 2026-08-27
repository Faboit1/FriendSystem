package com.faboit.friendsystem.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before a friend request is stored.
 *
 * <p>Cancelling it stops the request; the caller sees
 * {@link com.faboit.friendsystem.api.FriendRequestResult#CANCELLED} and no feedback is
 * shown, so a cancelling plugin should tell the player why itself.</p>
 *
 * <p>This is not fired when the request is accepted straight away because the target had
 * already sent one — that path fires {@link FriendAddEvent} instead.</p>
 */
public final class FriendRequestSendEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player requester;
    private final UUID target;
    private final String targetName;
    private boolean cancelled;

    public FriendRequestSendEvent(final Player requester, final UUID target, final String targetName) {
        this.requester = requester;
        this.target = target;
        this.targetName = targetName;
    }

    /** The player sending the request. */
    public Player getRequester() {
        return this.requester;
    }

    /** The player being asked; they may be offline. */
    public UUID getTarget() {
        return this.target;
    }

    /** The target's last known username. */
    public String getTargetName() {
        return this.targetName;
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
