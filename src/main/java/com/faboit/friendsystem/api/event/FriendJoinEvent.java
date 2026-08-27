package com.faboit.friendsystem.api.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player joins and at least one of their friends is already online — the
 * event a "your friend X just joined" announcer wants.
 *
 * <p>It is fired from {@code PlayerJoinEvent} at {@code MONITOR} priority, so the player
 * is fully online by then. Nothing is fired when none of the player's friends are
 * online; use Bukkit's own {@code PlayerJoinEvent} if you need every join.</p>
 *
 * <p>{@link #getOnlineFriends()} may contain players from other Folia regions — send to
 * each of them through {@code friend.getScheduler()}.</p>
 */
public final class FriendJoinEvent extends FriendSystemEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Set<UUID> friends;
    private final List<Player> onlineFriends;

    public FriendJoinEvent(final Player player, final Set<UUID> friends, final List<Player> onlineFriends) {
        this.player = player;
        this.friends = friends;
        this.onlineFriends = onlineFriends;
    }

    /** The player who just joined. */
    public Player getPlayer() {
        return this.player;
    }

    /** Every friend of theirs, online or not. */
    public Set<UUID> getFriends() {
        return this.friends;
    }

    /** The friends who are online right now and could be told about the join. */
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
