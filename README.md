# FriendSystem

Friends, direct messages and player blocking for **Paper 26.2+**, with full **Folia**
support. Everything runs through Minecraft's built-in dialog screens — no inventory
GUIs, no resource pack.

This is a Java port of the `friendsystem.sk` Skript that ran on
[CheeseSMP](https://cheesesmp.top). The report system and the team system have been
dropped; friends and messaging are kept feature-for-feature, and storage moved from
Skript variables to a real database.

## Features

- **Friends menu** (`/friends`) with friend count, unread badge, requests, blocked
  players and settings.
- **Friends list** with search, paging, and two view modes — plain buttons, or cards
  with player heads and clickable names. Unread conversations sort first, then online
  friends, then everyone else.
- **Direct messages** to anyone, friend or not, from a dialog or straight from the
  command line. Messages are mirrored into normal chat for both players, and each
  player picks the colour their own messages appear in.
- **DM inbox** listing every conversation with unread messages.
- **Friend page** per friend: message, ignore, unfriend, block, per-friend
  auto-accept TPA toggles, and optional pay/teleport/stats buttons that hand over to
  your economy, teleport and stats plugins.
- **Blocking** removes the friendship and stops new messages both ways, while leaving
  the existing conversation readable.
- **Privacy**: each player chooses who may message them — anyone, friends of friends,
  friends only, or no one. Staff (`friendsystem.mod`, `friendsystem.owner`) bypass it.
- **Toasts, sounds, action-bar notifications and unread reminders**, each toggleable
  per player.
- **`unread` scoreboard tag** on players with unread messages or pending requests, so
  `@a[tag=unread]` keeps working.
- **PlaceholderAPI** support.

## Requirements

| | |
|---|---|
| Server | Paper or Folia **26.2+** |
| Java | **25** |
| Optional | PlaceholderAPI |

The JDBC drivers and the connection pool are declared as plugin libraries, so Paper
downloads them into its own `libraries/` folder the first time the plugin starts.
That first start needs internet access; after that they are cached.

## Installation

1. Drop `FriendSystem-<version>.jar` into `plugins/`.
2. Start the server once — `plugins/FriendSystem/config.yml` is generated and SQLite
   works with no further setup.
3. Edit the config if you want MySQL/MariaDB or different integration commands, then
   restart.

## Storage

SQLite is the default and needs no configuration:

```yaml
storage:
  type: sqlite
  sqlite:
    file: friendsystem.db
```

For a shared database across several servers, switch the type and fill in the
`remote` block:

```yaml
storage:
  type: mariadb        # or: mysql
  remote:
    host: localhost
    port: 3306
    database: friendsystem
    username: friendsystem
    password: "secret"
    properties: "useSSL=false&characterEncoding=utf8"
```

Tables (`fs_players`, `fs_friends`, `fs_requests`, `fs_blocked`, `fs_ignored`,
`fs_unread`, `fs_messages`) are created automatically. Storage settings are read at
startup, so changing them needs a restart.

All state is held in memory and written back on a background thread, which is what
keeps dialog clicks free of database work — important on Folia, where blocking a
region thread stalls a whole slice of the world.

## Commands

| Command | Aliases | What it does |
|---|---|---|
| `/friends` | `/friend`, `/fr` | Opens the menu |
| `/friends add <name>` | | Sends a friend request |
| `/friends remove <name>` | `unfriend` | Removes a friend |
| `/friends block\|unblock <name>` | | Blocks / unblocks a player |
| `/friends accept\|deny <name>` | `decline` | Answers a friend request |
| `/friends requests\|blocked\|settings` | | Opens that dialog directly |
| `/message <player> [text]` | `/dm`, `/msg`, `/whisper`, `/w` | Opens a chat, or sends a message |
| `/reply [text]` | `/r` | Continues the most recent conversation |
| `/fsopen <uuid>` | | Internal, used by the clickable cards |

Tab completion suggests the right names for each subcommand: friends for `remove`,
blocked players for `unblock`, incoming requests for `accept` and `deny`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `friendsystem.use` | everyone | Use the menu and messaging commands |
| `friendsystem.mod` | op | Bypass message privacy settings |
| `friendsystem.owner` | op | Bypass message privacy settings |
| `friendsystem.color.<colour>` | op | Use one message colour |
| `friendsystem.color.*` | op | Use every message colour |

The legacy `chat.color.<colour>` and `cheeseplus` permissions from the Skript version
are still honoured, so existing rank setups keep working.

## Placeholders

| Placeholder | Value |
|---|---|
| `%friendsystem_friends%` | Total friends |
| `%friendsystem_online_friends%` | Friends currently online |
| `%friendsystem_offline_friends%` | Friends currently offline |
| `%friendsystem_unread_messages%` | Unread direct messages |
| `%friendsystem_incoming_friend_request%` | Pending friend requests |

## Integrations

The pay, teleport, stats and auto-accept-TPA buttons run commands from other plugins.
They are enabled by default with the commands the Skript used; disable the ones your
server doesn't have and the buttons disappear:

```yaml
integrations:
  pay:
    enabled: true
    command: "pay %player%"
  auto-accept-tpa:
    enabled: true
    command: "autoaccepttpafrom %player% %value%"
```

`%player%` is the friend's name and `%value%` is `true`/`false` for the toggles.

## Building

```bash
mvn -B clean package
```

The jar lands in `target/FriendSystem-<version>.jar`. Requires JDK 25.

GitHub Actions builds every push and pull request and uploads the jar as an artifact
(`.github/workflows/build.yml`). Pushing a `v*` tag additionally publishes a release
with the jar attached.

## Differences from the Skript version

- **Removed**: the report system (`/report`, `/reports`) and the team system
  (`/team`, `/teamchat`, team homes and team chat), along with their toasts and the
  `%friendsystem_team_name%` placeholder.
- **Storage**: SQLite/MySQL/MariaDB instead of Skript variables. There is no
  automatic import of old variable data.
- **Folia**: notifications are dispatched through each player's own scheduler and all
  database work happens off the region threads.
- **Name lookups** never hit Mojang's API — a player must have joined the server
  before (their name is taken from the plugin's own cache or the server's profile
  cache).
- **Added**: a short summary on join when friend requests or unread messages are
  waiting, which reuses the two toasts the Skript registered but never fired.
