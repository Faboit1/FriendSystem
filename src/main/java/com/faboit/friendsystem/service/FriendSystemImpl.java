package com.faboit.friendsystem.service;

import com.faboit.friendsystem.api.FriendMessage;
import com.faboit.friendsystem.api.FriendRequestResult;
import com.faboit.friendsystem.api.FriendSystemAPI;
import com.faboit.friendsystem.api.MessagePrivacy;
import com.faboit.friendsystem.api.MessageResult;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.Message;
import com.faboit.friendsystem.ui.Navigator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The {@link FriendSystemAPI} implementation, a thin façade over the plugin's own
 * services so that other plugins go through exactly the same code paths as the dialogs
 * and commands do.
 */
public final class FriendSystemImpl implements FriendSystemAPI {

    private final DataStore store;
    private final FriendService friends;
    private final MessageService messages;
    private final Navigator navigator;

    public FriendSystemImpl(final DataStore store, final FriendService friends,
                            final MessageService messages, final Navigator navigator) {
        this.store = store;
        this.friends = friends;
        this.messages = messages;
        this.navigator = navigator;
    }

    // ---------------------------------------------------------------- identity

    @Override
    public Optional<String> getName(final UUID player) {
        return player != null && this.store.hasName(player) ? Optional.of(this.store.name(player)) : Optional.empty();
    }

    @Override
    public Optional<UUID> getUniqueId(final String name) {
        final PlayerLookup.Resolved resolved = this.friends.lookup().resolve(name);
        return resolved == null ? Optional.empty() : Optional.of(resolved.uuid());
    }

    @Override
    public Optional<Instant> getLastSeen(final UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(this.store.lastSeen(player));
    }

    // ------------------------------------------------------------- friendships

    @Override
    public boolean areFriends(final UUID a, final UUID b) {
        return a != null && b != null && this.store.areFriends(a, b);
    }

    @Override
    public boolean areFriendsOfFriends(final UUID a, final UUID b) {
        return a != null && b != null && this.messages.areFriendsOfFriends(a, b);
    }

    @Override
    public Set<UUID> getFriends(final UUID player) {
        return player == null ? Set.of() : this.store.friendsOf(player);
    }

    @Override
    public List<Player> getOnlineFriends(final UUID player) {
        final Set<UUID> all = this.getFriends(player);
        if (all.isEmpty()) {
            return List.of();
        }
        final List<Player> online = new ArrayList<>(all.size());
        for (final UUID uuid : all) {
            final Player friend = Bukkit.getPlayer(uuid);
            if (friend != null && friend.isOnline()) {
                online.add(friend);
            }
        }
        return List.copyOf(online);
    }

    @Override
    public int getFriendCount(final UUID player) {
        return player == null ? 0 : this.store.friendCount(player);
    }

    // ---------------------------------------------------------------- requests

    @Override
    public Set<UUID> getIncomingRequests(final UUID player) {
        return player == null ? Set.of() : this.store.requests(player);
    }

    @Override
    public boolean hasIncomingRequest(final UUID player, final UUID requester) {
        return player != null && requester != null && this.store.hasRequest(player, requester);
    }

    @Override
    public FriendRequestResult sendFriendRequest(final Player requester, final String targetName) {
        return this.friends.requestFriend(requester, targetName);
    }

    @Override
    public FriendRequestResult sendFriendRequest(final Player requester, final UUID target) {
        return this.friends.requestFriend(requester, target);
    }

    @Override
    public boolean acceptFriendRequest(final Player player, final UUID requester) {
        if (requester == null || !this.store.hasRequest(player.getUniqueId(), requester)) {
            return false;
        }
        return this.friends.accept(player, requester);
    }

    @Override
    public boolean declineFriendRequest(final Player player, final UUID requester) {
        return requester != null && this.friends.decline(player, requester);
    }

