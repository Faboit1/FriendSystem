package com.faboit.friendsystem.api.event;

import org.bukkit.event.Event;

/**
 * Base class for every FriendSystem event.
 *
 * <p>All of them are synchronous and are fired on the thread that owns the acting
 * player — on Folia, that player's region thread. Handlers therefore may touch the
 * acting player directly, but must hop onto {@code other.getScheduler()} before
 * touching any <em>other</em> player, and must not block: a slow handler stalls the
 * region it runs on.</p>
 */
public abstract class FriendSystemEvent extends Event {

    protected FriendSystemEvent() {
        super(false);
    }
}
