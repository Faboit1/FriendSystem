package com.faboit.friendsystem.api;

/** The outcome of trying to send a friend request. */
public enum FriendRequestResult {

    /** The request was stored and the target notified. */
    SENT,
    /** The target had already requested us, so the two are now friends. */
    ACCEPTED,
    ALREADY_FRIENDS,
    /** A request from us to them is already pending. */
    ALREADY_SENT,
    /** No name was given. */
    EMPTY,
    /** Nobody by that name has ever joined this server. */
    UNKNOWN_PLAYER,
    SELF,
    BLOCKED_BY_YOU,
    BLOCKED_BY_THEM,
    /** Another plugin cancelled the request event. */
    CANCELLED;

    /** {@code true} when the two players ended up as friends or a request is now pending. */
    public boolean successful() {
        return this == SENT || this == ACCEPTED;
    }
}
