package com.faboit.friendsystem.service;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Delivery of direct messages, including privacy rules, blocking and notifications. */
public final class MessageService {

    /** Outcome of a delivery attempt; the caller turns it into feedback or navigation. */
    public enum Delivery {
        OK,
        EMPTY,
        SELF,
        COOLDOWN,
        BLOCKED_BY_YOU,
        BLOCKED_BY_THEM,
        PRIVACY
    }

    private final Plugin plugin;
    private final DataStore store;
    private final FriendConfig config;
    private final Notifier notifier;
    private final SessionManager sessions;
    private final UnreadTags tags;

    public MessageService(final Plugin plugin, final DataStore store, final FriendConfig config,
                          final Notifier notifier, final SessionManager sessions, final UnreadTags tags) {
        this.plugin = plugin;
        this.store = store;
        this.config = config;
        this.notifier = notifier;
        this.sessions = sessions;
        this.tags = tags;
    }

    /** Staff bypass privacy settings, matching the Skript's {@code fs_isStaff}. */
    public static boolean isStaff(final Player player) {
        return player.hasPermission("friendsystem.mod") || player.hasPermission("friendsystem.owner");
    }

    /** True when the two players share at least one friend. */
    public boolean areFriendsOfFriends(final UUID a, final UUID b) {
        for (final UUID mutual : this.store.friendsOf(a)) {
            if (this.store.areFriends(b, mutual)) {
                return true;
            }
        }
        return false;
    }

    /** Applies the receiver's "who can message me" setting. */
    public boolean canDm(final Player sender, final UUID receiver) {
        final UUID me = sender.getUniqueId();
        if (me.equals(receiver)) {
            return false;
        }
        if (isStaff(sender)) {
            return true;
        }
        return switch (this.store.settings(receiver).dmPrivacy()) {
            case com.faboit.friendsystem.data.PlayerSettings.PRIVACY_NONE -> false;
            case com.faboit.friendsystem.data.PlayerSettings.PRIVACY_FRIENDS -> this.store.areFriends(me, receiver);
            case com.faboit.friendsystem.data.PlayerSettings.PRIVACY_FOF ->
                this.store.areFriends(me, receiver) || this.areFriendsOfFriends(me, receiver);
            default -> true;
        };
    }

    /**
     * Stores and delivers a direct message. Works for friends and strangers alike;
     * strangers simply receive it without a sound or toast, like the Skript did.
     */
    public Delivery deliver(final Player sender, final UUID receiver, final String rawText) {
        final UUID me = sender.getUniqueId();
        if (receiver == null) {
            return Delivery.EMPTY;
        }
        if (rawText == null || rawText.isBlank()) {
            return Delivery.EMPTY;
        }
        if (receiver.equals(me)) {
            return Delivery.SELF;
        }
        final Session session = this.sessions.of(me);
        if (session.onCooldown(this.config.cooldownSeconds())) {
            return Delivery.COOLDOWN;
        }
        if (this.store.isBlocked(me, receiver)) {
            return Delivery.BLOCKED_BY_YOU;
        }
        if (this.store.isBlocked(receiver, me)) {
            return Delivery.BLOCKED_BY_THEM;
        }
        if (!this.canDm(sender, receiver)) {
            return Delivery.PRIVACY;
        }

        String text = Notifier.sanitize(rawText).trim();
        if (text.isEmpty()) {
            return Delivery.EMPTY;
        }
        if (text.length() > this.config.maxMessageLength()) {
            text = text.substring(0, this.config.maxMessageLength());
        }

        session.resetShowMore(DataStore.convoKey(me, receiver));
        session.markMessageSent();
        this.store.appendMessage(me, receiver, text);
        this.store.addUnread(receiver, me);
        this.store.lastConvo(me, receiver);
        this.store.lastConvo(receiver, me);
        this.tags.refresh(receiver);

        this.sendChatLines(sender, receiver, text);
        if (this.store.areFriends(me, receiver)) {
            this.notifyReceiver(sender, receiver, text);
        }
        return Delivery.OK;
    }

    /** Mirrors the message into both players' chat, using plain names and no colours. */
    private void sendChatLines(final Player sender, final UUID receiver, final String text) {
        final String senderName = this.store.name(sender.getUniqueId());
        final String receiverName = this.store.name(receiver);
        this.notifier.chat(sender, "<gray>[<white>you</white> <gray>-></gray> <white>"
            + receiverName + "</white>]</gray> <white>" + text + "</white>");

        final Player target = PlayerLookup.online(receiver);
        if (target == null || this.store.isIgnored(receiver, sender.getUniqueId())) {
            return;
        }
        Scheduling.onPlayer(this.plugin, target, player -> this.notifier.chat(player,
            "<gray>[<white>" + senderName + "</white> <gray>-></gray> <white>you</white>]</gray> <white>" + text + "</white>"));
    }

    /** Sound, action bar and toast for a friend's message. */
    private void notifyReceiver(final Player sender, final UUID receiver, final String text) {
        final Player target = PlayerLookup.online(receiver);
        if (target == null || this.store.isIgnored(receiver, sender.getUniqueId())) {
            return;
        }
        final String senderName = this.store.name(sender.getUniqueId());
        Scheduling.onPlayer(this.plugin, target, player -> {
            this.notifier.sound(player, this.config.soundMessage(), 1.0f, 1.6f);
            this.notifier.ambient(receiver, player, "<aqua>✉ " + senderName + ":</aqua> <white>" + text
                + "</white> <dark_gray>/friends</dark_gray>");
            this.notifier.toasts().show(player, ToastService.MESSAGE);
        });
    }

    /** Opening a conversation marks it as read. */
    public void markRead(final UUID reader, final UUID other) {
        this.store.clearUnread(reader, other);
        this.tags.refresh(reader);
    }

    /** Short action-bar feedback used by {@code /msg} and {@code /r}. */
    public void feedback(final Player player, final Delivery delivery, final String targetName) {
        switch (delivery) {
            case COOLDOWN -> this.notifier.feedback(player, "<yellow>Please wait a moment before messaging again.</yellow>");
            case BLOCKED_BY_YOU -> this.notifier.feedback(player,
                "<red>You've blocked " + targetName + " — unblock them to chat.</red>");
            case BLOCKED_BY_THEM -> this.notifier.feedback(player, "<red>Your message could not be delivered.</red>");
            case PRIVACY -> this.notifier.feedback(player,
                "<yellow>" + targetName + " isn't accepting messages right now.</yellow>");
            case SELF -> this.notifier.feedback(player, "<yellow>You can't message yourself.</yellow>");
            default -> {
            }
        }
    }

    /** Removes messages older than the configured retention window. */
    public int prune() {
        return this.store.pruneMessages(Instant.now().minus(this.config.retentionHours(), ChronoUnit.HOURS));
    }

    /** The retention cutoff, used when loading messages at startup. */
    public Instant retentionCutoff() {
        return Instant.now().minus(this.config.retentionHours(), ChronoUnit.HOURS);
    }
}
