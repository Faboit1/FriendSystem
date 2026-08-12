package com.faboit.friendsystem.service;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Friend requests, friendships, blocking and ignoring. */
public final class FriendService {

    /** Outcome of a friend request attempt. */
    public enum AddResult {
        SENT,
        /** They had already requested us, so the request was accepted instead. */
        ACCEPTED,
        ALREADY_FRIENDS,
        ALREADY_SENT,
        EMPTY,
        UNKNOWN_PLAYER,
        SELF,
        BLOCKED_BY_YOU,
        BLOCKED_BY_THEM
    }

    private final Plugin plugin;
    private final DataStore store;
    private final FriendConfig config;
    private final Notifier notifier;
    private final PlayerLookup lookup;
    private final UnreadTags tags;

    public FriendService(final Plugin plugin, final DataStore store, final FriendConfig config,
                         final Notifier notifier, final PlayerLookup lookup, final UnreadTags tags) {
        this.plugin = plugin;
        this.store = store;
        this.config = config;
        this.notifier = notifier;
        this.lookup = lookup;
        this.tags = tags;
    }

    /** Name resolution, shared with callers that only have a typed-in username. */
    public PlayerLookup lookup() {
        return this.lookup;
    }

    /** Sends a friend request to the player with the given name. */
    public AddResult requestFriend(final Player player, final String rawName) {
        final UUID me = player.getUniqueId();
        if (rawName == null || rawName.isBlank()) {
            return AddResult.EMPTY;
        }
        final PlayerLookup.Resolved target = this.lookup.resolve(rawName);
        if (target == null) {
            return AddResult.UNKNOWN_PLAYER;
        }
        final UUID other = target.uuid();
        if (other.equals(me)) {
            return AddResult.SELF;
        }
        if (this.store.areFriends(me, other)) {
            return AddResult.ALREADY_FRIENDS;
        }
        if (this.store.isBlocked(me, other)) {
            return AddResult.BLOCKED_BY_YOU;
        }
        if (this.store.isBlocked(other, me)) {
            return AddResult.BLOCKED_BY_THEM;
        }
        this.store.rememberName(other, target.name());
        if (this.store.hasRequest(me, other)) {
            this.accept(player, other);
            return AddResult.ACCEPTED;
        }
        if (this.store.hasRequest(other, me)) {
            return AddResult.ALREADY_SENT;
        }

        this.store.addRequest(other, me);
        this.tags.refresh(other);

        final Player online = PlayerLookup.online(other);
        if (online != null) {
            final String myName = this.store.name(me);
            Scheduling.onPlayer(this.plugin, online, receiver -> {
                this.notifier.sound(receiver, this.config.soundRequest(), 0.7f, 1.4f);
                this.notifier.ambient(other, receiver, "<aqua>👤 " + myName
                    + " sent you a friend request!</aqua> <dark_gray>/friends</dark_gray>");
                this.notifier.toasts().show(receiver, ToastService.REQUEST);
            });
        }
        return AddResult.SENT;
    }

    /** Action-bar feedback shared by the dialog and the {@code /friends add} command. */
    public void feedback(final Player player, final AddResult result, final String name) {
        switch (result) {
            case SENT -> this.notifier.feedback(player, "<green>Friend request sent to " + name + "!</green>");
            case ALREADY_FRIENDS -> this.notifier.feedback(player, "<yellow>You're already friends.</yellow>");
            case ALREADY_SENT -> this.notifier.feedback(player, "<yellow>Request already sent to " + name + ".</yellow>");
            case UNKNOWN_PLAYER -> this.notifier.feedback(player,
                "<red>No player named '" + name + "' has joined this server.</red>");
            case SELF -> this.notifier.feedback(player, "<yellow>You can't add yourself.</yellow>");
            case BLOCKED_BY_YOU -> this.notifier.feedback(player,
                "<red>You have this player blocked — unblock them first.</red>");
            case BLOCKED_BY_THEM -> this.notifier.feedback(player, "<red>You can't add this player.</red>");
            default -> {
            }
        }
    }

    /** Accepts a pending request (in either direction) and tells both players. */
    public void accept(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        this.store.removeRequest(me, other);
        this.store.removeRequest(other, me);
        this.store.addFriendship(me, other);
        this.tags.refresh(me);
        this.tags.refresh(other);

        final String otherName = this.store.name(other);
        this.notifier.sound(player, this.config.soundAccept(), 1.0f, 1.2f);
        this.notifier.feedback(player, "<green>You are now friends with " + otherName + "!</green>");

        final Player online = PlayerLookup.online(other);
        if (online != null) {
            final String myName = this.store.name(me);
            Scheduling.onPlayer(this.plugin, online, receiver -> {
                this.notifier.sound(receiver, this.config.soundAccept(), 1.0f, 1.2f);
                this.notifier.ambient(other, receiver, "<green>" + myName
                    + " accepted your friend request!</green> <dark_gray>/friends</dark_gray>");
                this.notifier.toasts().show(receiver, ToastService.ACCEPT);
            });
        }
    }

    public void decline(final Player player, final UUID other) {
        this.store.removeRequest(player.getUniqueId(), other);
        this.tags.refresh(player.getUniqueId());
        this.notifier.error(player);
    }

    public void unfriend(final Player player, final UUID other) {
        this.store.removeFriendship(player.getUniqueId(), other);
        this.notifier.sound(player, this.config.soundError(), 1.0f, 0.8f);
        this.notifier.feedback(player, "<red>Removed " + this.store.name(other) + " from your friends.</red>");
    }

    /**
     * Blocks a player: the friendship and any pending requests go away, but the
     * conversation history stays readable for both sides.
     */
    public void block(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        this.store.block(me, other);
        this.store.removeFriendship(me, other);
        this.store.removeRequest(me, other);
        this.store.removeRequest(other, me);
        this.tags.refresh(me);
        this.notifier.sound(player, this.config.soundBlock(), 0.6f, 1.2f);
        this.notifier.feedback(player, "<dark_red>Blocked " + this.store.name(other) + ".</dark_red>");
    }

    public void unblock(final Player player, final UUID other) {
        this.store.unblock(player.getUniqueId(), other);
        this.notifier.click(player);
    }

    /** Flips the ignore flag for a player and returns whether they are now ignored. */
    public boolean toggleIgnore(final Player player, final UUID other) {
        final boolean ignored = this.store.toggleIgnored(player.getUniqueId(), other);
        this.tags.refresh(player.getUniqueId());
        return ignored;
    }
}
