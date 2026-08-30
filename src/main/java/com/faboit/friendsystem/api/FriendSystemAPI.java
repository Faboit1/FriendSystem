package com.faboit.friendsystem.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * FriendSystem's public API.
 *
 * <p>Obtain an instance through {@link FriendSystemProvider#get()} or from Bukkit's
 * service manager:</p>
 *
 * <pre>{@code
 * FriendSystemAPI friends = Bukkit.getServicesManager().load(FriendSystemAPI.class);
 * }</pre>
 *
 * <h2>Threading</h2>
 *
 * <p>Every <em>query</em> method reads from memory, never from the database, so it is
 * safe to call from any thread — including a Folia region thread, an async chat
 * listener, or a scheduled async task.</p>
 *
 * <p>Every <em>mutation</em> method (sending messages, adding or removing friends,
 * blocking, opening dialogs) fires a Bukkit event and touches the acting player, so it
 * must be called from a server thread. On Folia that means the region thread that owns
 * the {@code Player} passed in — inside a command, an event handler for that player, or
 * a task on {@code player.getScheduler()}. Database writes are queued onto a background
 * thread by the plugin itself, so these calls never block.</p>
 */
public interface FriendSystemAPI {

    // ---------------------------------------------------------------- identity

    /** The last username FriendSystem saw for this player, if they ever joined. */
    Optional<String> getName(UUID player);

    /**
     * Looks a player up by name, case-insensitively. Only players who have joined this
     * server before are known; this never contacts Mojang and never blocks.
     */
    Optional<UUID> getUniqueId(String name);

    /** When the player was last seen, if FriendSystem has recorded it. */
    Optional<Instant> getLastSeen(UUID player);

    // ------------------------------------------------------------- friendships

    /** Whether the two players are friends. Friendship is always mutual. */
    boolean areFriends(UUID a, UUID b);

    /** Whether the two players share at least one friend. */
    boolean areFriendsOfFriends(UUID a, UUID b);

    /** An immutable snapshot of the player's friends. */
    Set<UUID> getFriends(UUID player);

    /**
     * The player's friends who are online right now.
     *
     * <p>On Folia each returned player may be ticking on a different region thread —
     * hop onto {@code friend.getScheduler()} before sending them anything.</p>
     */
    List<Player> getOnlineFriends(UUID player);

    int getFriendCount(UUID player);

    // ---------------------------------------------------------------- requests

    /** Players who have sent this player a friend request that is still pending. */
    Set<UUID> getIncomingRequests(UUID player);

    boolean hasIncomingRequest(UUID player, UUID requester);

    /**
     * Sends a friend request by name, exactly as {@code /friends add <name>} does,
     * including the notification, the toast and the auto-accept when the other player
     * already requested this one.
     */
    FriendRequestResult sendFriendRequest(Player requester, String targetName);

    /** As {@link #sendFriendRequest(Player, String)}, for a known UUID. */
    FriendRequestResult sendFriendRequest(Player requester, UUID target);

    /**
     * Accepts a pending request from {@code requester}.
     *
     * @return {@code false} if there was no such request or a plugin cancelled the event
     */
    boolean acceptFriendRequest(Player player, UUID requester);

    /**
     * Declines a pending request from {@code requester}.
     *
     * @return {@code false} if there was no such request or a plugin cancelled the event
     */
    boolean declineFriendRequest(Player player, UUID requester);

    /**
     * Ends a friendship for both sides.
     *
     * @return {@code false} if they were not friends or a plugin cancelled the event
     */
    boolean removeFriend(Player player, UUID friend);

    // -------------------------------------------------------- blocking/ignoring

    /** Whether {@code player} has blocked {@code target}. */
    boolean hasBlocked(UUID player, UUID target);

    /** Whether either player has blocked the other — the check most plugins want. */
    boolean isBlockedEitherWay(UUID a, UUID b);

    Set<UUID> getBlocked(UUID player);

    /**
     * Blocks a player: any friendship and pending requests between them are dropped,
     * while the existing conversation stays readable.
     *
     * @return {@code false} if they were already blocked or a plugin cancelled the event
     */
    boolean block(Player player, UUID target);

    /** @return {@code false} if they were not blocked or a plugin cancelled the event */
    boolean unblock(Player player, UUID target);

    /**
     * Whether {@code player} is ignoring {@code target}. Ignored players can still send
     * messages, but the receiver gets no chat line, sound, toast or action bar for them.
     */
    boolean isIgnoring(UUID player, UUID target);

    // --------------------------------------------------------------- messaging

    /** Whether the receiver's privacy setting lets this sender message them. */
    boolean canMessage(Player sender, UUID receiver);

    /**
     * Sends a direct message, exactly as {@code /msg} does: cooldown, blocking and
     * privacy are enforced, the message is stored, mirrored into both players' chat and
     * announced with a sound and a toast when the two are friends.
     */
    MessageResult sendMessage(Player sender, UUID receiver, String content);

    /** The stored conversation between two players, oldest message first. */
    List<FriendMessage> getConversation(UUID a, UUID b);

    /** Unread messages waiting for this player, ignoring senders they ignore. */
    int getUnreadCount(UUID player);

    /** Unread messages from one specific sender. */
    int getUnreadCount(UUID player, UUID sender);

    /** Unread counts per sender; an immutable snapshot. */
    Map<UUID, Integer> getUnreadCounts(UUID player);

    /** Clears the unread counter for one conversation, as opening the chat does. */
    void markRead(UUID player, UUID other);

    /** The player {@code /reply} would answer, if any. */
    Optional<UUID> getLastConversation(UUID player);

    // ---------------------------------------------------------------- settings

    MessagePrivacy getMessagePrivacy(UUID player);

    void setMessagePrivacy(UUID player, MessagePrivacy privacy);

    /** The MiniMessage colour the player's own messages are shown in. */
    String getMessageColor(UUID player);

    // ---------------------------------------------------------------------- ui

    /** Opens the {@code /friends} dialog; an alias of {@link #openFriendList(Player)}. */
    void openMenu(Player player);

    /** Opens the friends list dialog. */
    void openFriendList(Player player);

    /** Opens the conversation with another player, which also marks it read. */
    void openConversation(Player player, UUID other);
}
