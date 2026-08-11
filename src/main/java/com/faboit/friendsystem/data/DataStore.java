package com.faboit.friendsystem.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The plugin's state, held in memory and written back to the database on a single
 * background thread.
 *
 * <p>Dialogs are built on the region thread that handled the click, so nothing here
 * may block: every read hits a concurrent map, and every mutation queues its SQL
 * on {@link #dbThread}. Using one thread (rather than a pool) keeps writes in the
 * order they were made, which matters for pairs like "delete request, insert friend".</p>
 */
public final class DataStore {

    /** Per-friendship toggles, stored on the owner's row so both sides can differ. */
    public static final class FriendFlags {
        private volatile boolean autoTpa;
        private volatile boolean autoTpaHere;

        FriendFlags(final boolean autoTpa, final boolean autoTpaHere) {
            this.autoTpa = autoTpa;
            this.autoTpaHere = autoTpaHere;
        }

        public boolean autoTpa() {
            return this.autoTpa;
        }

        public boolean autoTpaHere() {
            return this.autoTpaHere;
        }
    }

    private final Database database;
    private final Logger logger;
    private final ExecutorService dbThread;
    private final AtomicLong localMessageIds = new AtomicLong(Long.MIN_VALUE / 2);

    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSeen = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastConvo = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, FriendFlags>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> blocked = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Integer>> unread = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    public DataStore(final Database database, final Logger logger) {
        this.database = database;
        this.logger = logger;
        this.dbThread = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "FriendSystem-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Runs a database write off the calling thread, logging anything that goes wrong. */
    private void async(final String what, final SqlTask task) {
        this.dbThread.execute(() -> {
            try {
                task.run();
            } catch (final Exception exception) {
                this.logger.log(Level.SEVERE, "Failed to persist " + what, exception);
            }
        });
    }

    /** Waits for queued writes to finish; called from {@code onDisable}. */
    public void shutdown() {
        this.dbThread.shutdown();
        try {
            if (!this.dbThread.awaitTermination(15, TimeUnit.SECONDS)) {
                this.logger.warning("Timed out waiting for pending database writes.");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SqlTask {
        void run() throws Exception;
    }

    // ------------------------------------------------------------ bulk loading

    void loadPlayer(final UUID uuid, final String name, final Instant seen, final PlayerSettings loaded) {
        if (name != null) {
            this.names.put(uuid, name);
        }
        if (seen != null) {
            this.lastSeen.put(uuid, seen);
        }
        this.settings.put(uuid, loaded);
    }

    void loadLastConvo(final UUID uuid, final UUID other) {
        this.lastConvo.put(uuid, other);
    }

    void loadFriend(final UUID owner, final UUID friend, final boolean autoTpa, final boolean autoTpaHere) {
        this.friends.computeIfAbsent(owner, key -> new ConcurrentHashMap<>()).put(friend, new FriendFlags(autoTpa, autoTpaHere));
    }

    void loadRequest(final UUID target, final UUID requester) {
        this.requests.computeIfAbsent(target, key -> ConcurrentHashMap.newKeySet()).add(requester);
    }

    void loadBlocked(final UUID owner, final UUID target) {
        this.blocked.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet()).add(target);
    }

    void loadIgnored(final UUID owner, final UUID target) {
        this.ignored.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet()).add(target);
    }

    void loadUnread(final UUID owner, final UUID other, final int amount) {
        if (amount > 0) {
            this.unread.computeIfAbsent(owner, key -> new ConcurrentHashMap<>()).put(other, amount);
        }
    }

    void loadMessage(final String convo, final Message message) {
        this.conversations.computeIfAbsent(convo, key -> new CopyOnWriteArrayList<>()).add(message);
    }

    // ---------------------------------------------------------------- identity

    /** The last known username of a player, falling back to their UUID like the Skript did. */
    public String name(final UUID uuid) {
        final String name = this.names.get(uuid);
        return name == null ? uuid.toString() : name;
    }

    public boolean hasName(final UUID uuid) {
        return this.names.containsKey(uuid);
    }

    public void rememberName(final UUID uuid, final String name) {
        if (name == null || name.equals(this.names.get(uuid))) {
            return;
        }
        this.names.put(uuid, name);
        this.persistPlayer(uuid);
    }

    /** Looks a player up by cached name, so offline friends resolve without a Mojang call. */
    public UUID lookupByName(final String name) {
        for (final Map.Entry<UUID, String> entry : this.names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Instant lastSeen(final UUID uuid) {
        return this.lastSeen.get(uuid);
    }

    public void markSeen(final UUID uuid) {
        this.lastSeen.put(uuid, Instant.now());
        this.persistPlayer(uuid);
    }

    public PlayerSettings settings(final UUID uuid) {
        return this.settings.computeIfAbsent(uuid, key -> new PlayerSettings());
    }

    /** Writes the player row (name, last seen, settings, reply target) in one statement. */
    public void persistPlayer(final UUID uuid) {
        final String name = this.names.get(uuid);
        final Instant seen = this.lastSeen.get(uuid);
        final PlayerSettings snapshot = this.settings(uuid);
        final UUID convo = this.lastConvo.get(uuid);
        this.async("player " + uuid, () -> this.database.savePlayer(uuid, name, seen, snapshot, convo));
    }

    public UUID lastConvo(final UUID uuid) {
        return this.lastConvo.get(uuid);
    }

    public void lastConvo(final UUID uuid, final UUID other) {
        this.lastConvo.put(uuid, other);
        this.persistPlayer(uuid);
    }

    // ----------------------------------------------------------------- friends

    public boolean areFriends(final UUID a, final UUID b) {
        final Map<UUID, FriendFlags> map = this.friends.get(a);
        return map != null && map.containsKey(b);
    }

    public Set<UUID> friendsOf(final UUID uuid) {
        final Map<UUID, FriendFlags> map = this.friends.get(uuid);
        return map == null ? Set.of() : Set.copyOf(map.keySet());
    }

    public int friendCount(final UUID uuid) {
        final Map<UUID, FriendFlags> map = this.friends.get(uuid);
        return map == null ? 0 : map.size();
    }

    public void addFriendship(final UUID a, final UUID b) {
        this.friends.computeIfAbsent(a, key -> new ConcurrentHashMap<>()).put(b, new FriendFlags(false, false));
        this.friends.computeIfAbsent(b, key -> new ConcurrentHashMap<>()).put(a, new FriendFlags(false, false));
        this.async("friendship " + a + '/' + b, () -> {
            this.database.saveFriend(a, b, false, false);
            this.database.saveFriend(b, a, false, false);
        });
    }

    public void removeFriendship(final UUID a, final UUID b) {
        final Map<UUID, FriendFlags> first = this.friends.get(a);
        if (first != null) {
            first.remove(b);
        }
        final Map<UUID, FriendFlags> second = this.friends.get(b);
        if (second != null) {
            second.remove(a);
        }
        this.async("unfriend " + a + '/' + b, () -> {
            this.database.deleteFriend(a, b);
            this.database.deleteFriend(b, a);
        });
    }

    public FriendFlags flags(final UUID owner, final UUID friend) {
        final Map<UUID, FriendFlags> map = this.friends.get(owner);
        final FriendFlags found = map == null ? null : map.get(friend);
        return found == null ? new FriendFlags(false, false) : found;
    }

    /** Flips one of the auto-accept toggles and returns its new value. */
    public boolean toggleAutoTpa(final UUID owner, final UUID friend, final boolean here) {
        final Map<UUID, FriendFlags> map = this.friends.get(owner);
        final FriendFlags found = map == null ? null : map.get(friend);
        if (found == null) {
            return false;
        }
        final boolean updated;
        if (here) {
            updated = !found.autoTpaHere;
            found.autoTpaHere = updated;
        } else {
            updated = !found.autoTpa;
            found.autoTpa = updated;
        }
        this.async("tpa toggle " + owner, () -> this.database.saveFriend(owner, friend, found.autoTpa, found.autoTpaHere));
        return updated;
    }

    // ---------------------------------------------------------------- requests

    public Set<UUID> requests(final UUID target) {
        final Set<UUID> set = this.requests.get(target);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public int requestCount(final UUID target) {
        final Set<UUID> set = this.requests.get(target);
        return set == null ? 0 : set.size();
    }

    public boolean hasRequest(final UUID target, final UUID requester) {
        final Set<UUID> set = this.requests.get(target);
        return set != null && set.contains(requester);
    }

    public void addRequest(final UUID target, final UUID requester) {
        this.requests.computeIfAbsent(target, key -> ConcurrentHashMap.newKeySet()).add(requester);
        final Instant now = Instant.now();
        this.async("request " + requester + " -> " + target, () -> this.database.saveRequest(target, requester, now));
    }

    public void removeRequest(final UUID target, final UUID requester) {
        final Set<UUID> set = this.requests.get(target);
        if (set != null) {
            set.remove(requester);
        }
        this.async("request removal", () -> this.database.deleteRequest(target, requester));
    }

    // ------------------------------------------------------- blocked / ignored

    public boolean isBlocked(final UUID owner, final UUID target) {
        final Set<UUID> set = this.blocked.get(owner);
        return set != null && set.contains(target);
    }

    public Set<UUID> blocked(final UUID owner) {
        final Set<UUID> set = this.blocked.get(owner);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public int blockedCount(final UUID owner) {
        final Set<UUID> set = this.blocked.get(owner);
        return set == null ? 0 : set.size();
    }

    public void block(final UUID owner, final UUID target) {
        this.blocked.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet()).add(target);
        this.async("block", () -> this.database.saveBlocked(owner, target));
    }

    public void unblock(final UUID owner, final UUID target) {
        final Set<UUID> set = this.blocked.get(owner);
        if (set != null) {
            set.remove(target);
        }
        this.async("unblock", () -> this.database.deleteBlocked(owner, target));
    }

    public boolean isIgnored(final UUID owner, final UUID target) {
        final Set<UUID> set = this.ignored.get(owner);
        return set != null && set.contains(target);
    }

    /** Toggles the ignore flag and returns {@code true} when the target is now ignored. */
    public boolean toggleIgnored(final UUID owner, final UUID target) {
        final Set<UUID> set = this.ignored.computeIfAbsent(owner, key -> ConcurrentHashMap.newKeySet());
        if (set.remove(target)) {
            this.async("unignore", () -> this.database.deleteIgnored(owner, target));
            return false;
        }
        set.add(target);
        this.async("ignore", () -> this.database.saveIgnored(owner, target));
        return true;
    }

    // ------------------------------------------------------------------ unread

    public int unread(final UUID owner, final UUID other) {
        final Map<UUID, Integer> map = this.unread.get(owner);
        final Integer amount = map == null ? null : map.get(other);
        return amount == null ? 0 : amount;
    }

    /** Every conversation with unread messages, newest counts included. */
    public Map<UUID, Integer> unreadMap(final UUID owner) {
        final Map<UUID, Integer> map = this.unread.get(owner);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    /** Total unread messages, optionally skipping senders the owner ignores. */
    public int totalUnread(final UUID owner, final boolean skipIgnored) {
        final Map<UUID, Integer> map = this.unread.get(owner);
        if (map == null) {
            return 0;
        }
        int total = 0;
        for (final Map.Entry<UUID, Integer> entry : map.entrySet()) {
            if (skipIgnored && this.isIgnored(owner, entry.getKey())) {
                continue;
            }
            total += entry.getValue();
        }
        return total;
    }

    public void addUnread(final UUID owner, final UUID other) {
        final Map<UUID, Integer> map = this.unread.computeIfAbsent(owner, key -> new ConcurrentHashMap<>());
        final int amount = map.merge(other, 1, Integer::sum);
        this.async("unread", () -> this.database.saveUnread(owner, other, amount));
    }

    public void clearUnread(final UUID owner, final UUID other) {
        final Map<UUID, Integer> map = this.unread.get(owner);
        if (map == null || map.remove(other) == null) {
            return;
        }
        this.async("unread reset", () -> this.database.deleteUnread(owner, other));
    }

    // ---------------------------------------------------------------- messages

    /** Stable conversation id for a pair of players, independent of who speaks first. */
    public static String convoKey(final UUID a, final UUID b) {
        final String first = a.toString();
        final String second = b.toString();
        return first.compareTo(second) <= 0 ? first + '_' + second : second + '_' + first;
    }

    /** The messages of a conversation, oldest first. */
    public List<Message> messages(final String convo) {
        final List<Message> list = this.conversations.get(convo);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void appendMessage(final UUID sender, final UUID receiver, final String text) {
        final String convo = convoKey(sender, receiver);
        final Instant now = Instant.now();
        this.conversations.computeIfAbsent(convo, key -> new CopyOnWriteArrayList<>())
            .add(new Message(this.localMessageIds.incrementAndGet(), sender, text, now));
        this.async("message", () -> this.database.insertMessage(convo, sender, receiver, text, now));
    }

    /** Drops messages older than the cutoff from memory and from the database. */
    public int pruneMessages(final Instant cutoff) {
        int removed = 0;
        for (final Map.Entry<String, List<Message>> entry : this.conversations.entrySet()) {
            final List<Message> list = entry.getValue();
            final List<Message> stale = new ArrayList<>();
            for (final Message message : list) {
                if (message.sentAt().isBefore(cutoff)) {
                    stale.add(message);
                }
            }
            if (!stale.isEmpty()) {
                list.removeAll(stale);
                removed += stale.size();
            }
            if (list.isEmpty()) {
                this.conversations.remove(entry.getKey(), list);
            }
        }
        this.async("message pruning", () -> this.database.deleteMessagesBefore(cutoff));
        return removed;
    }

    /** Every player the store knows about — used by tab completion and placeholders. */
    public Set<String> knownNames() {
        return new HashSet<>(this.names.values());
    }

    /** Names of the given players, sorted for tab completion. */
    public List<String> namesOf(final Set<UUID> uuids) {
        final List<String> result = new ArrayList<>(uuids.size());
        for (final UUID uuid : uuids) {
            result.add(this.name(uuid));
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(result);
    }

    /** Case-insensitive "does this name contain the query" used by the friends search box. */
    public static boolean matches(final String name, final String query) {
        return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
