# FriendSystem API

FriendSystem exposes a developer API so other plugins can read friendships, react to
what players do with them, and drive the system themselves — a chat plugin that only
shows messages between friends, a plugin that announces when a friend joins, a teleport
plugin that refuses requests from blocked players, and so on.

Everything public lives in **`com.faboit.friendsystem.api`** and
**`com.faboit.friendsystem.api.event`**. Anything outside those two packages is
internal and can change without notice.

---

## Contents

1. [Depending on FriendSystem](#depending-on-friendsystem)
2. [Getting the API](#getting-the-api)
3. [Threading (read this before Folia)](#threading)
4. [Queries](#queries)
5. [Actions](#actions)
6. [Events](#events)
7. [Examples](#examples)
   - [Friends-only chat](#example-1--friends-only-chat)
   - [Friend join and leave messages](#example-2--friend-join-and-leave-messages)
   - [Blocking-aware teleport requests](#example-3--blocking-aware-teleport-requests)
   - [Filtering or rewriting direct messages](#example-4--filtering-or-rewriting-direct-messages)
   - [Reacting to new friendships](#example-5--reacting-to-new-friendships)
8. [Placeholders](#placeholders)
9. [Stability](#stability)

---

## Depending on FriendSystem

### plugin.yml

```yaml
# The API must be there — your plugin will not load without FriendSystem:
depend: [ FriendSystem ]

# ...or make it optional, and guard your calls with FriendSystemProvider.isAvailable():
softdepend: [ FriendSystem ]
```

`depend` also guarantees FriendSystem enables first, so the API is ready by the time
your `onEnable` runs.

### Build setup

FriendSystem is not on Maven Central. Pick whichever of these suits your build:

**JitPack** — builds straight from the GitHub repository:

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.Faboit1</groupId>
  <artifactId>FriendSystem</artifactId>
  <version>v1.0.0</version> <!-- a release tag, or a commit hash -->
  <scope>provided</scope>
</dependency>
```

```kotlin
// Gradle (Kotlin DSL)
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.Faboit1:FriendSystem:v1.0.0") }
```

**A local jar** — install the release jar into your own Maven repository:

```bash
mvn install:install-file -Dfile=FriendSystem-1.0.0.jar \
  -DgroupId=com.faboit -DartifactId=FriendSystem -Dversion=1.0.0 -Dpackaging=jar
```

```xml
<dependency>
  <groupId>com.faboit</groupId>
  <artifactId>FriendSystem</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

(Those are the coordinates FriendSystem itself builds under, so a jar you built from
source with `mvn install` is already in your local repository under the same name.)

Always use `provided` / `compileOnly`. Never shade FriendSystem into your plugin — you
need the classes of the FriendSystem that is actually running on the server.

---

## Getting the API

Two equivalent ways; use whichever fits your code.

```java
import com.faboit.friendsystem.api.FriendSystemAPI;
import com.faboit.friendsystem.api.FriendSystemProvider;

// 1. Static provider
FriendSystemAPI friends = FriendSystemProvider.get();          // throws if not enabled
boolean present         = FriendSystemProvider.isAvailable();
Optional<FriendSystemAPI> maybe = FriendSystemProvider.find(); // empty if not enabled

// 2. Bukkit's service manager
FriendSystemAPI friends = Bukkit.getServicesManager().load(FriendSystemAPI.class);
```

Look the instance up when you need it rather than caching it in a field, so a
`/reload` or a plugin manager swapping FriendSystem out cannot leave you holding a dead
object.

```java
public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!FriendSystemProvider.isAvailable()) {
            getLogger().warning("FriendSystem is not installed — friend features are off.");
            return;
        }
        getServer().getPluginManager().registerEvents(new MyListener(this), this);
    }
}
```

---

## Threading

FriendSystem runs on Paper **and Folia**, and the API is built for both.

| | Rule |
|---|---|
| **Queries** (everything that only reads) | Safe from **any** thread. All state is held in memory — no database call, no lock, no blocking. Call them from an async chat listener without a second thought. |
| **Actions** (send a message, add/remove a friend, block, open a dialog) | Must run on a **server thread**, and on Folia specifically the region thread that owns the `Player` you pass in. Inside a command, or an event handler for that player, you already are on it. From anywhere else, use `player.getScheduler().run(plugin, task -> ..., null)`. |
| **Events** | Fired synchronously on the thread of the acting player. Handlers may touch that player directly, but must hop to `other.getScheduler()` before touching anyone else — and must not block, or they stall a whole region. |

An action that would fire its event from the wrong thread throws `IllegalStateException`
with an explanatory message rather than corrupting state.

Database writes are queued onto FriendSystem's own background thread, so no API call
ever waits for I/O.

```java
// Doing something to another player from an event handler, the Folia-safe way:
friend.getScheduler().run(plugin, task -> friend.sendMessage(text), null);
```

---

## Queries

Safe from any thread. `player` is always a `UUID`; players who have never joined are
simply unknown to FriendSystem.

### Identity

| Method | Returns |
|---|---|
| `getName(UUID)` | `Optional<String>` — last username seen for that player |
| `getUniqueId(String name)` | `Optional<UUID>` — case-insensitive; never contacts Mojang |
| `getLastSeen(UUID)` | `Optional<Instant>` |

### Friendships

| Method | Returns |
|---|---|
| `areFriends(UUID a, UUID b)` | `boolean` — friendship is always mutual |
| `areFriendsOfFriends(UUID a, UUID b)` | `boolean` — do they share at least one friend |
| `getFriends(UUID)` | `Set<UUID>` — immutable snapshot |
| `getOnlineFriends(UUID)` | `List<Player>` — the friends online right now |
| `getFriendCount(UUID)` | `int` |

### Requests, blocking, ignoring

| Method | Returns |
|---|---|
| `getIncomingRequests(UUID)` | `Set<UUID>` — pending requests *to* this player |
| `hasIncomingRequest(UUID player, UUID requester)` | `boolean` |
| `hasBlocked(UUID player, UUID target)` | `boolean` — one direction |
| `isBlockedEitherWay(UUID a, UUID b)` | `boolean` — the check most plugins want |
| `getBlocked(UUID)` | `Set<UUID>` |
| `isIgnoring(UUID player, UUID target)` | `boolean` — ignored players can still send, the receiver just gets no notification |

### Messaging

| Method | Returns |
|---|---|
| `canMessage(Player sender, UUID receiver)` | `boolean` — applies blocking and the receiver's privacy setting |
| `getConversation(UUID a, UUID b)` | `List<FriendMessage>`, oldest first, within the retention window |
| `getUnreadCount(UUID)` | `int` — total, skipping ignored senders |
| `getUnreadCount(UUID player, UUID sender)` | `int` — from one sender |
| `getUnreadCounts(UUID)` | `Map<UUID, Integer>` |
| `getLastConversation(UUID)` | `Optional<UUID>` — who `/reply` would answer |

`FriendMessage` is a record: `sender()`, `receiver()`, `content()`, `sentAt()`.

### Settings

| Method | Returns |
|---|---|
| `getMessagePrivacy(UUID)` | `MessagePrivacy` — `ANYONE`, `FRIENDS_OF_FRIENDS`, `FRIENDS`, `NOBODY` |
| `getMessageColor(UUID)` | `String` — the MiniMessage colour their own messages use |

---

## Actions

Server thread only (see [Threading](#threading)). Each of these fires the matching
event, so another plugin can cancel it — always check the return value.

| Method | Returns |
|---|---|
| `sendMessage(Player sender, UUID receiver, String content)` | `MessageResult` |
| `sendFriendRequest(Player requester, String targetName)` | `FriendRequestResult` |
| `sendFriendRequest(Player requester, UUID target)` | `FriendRequestResult` |
| `acceptFriendRequest(Player, UUID requester)` | `boolean` |
| `declineFriendRequest(Player, UUID requester)` | `boolean` |
| `removeFriend(Player, UUID friend)` | `boolean` |
| `block(Player, UUID target)` | `boolean` |
| `unblock(Player, UUID target)` | `boolean` |
| `markRead(UUID player, UUID other)` | `void` |
| `setMessagePrivacy(UUID, MessagePrivacy)` | `void` |
| `openMenu(Player)` | `void` — alias of `openFriendList` |
| `openFriendList(Player)` | `void` — the `/friends` dialog |
| `openConversation(Player, UUID other)` | `void` — also marks it read |

These go through exactly the same code as the dialogs and commands: notifications,
sounds, toasts, cooldowns, privacy rules and persistence all behave identically.

**`MessageResult`** — `OK`, `EMPTY`, `SELF`, `COOLDOWN`, `BLOCKED_BY_YOU`,
`BLOCKED_BY_THEM`, `PRIVACY`, `CANCELLED`. `result.successful()` is shorthand for `OK`.

**`FriendRequestResult`** — `SENT`, `ACCEPTED` (they had already asked, so the two are
now friends), `ALREADY_FRIENDS`, `ALREADY_SENT`, `EMPTY`, `UNKNOWN_PLAYER`, `SELF`,
`BLOCKED_BY_YOU`, `BLOCKED_BY_THEM`, `CANCELLED`. `result.successful()` covers `SENT`
and `ACCEPTED`.

---

## Events

All in `com.faboit.friendsystem.api.event`, all synchronous, all extending
`FriendSystemEvent`. Register them like any other Bukkit event.

| Event | Cancellable | Fired when |
|---|---|---|
| `FriendRequestSendEvent` | ✅ | A friend request is about to be stored |
| `FriendRequestDeclineEvent` | ✅ | A pending request is about to be declined |
| `FriendAddEvent` | ✅ | Two players are about to become friends |
| `FriendRemoveEvent` | ✅ | A friendship is about to end through unfriending |
| `FriendBlockEvent` | ✅ | A player is about to be blocked |
| `FriendUnblockEvent` | ✅ | A player is about to be unblocked |
| `FriendMessageEvent` | ✅ | A direct message passed every check and is about to be stored |
| `FriendJoinEvent` | — | A player joined and at least one friend is online |
| `FriendQuitEvent` | — | A player left and at least one friend is still online |

### What each one carries

```java
FriendRequestSendEvent    getRequester() : Player, getTarget() : UUID, getTargetName() : String
FriendRequestDeclineEvent getPlayer()    : Player, getRequester() : UUID
FriendAddEvent            getPlayer()    : Player, getFriend()    : UUID
FriendRemoveEvent         getPlayer()    : Player, getFriend()    : UUID
FriendBlockEvent          getPlayer()    : Player, getTarget()    : UUID, wereFriends() : boolean
FriendUnblockEvent        getPlayer()    : Player, getTarget()    : UUID
FriendMessageEvent        getSender()    : Player, getReceiver()  : UUID,
                          getReceiverPlayer() : Optional<Player>, areFriends() : boolean,
                          getContent()   : String, setContent(String)
FriendJoinEvent           getPlayer()    : Player, getFriends()   : Set<UUID>, getOnlineFriends() : List<Player>
FriendQuitEvent           getPlayer()    : Player, getFriends()   : Set<UUID>, getOnlineFriends() : List<Player>
```

Notes worth knowing:

- Cancelling `FriendRequestSendEvent` or `FriendMessageEvent` produces no player-facing
  message at all, so tell the player why yourself.
- Cancelling `FriendMessageEvent` also leaves the sender's cooldown untouched.
- `FriendRequestSendEvent` does **not** fire when the request is accepted immediately
  because the target had already sent one — that fires `FriendAddEvent` instead.
- Blocking someone ends the friendship too; that path fires `FriendBlockEvent` (with
  `wereFriends()` set), **not** `FriendRemoveEvent`. Listen to both if you want to catch
  every way a friendship can end.
- `FriendJoinEvent` / `FriendQuitEvent` only fire when there is at least one friend
  online to tell. Use Bukkit's own join event if you need every join.

---

## Examples

### Example 1 — friends-only chat

Show a player's chat only to their friends. `AsyncChatEvent` runs off the main thread,
which is fine: queries are safe from any thread.

```java
public final class FriendChatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(final AsyncChatEvent event) {
        final FriendSystemAPI api = FriendSystemProvider.get();
        final UUID speaker = event.getPlayer().getUniqueId();

        event.viewers().removeIf(viewer -> viewer instanceof Player player
            && !player.getUniqueId().equals(speaker)
            && !api.areFriends(speaker, player.getUniqueId()));
    }
}
```

A softer variant — keep global chat, but drop players who blocked the speaker, and add
a friends-only channel triggered by a `!` prefix:

```java
@EventHandler
public void onChat(final AsyncChatEvent event) {
    final FriendSystemAPI api = FriendSystemProvider.get();
    final UUID speaker = event.getPlayer().getUniqueId();
    final boolean friendsOnly = PlainTextComponentSerializer.plainText()
        .serialize(event.message()).startsWith("!");

    event.viewers().removeIf(viewer -> {
        if (!(viewer instanceof Player player) || player.getUniqueId().equals(speaker)) {
            return false;                                    // console and the speaker always see it
        }
        final UUID uuid = player.getUniqueId();
        if (api.isBlockedEitherWay(speaker, uuid)) {
            return true;
        }
        return friendsOnly && !api.areFriends(speaker, uuid);
    });
}
```

### Example 2 — friend join and leave messages

```java
public final class FriendAnnouncer implements Listener {

    private final Plugin plugin;

    public FriendAnnouncer(final Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFriendJoin(final FriendJoinEvent event) {
        this.announce(event.getOnlineFriends(),
            "<green>+</green> <white>" + event.getPlayer().getName() + "</white> <gray>came online</gray>");
    }

    @EventHandler
    public void onFriendQuit(final FriendQuitEvent event) {
        this.announce(event.getOnlineFriends(),
            "<red>-</red> <white>" + event.getPlayer().getName() + "</white> <gray>went offline</gray>");
    }

    /** Every friend may be ticking on a different Folia region, so hop to each one. */
    private void announce(final List<Player> friends, final String miniMessage) {
        final Component line = MiniMessage.miniMessage().deserialize(miniMessage);
        for (final Player friend : friends) {
            friend.getScheduler().run(this.plugin, task -> friend.sendMessage(line), null);
        }
    }
}
```

If you would rather build the message from your own logic — say, only announce to
friends who are in the same world — filter the list before sending; it is a plain
`List<Player>`.

### Example 3 — blocking-aware teleport requests

```java
@EventHandler
public void onTeleportRequest(final MyTpaRequestEvent event) {
    final FriendSystemAPI api = FriendSystemProvider.get();
    final UUID from = event.getSender().getUniqueId();
    final UUID to = event.getTarget().getUniqueId();

    if (api.isBlockedEitherWay(from, to)) {
        event.setCancelled(true);
        event.getSender().sendRichMessage("<red>You can't teleport to that player.</red>");
        return;
    }
    if (api.areFriends(from, to)) {
        event.setCooldown(0);   // friends skip the cooldown
    }
}
```

### Example 4 — filtering or rewriting direct messages

```java
@EventHandler(priority = EventPriority.HIGH)
public void onDirectMessage(final FriendMessageEvent event) {
    if (this.isMuted(event.getSender().getUniqueId())) {
        event.setCancelled(true);
        event.getSender().sendRichMessage("<red>You are muted.</red>");
        return;
    }
    event.setContent(this.profanityFilter.clean(event.getContent()));
}
```

The replacement text is stripped of formatting codes and truncated to the configured
maximum length again after your handler returns, so a rewrite can never inject
MiniMessage into someone else's chat.

### Example 5 — reacting to new friendships

```java
@EventHandler
public void onFriendAdd(final FriendAddEvent event) {
    final Player player = event.getPlayer();
    final UUID friend = event.getFriend();

    if (this.economy.getBalance(player) < 0) {
        event.setCancelled(true);
        player.sendRichMessage("<red>Settle your debts before making friends.</red>");
        return;
    }
    // Acting on the other player: they may be offline, and may be in another region.
    final Player online = Bukkit.getPlayer(friend);
    if (online != null) {
        online.getScheduler().run(this.plugin,
            task -> this.rewards.grantFirstFriendBonus(online), null);
    }
}
```

Driving the system from your own command, rather than listening:

```java
final FriendSystemAPI api = FriendSystemProvider.get();
final FriendRequestResult result = api.sendFriendRequest(player, "Notch");

switch (result) {
    case SENT            -> player.sendRichMessage("<green>Request sent.</green>");
    case ACCEPTED        -> player.sendRichMessage("<green>You are now friends!</green>");
    case UNKNOWN_PLAYER  -> player.sendRichMessage("<red>Never heard of them.</red>");
    case CANCELLED       -> { /* another plugin said no and explained itself */ }
    default              -> player.sendRichMessage("<yellow>That didn't work.</yellow>");
}
```

---

## Placeholders

If you only need friend data inside another plugin's *text*, PlaceholderAPI may be
enough and needs no code at all:

| Placeholder | Value |
|---|---|
| `%friendsystem_friends%` | Total friends |
| `%friendsystem_online_friends%` | Friends currently online |
| `%friendsystem_offline_friends%` | Friends currently offline |
| `%friendsystem_unread_messages%` | Unread direct messages |
| `%friendsystem_incoming_friend_request%` | Pending friend requests |

---

## Stability

- `com.faboit.friendsystem.api` and `com.faboit.friendsystem.api.event` are the public
  surface and follow the plugin's version: no breaking changes within a major version.
- Everything else — `data`, `service`, `ui`, `listener`, `command`, `papi` — is
  internal. It is visible because Bukkit plugins ship as plain jars, not because it is
  supported.
- New enum constants (for example a future `MessageResult`) may be added in a minor
  release, so always give your `switch` statements a `default` branch.
