package com.faboit.friendsystem.service;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe scheduling helpers.
 *
 * <p>On Folia a player may be ticked by any region thread, so anything that touches a
 * player other than the one who caused the event has to be handed to that player's
 * own scheduler. These helpers work unchanged on regular Paper, where the schedulers
 * simply run everything on the main thread.</p>
 */
public final class Scheduling {

    private Scheduling() {
    }

    /** Runs an action on the thread that owns the given player, if they are still online. */
    public static void onPlayer(final Plugin plugin, final Player player, final Consumer<Player> action) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.accept(player);
            }
        }, null);
    }

    /** Runs a task off the server threads entirely (database work, pruning, …). */
    public static void async(final Plugin plugin, final Runnable action) {
        org.bukkit.Bukkit.getAsyncScheduler().runNow(plugin, task -> action.run());
    }
}
