package com.faboit.friendsystem.data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** A single direct message inside a conversation. */
public record Message(long id, UUID sender, String text, Instant sentAt) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** The {@code HH:mm} stamp shown next to the message in the chat dialog. */
    public String time() {
        return TIME.format(this.sentAt.atZone(ZoneId.systemDefault()));
    }
}
