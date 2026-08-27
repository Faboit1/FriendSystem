package com.faboit.friendsystem.service;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.api.FriendRequestResult;
import com.faboit.friendsystem.api.event.FriendAddEvent;
import com.faboit.friendsystem.api.event.FriendBlockEvent;
import com.faboit.friendsystem.api.event.FriendRemoveEvent;
import com.faboit.friendsystem.api.event.FriendRequestDeclineEvent;
import com.faboit.friendsystem.api.event.FriendRequestSendEvent;
import com.faboit.friendsystem.api.event.FriendUnblockEvent;
import com.faboit.friendsystem.data.DataStore;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Friend requests, friendships, blocking and ignoring. */
public final class FriendService {

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
    public FriendRequestResult requestFriend(final Player player, final String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return FriendRequestResult.EMPTY;
        }
        final PlayerLookup.Resolved target = this.lookup.resolve(rawName);
        if (target == null) {
            return FriendRequestResult.UNKNOWN_PLAYER;
        }
        return this.request(player, target.uuid(), target.name());
    }

    /** Sends a friend request to a player we already have the UUID of. */
    public FriendRequestResult requestFriend(final Player player, final UUID other) {
        if (other == null) {
            return FriendRequestResult.EMPTY;
        }
        final Player online = PlayerLookup.online(other);
        if (online != null) {
            return this.request(player, other, online.getName());
        }
        if (!this.store.hasName(other)) {
            return FriendRequestResult.UNKNOWN_PLAYER;
        }
        return this.request(player, other, this.store.name(other));
    }

    private FriendRequestResult request(final Player player, final UUID other, final String name) {
        final UUID me = player.getUniqueId();
        if (other.equals(me)) {
            return FriendRequestResult.SELF;
        }
        if (this.store.areFriends(me, other)) {
            return FriendRequestResult.ALREADY_FRIENDS;
        }
        if (this.store.isBlocked(me, other)) {
            return FriendRequestResult.BLOCKED_BY_YOU;
        }
        if (this.store.isBlocked(other, me)) {
            return FriendRequestResult.BLOCKED_BY_THEM;
        }
        this.store.rememberName(other, name);
        if (this.store.hasRequest(me, other)) {
            return this.accept(player, other) ? FriendRequestResult.ACCEPTED : FriendRequestResult.CANCELLED;
        }
        if (this.store.hasRequest(other, me)) {
            return FriendRequestResult.ALREADY_SENT;
        }
        if (!Events.call(new FriendRequestSendEvent(player, other, name))) {
            return FriendRequestResult.CANCELLED;
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
        return FriendRequestResult.SENT;
    }

    /** Action-bar feedback shared by the dialog and the {@code /friends add} command. */
    public void feedback(final Player player, final FriendRequestResult result, final String name) {
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

    /**
     * Accepts a pending request (in either direction) and tells both players.
     *
     * @return {@code false} when a plugin cancelled {@link FriendAddEvent}
     */
    public boolean accept(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        if (!Events.call(new FriendAddEvent(player, other))) {
            return false;
        }
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
        return true;
    }

    /** @return {@code false} when there was no request, or a plugin cancelled the event */
    public boolean decline(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        if (!this.store.hasRequest(me, other) || !Events.call(new FriendRequestDeclineEvent(player, other))) {
            return false;
        }
        this.store.removeRequest(me, other);
        this.tags.refresh(me);
        this.notifier.error(player);
        return true;
    }

    /** @return {@code false} when they were not friends, or a plugin cancelled the event */
    public boolean unfriend(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        if (!this.store.areFriends(me, other) || !Events.call(new FriendRemoveEvent(player, other))) {
            return false;
        }
        this.store.removeFriendship(me, other);
        this.notifier.sound(player, this.config.soundError(), 1.0f, 0.8f);
        this.notifier.feedback(player, "<red>Removed " + this.store.name(other) + " from your friends.</red>");
        return true;
    }

    /**
     * Blocks a player: the friendship and any pending requests go away, but the
     * conversation history stays readable for both sides.
     *
     * @return {@code false} when they were already blocked, or a plugin cancelled the event
     */
    public boolean block(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        if (this.store.isBlocked(me, other)
            || !Events.call(new FriendBlockEvent(player, other, this.store.areFriends(me, other)))) {
            return false;
        }
        this.store.block(me, other);
        this.store.removeFriendship(me, other);
        this.store.removeRequest(me, other);
        this.store.removeRequest(other, me);
        this.tags.refresh(me);
        this.notifier.sound(player, this.config.soundBlock(), 0.6f, 1.2f);
        this.notifier.feedback(player, "<dark_red>Blocked " + this.store.name(other) + ".</dark_red>");
        return true;
    }

    /** @return {@code false} when they were not blocked, or a plugin cancelled the event */
    public boolean unblock(final Player player, final UUID other) {
        final UUID me = player.getUniqueId();
        if (!this.store.isBlocked(me, other) || !Events.call(new FriendUnblockEvent(player, other))) {
            return false;
        }
        this.store.unblock(me, other);
        this.notifier.click(player);
        return true;
    }

    /** Flips the ignore flag for a player and returns whether they are now ignored. */
    public boolean toggleIgnore(final Player player, final UUID other) {
        final boolean ignored = this.store.toggleIgnored(player.getUniqueId(), other);
        this.tags.refresh(player.getUniqueId());
        return ignored;
    }
}
