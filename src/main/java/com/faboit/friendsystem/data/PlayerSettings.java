package com.faboit.friendsystem.data;

/**
 * Per-player preferences. Mirrors the {@code fs::set::*} variables of the original
 * Skript: every value has a sensible default so a player who never opened the
 * settings dialog still behaves like the old system did.
 */
public final class PlayerSettings {

    public static final String VIEW_BUTTONS = "buttons";
    public static final String VIEW_CARDS = "cards";

    public static final String PRIVACY_ANYONE = "anyone";
    public static final String PRIVACY_FOF = "fof";
    public static final String PRIVACY_FRIENDS = "friends";
    public static final String PRIVACY_NONE = "none";

    private String viewMode = VIEW_BUTTONS;
    private boolean toasts = true;
    private boolean sounds = true;
    private boolean actionBar = true;
    private boolean reminder = true;
    private String dmPrivacy = PRIVACY_ANYONE;
    private int guiScale = 4;
    private String color = "white";

    public String viewMode() {
        return this.viewMode;
    }

    public void viewMode(final String viewMode) {
        this.viewMode = VIEW_CARDS.equals(viewMode) ? VIEW_CARDS : VIEW_BUTTONS;
    }

    public boolean cards() {
        return VIEW_CARDS.equals(this.viewMode);
    }

    public boolean toasts() {
        return this.toasts;
    }

    public void toasts(final boolean toasts) {
        this.toasts = toasts;
    }

    public boolean sounds() {
        return this.sounds;
    }

    public void sounds(final boolean sounds) {
        this.sounds = sounds;
    }

    public boolean actionBar() {
        return this.actionBar;
    }

    public void actionBar(final boolean actionBar) {
        this.actionBar = actionBar;
    }

    public boolean reminder() {
        return this.reminder;
    }

    public void reminder(final boolean reminder) {
        this.reminder = reminder;
    }

    public String dmPrivacy() {
        return this.dmPrivacy;
    }

    public void dmPrivacy(final String dmPrivacy) {
        this.dmPrivacy = switch (dmPrivacy == null ? "" : dmPrivacy) {
            case PRIVACY_FOF -> PRIVACY_FOF;
            case PRIVACY_FRIENDS -> PRIVACY_FRIENDS;
            case PRIVACY_NONE -> PRIVACY_NONE;
            default -> PRIVACY_ANYONE;
        };
    }

    /** Advances the privacy setting to the next option, like clicking the settings button did. */
    public void cycleDmPrivacy() {
        this.dmPrivacy = switch (this.dmPrivacy) {
            case PRIVACY_ANYONE -> PRIVACY_FOF;
            case PRIVACY_FOF -> PRIVACY_FRIENDS;
            case PRIVACY_FRIENDS -> PRIVACY_NONE;
            default -> PRIVACY_ANYONE;
        };
    }

    public int guiScale() {
        return this.guiScale;
    }

    public void guiScale(final int guiScale) {
        this.guiScale = guiScale < 1 || guiScale > 4 ? 4 : guiScale;
    }

    public String color() {
        return this.color;
    }

    public void color(final String color) {
        this.color = color == null || color.isEmpty() ? "white" : color;
    }

    /** Chat lines shown in a conversation dialog, by GUI scale. */
    public int maxMessages() {
        return switch (this.guiScale) {
            case 1 -> 25;
            case 2 -> 10;
            case 3 -> 5;
            default -> 4;
        };
    }

    /** Friends shown per page in the friends dialog, by GUI scale. */
    public int friendsPerPage() {
        return switch (this.guiScale) {
            case 1 -> 100;
            case 2 -> 75;
            case 3 -> 60;
            default -> 50;
        };
    }

    public String privacyDisplayName() {
        return switch (this.dmPrivacy) {
            case PRIVACY_FRIENDS -> "Friends only";
            case PRIVACY_FOF -> "Friends of friends";
            case PRIVACY_NONE -> "No one";
            default -> "Anyone";
        };
    }
}
