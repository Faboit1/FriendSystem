package com.faboit.friendsystem.service;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/** Sounds, action bars and chat lines, all honouring the receiver's settings. */
public final class Notifier {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DataStore store;
    private final FriendConfig config;
    private final ToastService toasts;

    public Notifier(final DataStore store, final FriendConfig config, final ToastService toasts) {
        this.store = store;
        this.config = config;
        this.toasts = toasts;
    }

    /** Renders a MiniMessage string. */
    public static Component mm(final String text) {
        return MINI.deserialize(text);
    }

    /**
     * Strips MiniMessage syntax out of player-supplied text so a message can never
     * inject formatting, exactly like the Skript's {@code replace all "<" with ""}.
     */
    public static String sanitize(final String text) {
        return text == null ? "" : text.replace("<", "");
    }

    public void sound(final Player player, final String sound, final float volume, final float pitch) {
        if (!this.store.settings(player.getUniqueId()).sounds()) {
            return;
        }
        player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, volume, pitch));
    }

    public void click(final Player player) {
        this.sound(player, this.config.soundClick(), 1.0f, 1.0f);
    }

    public void error(final Player player) {
        this.sound(player, this.config.soundError(), 1.0f, 0.9f);
    }

    /** Sends an action bar regardless of the player's action-bar preference (direct feedback). */
    public void feedback(final Player player, final String miniMessage) {
        player.sendActionBar(mm(miniMessage));
    }

    /** Sends an action bar only if the receiver left action-bar notifications enabled. */
    public void ambient(final UUID receiver, final Player player, final String miniMessage) {
        if (this.store.settings(receiver).actionBar()) {
            player.sendActionBar(mm(miniMessage));
        }
    }

    public void chat(final Player player, final String miniMessage) {
        player.sendMessage(mm(miniMessage));
    }

    public ToastService toasts() {
        return this.toasts;
    }
}