    @Override
    public boolean removeFriend(final Player player, final UUID friend) {
        return friend != null && this.friends.unfriend(player, friend);
    }

    // ------------------------------------------------------- blocking/ignoring

    @Override
    public boolean hasBlocked(final UUID player, final UUID target) {
        return player != null && target != null && this.store.isBlocked(player, target);
    }

    @Override
    public boolean isBlockedEitherWay(final UUID a, final UUID b) {
        return this.hasBlocked(a, b) || this.hasBlocked(b, a);
    }

    @Override
    public Set<UUID> getBlocked(final UUID player) {
        return player == null ? Set.of() : this.store.blocked(player);
    }

    @Override
    public boolean block(final Player player, final UUID target) {
        return target != null && !target.equals(player.getUniqueId()) && this.friends.block(player, target);
    }

    @Override
    public boolean unblock(final Player player, final UUID target) {
        return target != null && this.friends.unblock(player, target);
    }

    @Override
    public boolean isIgnoring(final UUID player, final UUID target) {
        return player != null && target != null && this.store.isIgnored(player, target);
    }

    // --------------------------------------------------------------- messaging

    @Override
    public boolean canMessage(final Player sender, final UUID receiver) {
        if (receiver == null) {
            return false;
        }
        return !this.isBlockedEitherWay(sender.getUniqueId(), receiver) && this.messages.canDm(sender, receiver);
    }

    @Override
    public MessageResult sendMessage(final Player sender, final UUID receiver, final String content) {
        return this.messages.deliver(sender, receiver, content);
    }

    @Override
    public List<FriendMessage> getConversation(final UUID a, final UUID b) {
        if (a == null || b == null) {
            return List.of();
        }
        final List<Message> stored = this.store.messages(DataStore.convoKey(a, b));
        final List<FriendMessage> result = new ArrayList<>(stored.size());
        for (final Message message : stored) {
            final UUID receiver = message.sender().equals(a) ? b : a;
            result.add(new FriendMessage(message.sender(), receiver, message.text(), message.sentAt()));
        }
        return List.copyOf(result);
    }

    @Override
    public int getUnreadCount(final UUID player) {
        return player == null ? 0 : this.store.totalUnread(player, true);
    }

    @Override
    public int getUnreadCount(final UUID player, final UUID sender) {
        return player == null || sender == null ? 0 : this.store.unread(player, sender);
    }

    @Override
    public Map<UUID, Integer> getUnreadCounts(final UUID player) {
        return player == null ? Map.of() : this.store.unreadMap(player);
    }

    @Override
    public void markRead(final UUID player, final UUID other) {
        if (player != null && other != null) {
            this.messages.markRead(player, other);
        }
    }

    @Override
    public Optional<UUID> getLastConversation(final UUID player) {
        return player == null ? Optional.empty() : Optional.ofNullable(this.store.lastConvo(player));
    }

    // ---------------------------------------------------------------- settings

    @Override
    public MessagePrivacy getMessagePrivacy(final UUID player) {
        return player == null ? MessagePrivacy.ANYONE : MessagePrivacy.fromKey(this.store.settings(player).dmPrivacy());
    }

    @Override
    public void setMessagePrivacy(final UUID player, final MessagePrivacy privacy) {
        if (player == null) {
            return;
        }
        this.store.settings(player).dmPrivacy((privacy == null ? MessagePrivacy.ANYONE : privacy).key());
        this.store.persistPlayer(player);
    }

    @Override
    public String getMessageColor(final UUID player) {
        return player == null ? "white" : this.store.settings(player).color();
    }

    // ---------------------------------------------------------------------- ui

    @Override
    public void openMenu(final Player player) {
        this.navigator.menu(player);
    }

    @Override
    public void openFriendList(final Player player) {
        this.navigator.friends(player);
    }

    @Override
    public void openConversation(final Player player, final UUID other) {
        if (other != null) {
            this.navigator.chat(player, other);
        }
    }
}
