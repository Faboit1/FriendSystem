package com.faboit.friendsystem.api;

/** The outcome of trying to deliver a direct message. */
public enum MessageResult {

    /** Stored, mirrored into chat and delivered. */
    OK,
    /** No receiver, or nothing left after trimming the text. */
    EMPTY,
    SELF,
    /** The sender is still on the message cooldown. */
    COOLDOWN,
    BLOCKED_BY_YOU,
    BLOCKED_BY_THEM,
    /** The receiver's {@link MessagePrivacy} setting does not allow this sender. */
    PRIVACY,
    /** Another plugin cancelled the message event. */
    CANCELLED;

    public boolean successful() {
        return this == OK;
    }
}
