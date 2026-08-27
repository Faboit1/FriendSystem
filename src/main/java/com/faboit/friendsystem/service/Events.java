package com.faboit.friendsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

/** Fires the plugin's API events and reports whether the action may go ahead. */
public final class Events {

    private Events() {
    }

    /**
     * Fires an event and returns {@code false} when a listener cancelled it.
     *
     * <p>All FriendSystem events are synchronous, so this must run on a server thread —
     * on Folia, the region thread owning the player the event is about. Every call site
     * inside the plugin is a command, a dialog click or a connection event, all of which
     * already satisfy that; API callers get a clear error instead of a confusing one from
     * deep inside Bukkit.</p>
     */
    public static boolean call(final Event event) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(event.getEventName()
                + " must be fired from a server thread — call FriendSystem's API from the acting "
                + "player's scheduler, not from an async task.");
        }
        Bukkit.getPluginManager().callEvent(event);
        return !(event instanceof Cancellable cancellable) || !cancellable.isCancelled();
    }
}
