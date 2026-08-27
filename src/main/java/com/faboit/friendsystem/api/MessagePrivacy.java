package com.faboit.friendsystem.api;

/** Who a player is willing to receive direct messages from. */
public enum MessagePrivacy {

    /** Anybody on the server. */
    ANYONE("anyone"),
    /** Friends, plus anyone who shares at least one friend with them. */
    FRIENDS_OF_FRIENDS("fof"),
    /** Friends only. */
    FRIENDS("friends"),
    /** Nobody; only staff can get through. */
    NOBODY("none");

    private final String key;

    MessagePrivacy(final String key) {
        this.key = key;
    }

    /** The value as it is stored in the database and the config. */
    public String key() {
        return this.key;
    }

    /** Parses a stored value, falling back to {@link #ANYONE} for anything unknown. */
    public static MessagePrivacy fromKey(final String key) {
        for (final MessagePrivacy privacy : values()) {
            if (privacy.key.equalsIgnoreCase(key)) {
                return privacy;
            }
        }
        return ANYONE;
    }
}
