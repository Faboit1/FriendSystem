package com.faboit.friendsystem.ui;

import java.util.UUID;
import net.kyori.adventure.key.Key;

/**
 * Identifiers used by the dialog buttons.
 *
 * <p>Buttons that act on somebody encode that player's UUID in the key itself
 * ({@code friendsystem:friend_page/<uuid>}) — UUIDs are valid key characters, so the
 * router gets its argument without any NBT parsing.</p>
 */
public final class Routes {

    public static final String NAMESPACE = "friendsystem";

    public static final String MENU = "menu";
    public static final String FRIENDS = "friends";
    public static final String DMS = "dms";
    public static final String REQUESTS = "requests";
    public static final String BLOCKED = "blocked";
    public static final String SETTINGS = "settings";
    public static final String SETTINGS_BACK = "settings_back";
    public static final String CLOSE = "close";

    public static final String ADD_FRIEND = "add_friend";
    public static final String ADD_FRIEND_SUBMIT = "add_friend_submit";
    public static final String BLOCK_ADD = "block_add";
    public static final String BLOCK_ADD_SUBMIT = "block_add_submit";

    public static final String PAGE_PREV = "page_prev";
    public static final String PAGE_NEXT = "page_next";
    public static final String SEARCH_SUBMIT = "search_submit";
    public static final String SEARCH_CLEAR = "search_clear";

    public static final String FRIEND_PAGE = "friend_page";
    public static final String OPEN_CHAT = "open_chat";
    public static final String DM_OPEN_CHAT = "dm_open_chat";
    public static final String CHAT_BACK = "chat_back";
    public static final String SHOW_OLDER = "show_older";
    public static final String SEND = "send";
    public static final String PAY = "pay";
    public static final String TELEPORT = "teleport";
    public static final String STATS = "stats";
    public static final String IGNORE = "ignore";
    public static final String IGNORE_CHAT = "ignore_chat";
    public static final String BLOCK_CHAT = "block_chat";
    public static final String UNBLOCK_CHAT = "unblock_chat";
    public static final String AUTO_TPA = "auto_tpa";
    public static final String AUTO_TPA_HERE = "auto_tpa_here";
    public static final String UNFRIEND = "unfriend";
    public static final String BLOCK = "block";
    public static final String UNBLOCK = "unblock";
    public static final String ACCEPT = "accept";
    public static final String DECLINE = "decline";

    public static final String SET_VIEW = "set_view";
    public static final String SET_TOASTS = "set_toasts";
    public static final String SET_SOUNDS = "set_sounds";
    public static final String SET_ACTIONBAR = "set_actionbar";
    public static final String SET_REMINDER = "set_reminder";
    public static final String SET_DM_PRIVACY = "set_dm_privacy";
    public static final String SCALE_OPEN = "scale_open";
    public static final String SCALE = "scale";
    public static final String COLOR_OPEN = "color_open";
    public static final String COLOR = "color";

    /** Text input keys, matched against {@code DialogResponseView#getText}. */
    public static final String INPUT_QUERY = "query";
    public static final String INPUT_MESSAGE = "message_input";
    public static final String INPUT_NAME = "target_name";

    private Routes() {
    }

    public static Key key(final String route) {
        return Key.key(NAMESPACE, route);
    }

    public static Key key(final String route, final UUID target) {
        return Key.key(NAMESPACE, route + '/' + target);
    }

    public static Key key(final String route, final String argument) {
        return Key.key(NAMESPACE, route + '/' + argument);
    }

    /** A click identifier split into its route and optional argument. */
    public record Parsed(String route, String argument) {

        /** The argument as a UUID, or {@code null} when it is missing or malformed. */
        public UUID uuid() {
            if (this.argument == null) {
                return null;
            }
            try {
                return UUID.fromString(this.argument);
            } catch (final IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static Parsed parse(final Key key) {
        final String value = key.value();
        final int slash = value.indexOf('/');
        return slash < 0
            ? new Parsed(value, null)
            : new Parsed(value.substring(0, slash), value.substring(slash + 1));
    }
}
