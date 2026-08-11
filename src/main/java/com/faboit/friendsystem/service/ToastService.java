package com.faboit.friendsystem.service;

import com.faboit.friendsystem.data.DataStore;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

/**
 * Pop-up toasts, implemented the same way the Skript did it: hidden advancements
 * with an impossible criterion that get revoked and re-awarded to make the toast
 * appear. Registration happens once on startup; from then on the advancements are
 * part of the world and are looked up instead of re-created.
 */
public final class ToastService {

    public static final String MESSAGE = "t_message";
    public static final String REQUEST = "t_request";
    public static final String ACCEPT = "t_accept";
    public static final String PENDING = "t_pending";
    public static final String UNREAD = "t_unread";

    private static final String CRITERION = "t";

    private final DataStore store;
    private final Logger logger;
    private final boolean enabled;

    public ToastService(final DataStore store, final Logger logger, final boolean enabled) {
        this.store = store;
        this.logger = logger;
        this.enabled = enabled;
    }

    /** Registers the toast advancements. Must run on the main thread during startup. */
    public void register() {
        if (!this.enabled) {
            return;
        }
        this.register(MESSAGE, "minecraft:player_head", "New message", "task");
        this.register(REQUEST, "minecraft:player_head", "Friend request", "goal");
        this.register(ACCEPT, "minecraft:player_head", "New friend!", "challenge");
        this.register(PENDING, "minecraft:nether_star", "Pending requests", "goal");
        this.register(UNREAD, "minecraft:writable_book", "Unread messages", "task");
    }

    private void register(final String id, final String icon, final String title, final String frame) {
        final NamespacedKey key = new NamespacedKey("friendsystem", id);
        if (Bukkit.getAdvancement(key) != null) {
            return;
        }
        final String json = """
            {"criteria":{"%s":{"trigger":"minecraft:impossible"}},"display":{"icon":{"id":"%s"},\
            "title":"%s","description":"","frame":"%s","show_toast":true,"announce_to_chat":false,"hidden":true}}"""
            .formatted(CRITERION, icon, title, frame);
        try {
            Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (final Exception exception) {
            this.logger.log(Level.WARNING, "Could not register the toast advancement " + key, exception);
        }
    }

    /** Shows a toast to a player, on the thread that owns them. */
    public void show(final Player player, final String id) {
        if (!this.enabled || !this.store.settings(player.getUniqueId()).toasts()) {
            return;
        }
        final Advancement advancement = Bukkit.getAdvancement(new NamespacedKey("friendsystem", id));
        if (advancement == null) {
            return;
        }
        final AdvancementProgress progress = player.getAdvancementProgress(advancement);
        progress.revokeCriteria(CRITERION);
        progress.awardCriteria(CRITERION);
    }
}
