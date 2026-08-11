package com.faboit.friendsystem.ui;

/**
 * Inline sprite tags used on buttons and labels.
 *
 * <p>Everything comes from the vanilla {@code items}/{@code blocks} atlases except the
 * two beacon buttons, so no resource pack is needed. They are plain MiniMessage
 * strings, which keeps building labels as simple as string concatenation.</p>
 */
public final class Icons {

    public static final String FRIENDS = "<sprite:items:item/apple>";
    public static final String MAIL = "<sprite:items:item/writable_book>";
    public static final String PLUS = "<sprite:blocks:block/oak_sapling>";
    public static final String BLOCK = "<sprite:items:item/barrier>";
    public static final String SETTINGS = "<sprite:items:item/redstone>";
    public static final String SEARCH = "<sprite:items:item/spyglass>";
    public static final String CROSS = "<sprite:gui:container/beacon/cancel>";
    public static final String CHECK = "<sprite:gui:container/beacon/confirm>";
    public static final String PREV = "<sprite:items:item/spectral_arrow>";
    public static final String NEXT = "<sprite:items:item/arrow>";
    public static final String ONLINE = "<green>●</green> ";
    public static final String OFFLINE = "<red>●</red> ";
    public static final String BELL_ON = "<sprite:items:item/bell>";
    public static final String BELL_OFF = "<sprite:items:item/bell>";
    public static final String COIN = "<sprite:items:item/emerald>";
    public static final String ROCKET = "<sprite:items:item/ender_pearl>";
    public static final String STATS = "<sprite:items:item/experience_bottle>";
    public static final String UP = "<sprite:items:item/feather>";
    public static final String SEND = "<sprite:items:item/arrow>";
    public static final String TOGGLE_ON = "<sprite:blocks:block/redstone_lamp_on>";
    public static final String TOGGLE_OFF = "<sprite:blocks:block/redstone_lamp>";
    public static final String VIEW = "<sprite:items:item/painting>";
    public static final String PRIVACY = "<sprite:items:item/shield>";
    public static final String SCALE = "<sprite:items:item/map>";
    public static final String PALETTE = "<sprite:items:item/lime_dye>";

    private Icons() {
    }

    /** The redstone-lamp icon matching a boolean setting. */
    public static String toggle(final boolean on) {
        return on ? TOGGLE_ON : TOGGLE_OFF;
    }

    /** {@code ON}/{@code OFF} in the matching colour. */
    public static String onOff(final boolean on) {
        return on ? "<green>ON</green>" : "<red>OFF</red>";
    }

    /** The status dot in front of a player's name. */
    public static String dot(final boolean online) {
        return online ? ONLINE : OFFLINE;
    }
}
