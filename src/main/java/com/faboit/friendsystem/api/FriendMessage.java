package com.faboit.friendsystem.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One direct message out of a conversation.
 *
 * <p>Only messages inside the configured retention window
 * ({@code messages.retention-hours}) are kept, so a conversation is a rolling
 * window rather than a complete history.</p>
 *
 * @param sender   who wrote it
 * @param receiver the other side of the conversation
 * @param content  the text, already stripped of formatting codes
 * @param sentAt   when it was sent
 */
public record FriendMessage(UUID sender, UUID receiver, String content, Instant sentAt) {
}
