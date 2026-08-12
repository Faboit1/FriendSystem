package com.faboit.friendsystem.ui;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.Message;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.PlayerLookup;
import com.faboit.friendsystem.service.Session;
import com.faboit.friendsystem.service.SessionManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Builds every dialog in the plugin.
 *
 * <p>Dialogs are rebuilt from scratch on each open, which is what keeps counters,
 * online dots and unread badges current without any refresh logic.</p>
 */
public final class DialogFactory {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int HEAD_SIZE = 16;

    private final DataStore store;
    private final FriendConfig config;
    private final SessionManager sessions;

    public DialogFactory(final DataStore store, final FriendConfig config, final SessionManager sessions) {
        this.store = store;
        this.config = config;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------- menu

    public Dialog menu(final Player player) {
        final UUID me = player.getUniqueId();
        final int friends = this.store.friendCount(me);
        final int requests = this.store.requestCount(me);
        final int blocked = this.store.blockedCount(me);
        final int unread = this.store.totalUnread(me, false);

        final String friendsLabel = unread > 0
            ? Icons.FRIENDS + " Friends (" + friends + ") (" + unread + ')'
            : Icons.FRIENDS + " Friends (" + friends + ')';

        final List<DialogBody> body = List.of(this.card(me,
            "<white><b>" + this.store.name(me) + "</b></white><newline><gray>"
                + friends + " friends • " + unread + " unread</gray>"));

        final List<ActionButton> actions = List.of(
            button(friendsLabel, null, 320, Routes.key(Routes.FRIENDS)),
            button(Icons.MAIL + " Requests (" + requests + ')', null, 320, Routes.key(Routes.REQUESTS)),
            button(Icons.PLUS + " Add Friend", null, 320, Routes.key(Routes.ADD_FRIEND)),
            button("<red>" + Icons.BLOCK + " Blocked (" + blocked + ")</red>", null, 320, Routes.key(Routes.BLOCKED)),
            button(Icons.SETTINGS + " Settings", null, 320, Routes.key(Routes.SETTINGS)));

        return build(base("Friends & Messages", "Friends", body, List.of()),
            DialogType.multiAction(actions, null, 1));
    }

    // ---------------------------------------------------------------- friends

    public Dialog friends(final Player player) {
        final UUID me = player.getUniqueId();
        final Session session = this.sessions.of(me);
        final PlayerSettings settings = this.store.settings(me);
        final String query = session.search();

        final List<UUID> ordered = this.orderedFriends(me, query);
        final int total = ordered.size();
        final int perPage = settings.friendsPerPage();
        final int pages = Math.max(1, (int) Math.ceil(total / (double) perPage));
        final int page = Math.clamp(session.page(), 1, pages);
        session.page(page);
        final int first = (page - 1) * perPage;
        final int last = Math.min(page * perPage, total);
        final List<UUID> shown = first >= last ? List.of() : ordered.subList(first, last);

        final int dmUnread = this.store.totalUnread(me, true);
        final String dmLabel = dmUnread > 0 ? Icons.MAIL + " DMs (" + dmUnread + ')' : Icons.MAIL + " DMs";

        final List<DialogBody> body = new ArrayList<>();
        if (total == 0) {
            body.add(DialogBody.plainMessage(
                mm("<gray>No friends to show. Use <white>Add Friend</white> on the menu.</gray>"), 320));
        } else if (settings.cards()) {
            for (final UUID friend : shown) {
                body.add(this.friendCard(me, friend));
            }
        }

        final List<DialogInput> inputs = List.of(DialogInput.text(
            Routes.INPUT_QUERY, 300, Component.text("Search"), false, query == null ? "" : query, 16, null));

        final List<ActionButton> actions = new ArrayList<>();
        actions.add(button(Icons.SEARCH + " Search", null, 155, Routes.key(Routes.SEARCH_SUBMIT)));
        actions.add(button(Icons.CROSS + " Clear", null, 155, Routes.key(Routes.SEARCH_CLEAR)));
        actions.add(button(dmLabel,
            "<light_purple><b>Direct Messages</b></light_purple><newline><gray>View everyone with unread messages.</gray>",
            155, Routes.key(Routes.DMS)));

        if (!settings.cards()) {
            for (final UUID friend : shown) {
                final int unread = this.store.unread(me, friend);
                final String name = this.store.name(friend);
                final String label = Icons.dot(PlayerLookup.isOnline(friend)) + name + (unread > 0 ? " (" + unread + ')' : "");
                actions.add(button(label, this.friendTooltip(me, friend), 155, Routes.key(Routes.FRIEND_PAGE, friend)));
            }
        }
        if (pages > 1) {
            actions.add(button(Icons.PREV + " Prev", null, 155, Routes.key(Routes.PAGE_PREV)));
            actions.add(button("Next " + Icons.NEXT, null, 155, Routes.key(Routes.PAGE_NEXT)));
        }

        return build(base("Your Friends (" + page + '/' + pages + ')', "Friends", body, inputs),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.MENU)), 3));
    }

    /** Unread conversations first, then online friends, then everyone else. */
    private List<UUID> orderedFriends(final UUID me, final String query) {
        final List<UUID> unread = new ArrayList<>();
        final List<UUID> online = new ArrayList<>();
        final List<UUID> offline = new ArrayList<>();
        for (final UUID friend : this.store.friendsOf(me)) {
            final String name = this.store.name(friend);
            if (query != null && !DataStore.matches(name, query)) {
                continue;
            }
            if (this.store.unread(me, friend) > 0) {
                unread.add(friend);
            } else if (PlayerLookup.isOnline(friend)) {
                online.add(friend);
            } else {
                offline.add(friend);
            }
        }
        final Comparator<UUID> byName = Comparator.comparing((UUID uuid) -> this.store.name(uuid), String.CASE_INSENSITIVE_ORDER);
        unread.sort(byName);
        online.sort(byName);
        offline.sort(byName);
        final List<UUID> ordered = new ArrayList<>(unread.size() + online.size() + offline.size());
        ordered.addAll(unread);
        ordered.addAll(online);
        ordered.addAll(offline);
        return ordered;
    }

    /** A head plus a clickable name, used by the "cards" friend view. */
    private DialogBody friendCard(final UUID me, final UUID friend) {
        final String name = this.store.name(friend);
        final int unread = this.store.unread(me, friend);
        final Component label = Component.empty()
            .append(mm(PlayerLookup.isOnline(friend) ? "<green>●</green> " : "<red>●</red> "))
            .append(mm("<white><b>" + name + "</b></white>"))
            .append(unread > 0 ? mm(" <yellow>(" + unread + ")</yellow>") : Component.empty())
            .hoverEvent(HoverEvent.showText(mm(this.friendTooltip(me, friend))))
            .clickEvent(ClickEvent.runCommand("/fsopen " + friend));
        final Component contents = Component.empty()
            .append(label)
            .append(Component.newline())
            .append(mm(this.lastSeenText(friend)));
        return DialogBody.item(this.head(friend), DialogBody.plainMessage(contents, 260),
            true, false, HEAD_SIZE, HEAD_SIZE);
    }

    // -------------------------------------------------------------------- DMs

    public Dialog directMessages(final Player player) {
        final UUID me = player.getUniqueId();
        final List<UUID> conversations = new ArrayList<>();
        for (final Map.Entry<UUID, Integer> entry : this.store.unreadMap(me).entrySet()) {
            if (entry.getValue() > 0 && !this.store.isIgnored(me, entry.getKey())) {
                conversations.add(entry.getKey());
            }
        }
        conversations.sort(Comparator.comparing((UUID uuid) -> this.store.name(uuid), String.CASE_INSENSITIVE_ORDER));

        final List<DialogBody> body = conversations.isEmpty()
            ? List.of(DialogBody.plainMessage(mm("<gray>No unread messages — you're all caught up! 🎉</gray>"), 320))
            : List.of();

        final List<ActionButton> actions = new ArrayList<>();
        if (conversations.isEmpty()) {
            // A multi-action dialog always needs at least one button to be worth opening.
            actions.add(button(Icons.PREV + " Back to friends", null, 320, Routes.key(Routes.FRIENDS)));
        }
        for (final UUID other : conversations) {
            final String name = this.store.name(other);
            final int unread = this.store.unread(me, other);
            final String tag = this.store.areFriends(me, other) ? "" : " (DM)";
            actions.add(button(
                Icons.dot(PlayerLookup.isOnline(other)) + name + tag + " (" + unread + ')',
                "<white><b>" + name + "</b></white><newline><yellow>" + unread
                    + " unread message(s)</yellow><newline><gray>Click to open the chat</gray>",
                320, Routes.key(Routes.DM_OPEN_CHAT, other)));
        }

        return build(base("Direct Messages (" + conversations.size() + ')', "DMs", body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.FRIENDS)), 1));
    }

    // ------------------------------------------------------------ friend page

    public Dialog friendPage(final Player player, final UUID friend) {
        final UUID me = player.getUniqueId();
        final String name = this.store.name(friend);
        final int unread = this.store.unread(me, friend);
        final boolean online = PlayerLookup.isOnline(friend);
        final DataStore.FriendFlags flags = this.store.flags(me, friend);

        final List<DialogBody> body = List.of(this.card(friend,
            "<white><b>" + name + "</b></white><newline>"
                + (online ? "<green>● Online</green>" : "<red>● Offline</red>")
                + "<newline>" + this.lastSeenText(friend)));

        final List<ActionButton> actions = new ArrayList<>();
        actions.add(button(unread > 0 ? Icons.MAIL + " Message (" + unread + ')' : Icons.MAIL + " Message",
            null, 155, Routes.key(Routes.OPEN_CHAT, friend)));
        if (this.config.pay().enabled()) {
            actions.add(button(Icons.COIN + " Pay", null, 155, Routes.key(Routes.PAY, friend)));
        }
        if (this.config.teleport().enabled()) {
            actions.add(button(Icons.ROCKET + " Teleport", null, 155, Routes.key(Routes.TELEPORT, friend)));
        }
        if (this.config.stats().enabled()) {
            actions.add(button(Icons.STATS + " Stats", null, 155, Routes.key(Routes.STATS, friend)));
        }
        actions.add(button(this.store.isIgnored(me, friend) ? Icons.BELL_ON + " Unignore" : Icons.BELL_OFF + " Ignore",
            null, 155, Routes.key(Routes.IGNORE, friend)));
        if (this.config.autoAcceptTpa().enabled()) {
            actions.add(button(Icons.toggle(flags.autoTpa()) + " Auto-Accept TPA: " + Icons.onOff(flags.autoTpa()),
                null, 155, Routes.key(Routes.AUTO_TPA, friend)));
        }
        if (this.config.autoAcceptTpaHere().enabled()) {
            actions.add(button(
                Icons.toggle(flags.autoTpaHere()) + " Auto-Accept TPA-Here: " + Icons.onOff(flags.autoTpaHere()),
                null, 155, Routes.key(Routes.AUTO_TPA_HERE, friend)));
        }
        actions.add(button("<red>" + Icons.CROSS + " Unfriend</red>", null, 155, Routes.key(Routes.UNFRIEND, friend)));
        actions.add(button("<dark_red>" + Icons.BLOCK + " Block</dark_red>", null, 155, Routes.key(Routes.BLOCK, friend)));

        return build(base(name, null, body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.FRIENDS)), 2));
    }

    // ------------------------------------------------------------------- chat

    public Dialog chat(final Player player, final UUID friend, final String prefill, final String warning) {
        final UUID me = player.getUniqueId();
        final String convo = DataStore.convoKey(me, friend);
        final Session session = this.sessions.of(me);
        final String name = this.store.name(friend);
        final PlayerSettings settings = this.store.settings(me);

        final List<Message> messages = this.store.messages(convo);
        final int shown = settings.maxMessages() + session.showMore(convo);
        final int from = Math.max(0, messages.size() - shown);

        final boolean iBlocked = this.store.isBlocked(me, friend);
        final boolean theyBlocked = this.store.isBlocked(friend, me);
        final boolean canSpeak = !iBlocked && !theyBlocked;
        final boolean isFriend = this.store.areFriends(me, friend);

        final String myColor = settings.color();
        final String theirColor = this.store.settings(friend).color();
        final ItemStack myHead = this.head(me);
        final ItemStack theirHead = this.head(friend);

        final List<DialogBody> body = new ArrayList<>();
        if (warning != null && !warning.isEmpty()) {
            body.add(DialogBody.plainMessage(mm("<red>" + warning + "</red>"), 300));
        }
        if (iBlocked) {
            body.add(DialogBody.plainMessage(mm("<dark_red>🚫 You blocked " + name
                + ". Old messages stay visible, but neither of you can send new ones. Unblock to chat again.</dark_red>"), 300));
        } else if (theyBlocked) {
            body.add(DialogBody.plainMessage(mm("<red>You can't message " + name + " right now.</red>"), 300));
        }
        if (messages.isEmpty()) {
            body.add(DialogBody.plainMessage(mm("<dark_gray><i>No messages yet — say hello! 👋</i></dark_gray>"), 300));
        }
        for (final Message message : messages.subList(from, messages.size())) {
            final boolean mine = message.sender().equals(me);
            final String wrapped = Colors.wrap(mine ? myColor : theirColor, mine, message.text());
            body.add(DialogBody.item(mine ? myHead : theirHead,
                DialogBody.plainMessage(mm(wrapped + "  <dark_gray>" + message.time() + "</dark_gray>"), 240),
                true, false, HEAD_SIZE, HEAD_SIZE));
        }

        final List<DialogInput> inputs = List.of(DialogInput.text(Routes.INPUT_MESSAGE, 300,
            Component.text("Message"), false, prefill == null ? "" : prefill, this.config.maxMessageLength(), null));

        final List<ActionButton> actions = new ArrayList<>();
        if (from > 0) {
            actions.add(button(Icons.UP + " Show older messages", null, 320, Routes.key(Routes.SHOW_OLDER, friend)));
        }
        if (canSpeak) {
            actions.add(button("Send " + Icons.SEND, null, 320, Routes.key(Routes.SEND, friend)));
        }
        if (!isFriend) {
            actions.add(button(this.store.isIgnored(me, friend) ? Icons.BELL_ON + " Unignore" : Icons.BELL_OFF + " Ignore",
                null, 155, Routes.key(Routes.IGNORE_CHAT, friend)));
            actions.add(iBlocked
                ? button(Icons.CHECK + " Unblock", null, 155, Routes.key(Routes.UNBLOCK_CHAT, friend))
                : button("<dark_red>" + Icons.BLOCK + " Block</dark_red>", null, 155, Routes.key(Routes.BLOCK_CHAT, friend)));
        }
        if (actions.isEmpty()) {
            // Blocked by a friend, with nothing older to show: keep one way out.
            actions.add(button(Icons.PREV + " Back", null, 320, Routes.key(Routes.CHAT_BACK, friend)));
        }

        return build(base(name, null, body, inputs),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.CHAT_BACK, friend)), 1));
    }

    // --------------------------------------------------------------- requests

    public Dialog requests(final Player player) {
        final UUID me = player.getUniqueId();
        final List<UUID> pending = new ArrayList<>(this.store.requests(me));
        pending.sort(Comparator.comparing((UUID uuid) -> this.store.name(uuid), String.CASE_INSENSITIVE_ORDER));

        final List<DialogBody> body = pending.isEmpty()
            ? List.of(DialogBody.plainMessage(mm("<gray>No pending friend requests.</gray>"), 320))
            : List.of();

        final List<ActionButton> actions = new ArrayList<>();
        if (pending.isEmpty()) {
            actions.add(button(Icons.PREV + " Back to menu", null, 320, Routes.key(Routes.MENU)));
        }
        for (final UUID requester : pending) {
            final String name = this.store.name(requester);
            actions.add(button(Icons.CHECK + ' ' + name,
                "<white><b>" + name + "</b></white><newline><gray>Accept friend request</gray>",
                155, Routes.key(Routes.ACCEPT, requester)));
            actions.add(button("<red>" + Icons.CROSS + ' ' + name + "</red>",
                "<white><b>" + name + "</b></white><newline><gray>Decline friend request</gray>",
                155, Routes.key(Routes.DECLINE, requester)));
        }

        return build(base("Friend Requests (" + pending.size() + ')', null, body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.MENU)), 2));
    }

    // ---------------------------------------------------------------- blocked

    public Dialog blocked(final Player player) {
        final UUID me = player.getUniqueId();
        final List<UUID> blocked = new ArrayList<>(this.store.blocked(me));
        blocked.sort(Comparator.comparing((UUID uuid) -> this.store.name(uuid), String.CASE_INSENSITIVE_ORDER));

        final List<DialogBody> body = blocked.isEmpty()
            ? List.of(DialogBody.plainMessage(mm("<gray>You haven't blocked anyone.</gray>"), 320))
            : List.of();

        final List<ActionButton> actions = new ArrayList<>();
        for (final UUID target : blocked) {
            actions.add(button("Unblock " + this.store.name(target), null, 320, Routes.key(Routes.UNBLOCK, target)));
        }
        actions.add(button("<red>" + Icons.BLOCK + " Block a player</red>", null, 320, Routes.key(Routes.BLOCK_ADD)));

        return build(base("Blocked Players (" + blocked.size() + ')', null, body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.MENU)), 1));
    }

    // ----------------------------------------------------------- name prompts

    public Dialog addFriend() {
        final List<DialogBody> body = List.of(DialogBody.plainMessage(
            mm("<gray>Type the exact username of the player you want to add.</gray>"), 320));
        final List<DialogInput> inputs = List.of(
            DialogInput.text(Routes.INPUT_NAME, 320, Component.text("Username"), true, "", 16, null));
        final List<ActionButton> actions = List.of(
            button("Send Request " + Icons.SEND, null, 320, Routes.key(Routes.ADD_FRIEND_SUBMIT)));
        return build(base("Add a Friend", null, body, inputs),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.MENU)), 1));
    }

    public Dialog blockAdd() {
        final List<DialogBody> body = List.of(DialogBody.plainMessage(
            mm("<gray>Type the exact username to block.</gray>"), 320));
        final List<DialogInput> inputs = List.of(
            DialogInput.text(Routes.INPUT_NAME, 320, Component.text("Username"), true, "", 16, null));
        final List<ActionButton> actions = List.of(
            button("<red>Block Player</red>", null, 320, Routes.key(Routes.BLOCK_ADD_SUBMIT)));
        return build(base("Block a Player", null, body, inputs),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.BLOCKED)), 1));
    }

    // --------------------------------------------------------------- settings

    public Dialog settings(final Player player) {
        final PlayerSettings settings = this.store.settings(player.getUniqueId());
        final List<ActionButton> actions = List.of(
            button(Icons.VIEW + " Friend view: " + (settings.cards() ? "Cards (heads)" : "Buttons"),
                null, 320, Routes.key(Routes.SET_VIEW)),
            button(Icons.toggle(settings.toasts()) + " Toasts: " + Icons.onOff(settings.toasts()),
                null, 320, Routes.key(Routes.SET_TOASTS)),
            button(Icons.toggle(settings.sounds()) + " Sound effects: " + Icons.onOff(settings.sounds()),
                null, 320, Routes.key(Routes.SET_SOUNDS)),
            button(Icons.toggle(settings.actionBar()) + " Action bar: " + Icons.onOff(settings.actionBar()),
                null, 320, Routes.key(Routes.SET_ACTIONBAR)),
            button(Icons.toggle(settings.reminder()) + " Unread reminder: " + Icons.onOff(settings.reminder()),
                null, 320, Routes.key(Routes.SET_REMINDER)),
            button(Icons.PRIVACY + " Who can message me: " + settings.privacyDisplayName(),
                null, 320, Routes.key(Routes.SET_DM_PRIVACY)),
            button(Icons.SCALE + " GUI Scale: " + settings.guiScale(), null, 320, Routes.key(Routes.SCALE_OPEN)),
            button(Icons.PALETTE + ' ' + Colors.wrap(settings.color(), false, "Customize Color"),
                null, 320, Routes.key(Routes.COLOR_OPEN)));

        return build(base("Settings", null, List.of(), List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.SETTINGS_BACK)), 1));
    }

    public Dialog guiScale() {
        final List<DialogBody> body = List.of(DialogBody.plainMessage(mm(
            "<gray>Sets how many chat messages and friends per page show before scrolling/paging. "
                + "<yellow>Match it to your actual Minecraft GUI Scale</yellow> (Options ▸ Video Settings ▸ GUI Scale). "
                + "<newline>If unsure, leave it at <white>4 / Auto</white>.</gray>"), 340));
        final List<ActionButton> actions = List.of(
            button("GUI Scale 4 / Auto", null, 320, Routes.key(Routes.SCALE, "4")),
            button("GUI Scale 3", null, 320, Routes.key(Routes.SCALE, "3")),
            button("GUI Scale 2", null, 320, Routes.key(Routes.SCALE, "2")),
            button("GUI Scale 1", null, 320, Routes.key(Routes.SCALE, "1")));
        return build(base("GUI Scale", null, body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.SETTINGS)), 1));
    }

    public Dialog colorPicker(final Player player) {
        final List<DialogBody> body = List.of(DialogBody.plainMessage(mm(
            "<gray>Pick your message color. Your own messages show a lighter, pastel version.</gray>"), 340));
        final List<ActionButton> actions = new ArrayList<>();
        for (final String color : Colors.ALL) {
            if (Colors.canUse(player, color)) {
                actions.add(button(Colors.wrap(color, false, color), null, 105, Routes.key(Routes.COLOR, color)));
            }
        }
        return build(base("Customize Color", null, body, List.of()),
            DialogType.multiAction(actions, backButton(Routes.key(Routes.SETTINGS)), 3));
    }

    // ---------------------------------------------------------------- helpers

    private static Dialog build(final DialogBase base, final DialogType type) {
        return Dialog.create(builder -> builder.empty().base(base).type(type));
    }

    private static DialogBase base(final String title, final String externalTitle,
                                   final List<DialogBody> body, final List<DialogInput> inputs) {
        return DialogBase.create(mm(title), externalTitle == null ? null : Component.text(externalTitle),
            true, false, DialogBase.DialogAfterAction.NONE, body, inputs);
    }

    private static ActionButton button(final String label, final String tooltip, final int width, final Key route) {
        return ActionButton.create(mm(label), tooltip == null ? null : mm(tooltip), width,
            DialogAction.customClick(route, null));
    }

    private static ActionButton backButton(final Key route) {
        return ActionButton.create(Component.text("Back"), null, 100, DialogAction.customClick(route, null));
    }

    /** A head with a short description next to it. */
    private DialogBody card(final UUID uuid, final String description) {
        final PlainMessageDialogBody text = DialogBody.plainMessage(mm(description), 260);
        return DialogBody.item(this.head(uuid), text, true, false, HEAD_SIZE, HEAD_SIZE);
    }

    private ItemStack head(final UUID uuid) {
        final ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        final String name = this.store.hasName(uuid) ? this.store.name(uuid) : null;
        item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(
            Bukkit.createProfile(uuid, name != null && name.length() <= 16 ? name : null)));
        return item;
    }

    /** Tooltip shown when hovering a friend's button or card. */
    public String friendTooltip(final UUID me, final UUID friend) {
        final String name = this.store.name(friend);
        final int unread = this.store.unread(me, friend);
        final StringBuilder tooltip = new StringBuilder()
            .append("<white><b>").append(name).append("</b></white><newline>")
            .append(PlayerLookup.isOnline(friend) ? "<green>● Online</green>" : "<red>● Offline</red>")
            .append("<newline>").append(this.lastSeenText(friend));
        tooltip.append(unread > 0
            ? "<newline><yellow>" + unread + " unread message(s)</yellow>"
            : "<newline><gray>No unread messages</gray>");
        if (this.store.isIgnored(me, friend)) {
            tooltip.append("<newline><dark_gray>(ignored)</dark_gray>");
        }
        return tooltip.toString();
    }

    /** "Online now", or how long ago the player was last seen. */
    public String lastSeenText(final UUID uuid) {
        if (PlayerLookup.isOnline(uuid)) {
            return "<green>Online now</green>";
        }
        final Instant seen = this.store.lastSeen(uuid);
        if (seen == null) {
            return "<gray>Last seen: Unknown</gray>";
        }
        return "<gray>Last seen " + describe(Duration.between(seen, Instant.now())) + " ago</gray>";
    }

    /** Compact "3 days 4 hours" style duration, matching the Skript's phrasing. */
    private static String describe(final Duration duration) {
        final long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return plural(seconds, "second");
        }
        if (seconds < 3600) {
            return plural(seconds / 60, "minute");
        }
        if (seconds < 86_400) {
            final long hours = seconds / 3600;
            final long minutes = seconds % 3600 / 60;
            return minutes == 0 ? plural(hours, "hour") : plural(hours, "hour") + ' ' + plural(minutes, "minute");
        }
        final long days = seconds / 86_400;
        final long hours = seconds % 86_400 / 3600;
        return hours == 0 ? plural(days, "day") : plural(days, "day") + ' ' + plural(hours, "hour");
    }

    private static String plural(final long amount, final String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    public static Component mm(final String text) {
        return MINI.deserialize(text);
    }
}
