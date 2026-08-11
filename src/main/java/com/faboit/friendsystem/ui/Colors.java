package com.faboit.friendsystem.ui;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * The chat-colour palette players can pick for their direct messages. Their own
 * messages render in a lighter pastel variant so both sides of a conversation stay
 * readable next to each other.
 */
public final class Colors {

    public static final String RAINBOW = "rainbow";

    /** Selectable colours, in the order they appear in the picker. */
    public static final List<String> ALL = List.of(
        "white", "gray", "dark_gray", "black", "red", "dark_red", "gold", "yellow", "green", "dark_green",
        "aqua", "dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple", RAINBOW);

    private static final Map<String, String> BASE = Map.ofEntries(
        Map.entry("white", "#FFFFFF"),
        Map.entry("gray", "#AAAAAA"),
        Map.entry("dark_gray", "#555555"),
        Map.entry("black", "#000000"),
        Map.entry("red", "#FF5555"),
        Map.entry("dark_red", "#AA0000"),
        Map.entry("gold", "#FFAA00"),
        Map.entry("yellow", "#FFFF55"),
        Map.entry("green", "#55FF55"),
        Map.entry("dark_green", "#00AA00"),
        Map.entry("aqua", "#55FFFF"),
        Map.entry("dark_aqua", "#00AAAA"),
        Map.entry("blue", "#5555FF"),
        Map.entry("dark_blue", "#0000AA"),
        Map.entry("light_purple", "#FF55FF"),
        Map.entry("dark_purple", "#AA00AA"));

    private static final Map<String, String> LIGHT = Map.ofEntries(
        Map.entry("white", "#DDDDDD"),
        Map.entry("gray", "#CFCFCF"),
        Map.entry("dark_gray", "#9A9A9A"),
        Map.entry("black", "#6E6E6E"),
        Map.entry("red", "#FF9E9E"),
        Map.entry("dark_red", "#E06A6A"),
        Map.entry("gold", "#FFD27F"),
        Map.entry("yellow", "#FFFFAE"),
        Map.entry("green", "#AEFFAE"),
        Map.entry("dark_green", "#6AE06A"),
        Map.entry("aqua", "#AEFFFF"),
        Map.entry("dark_aqua", "#6AE0E0"),
        Map.entry("blue", "#9E9EFF"),
        Map.entry("dark_blue", "#6A6AE0"),
        Map.entry("light_purple", "#FFAEFF"),
        Map.entry("dark_purple", "#E06AE0"));

    private Colors() {
    }

    public static boolean exists(final String color) {
        return RAINBOW.equals(color) || BASE.containsKey(color);
    }

    /**
     * White is free for everyone; the rest need {@code friendsystem.color.<name>},
     * {@code friendsystem.color.*} or the legacy {@code chat.color.<name>}/{@code cheeseplus}
     * permissions the Skript version used.
     */
    public static boolean canUse(final Player player, final String color) {
        if ("white".equals(color)) {
            return true;
        }
        final String lower = color.toLowerCase(Locale.ROOT);
        return player.hasPermission("friendsystem.color.*")
            || player.hasPermission("friendsystem.color." + lower)
            || player.hasPermission("chat.color." + lower)
            || player.hasPermission("cheeseplus");
    }

    /**
     * Wraps text in the given colour.
     *
     * @param own whether this is the viewer's own message, which uses the pastel variant
     */
    public static String wrap(final String color, final boolean own, final String text) {
        if (RAINBOW.equals(color)) {
            return own
                ? "<gradient:#ffb3b3:#fff0b3:#b3ffb3:#b3f0ff:#d9b3ff>" + text + "</gradient>"
                : "<rainbow>" + text + "</rainbow>";
        }
        final Map<String, String> palette = own ? LIGHT : BASE;
        final String hex = palette.getOrDefault(color, "#FFFFFF");
        return "<color:" + hex + '>' + text + "</color>";
    }
}
