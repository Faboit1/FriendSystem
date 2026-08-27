package com.faboit.friendsystem.api.event;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before a direct message is stored and delivered — after the cooldown, blocking
 * and privacy checks have all passed.
 *
 * <p>Cancelling it drops the message silently: nothing is stored, no unread counter
 * moves, the cooldown is not consumed and the caller sees
 * {@link com.faboit.friendsystem.api.MessageResult#CANCELLED}.</p>
 *
 * <p>{@link #setContent(String)} rewrites the message. The text is stripped of
 * MiniMessage syntax and truncated to the configured maximum length again after the
 * event, so a replacement can never inject formatting.</p>
 */
public final class FriendMessageEvent extends FriendSystemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final UUID receiver;
    private final boolean friends;
    private String content;
    private boolean cancelled;

    public FriendMessageEvent(final Player sender, final UUID receiver, final String content, final boolean friends) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.friends = friends;
    }

    public Player getSender() {
        return this.sender;
    }

    /** The receiver; they may be offline, in which case the message simply waits. */
    public UUID getReceiver() {
        return this.receiver;
    }

    /** The receiver as an online player, if they are online. */
    public Optional<Player> getReceiverPlayer() {
        return Optional.ofNullable(Bukkit.getPlayer(this.receiver));
    }

    /** Whether the two are friends — strangers get no sound or toast. */
    public boolean areFriends() {
        return this.friends;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(final String content) {
        this.content = content == null ? "" : content;
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
