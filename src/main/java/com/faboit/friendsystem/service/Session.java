package com.faboit.friendsystem.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived per-player UI state: which page of the friends list is open, what was
 * typed into the search box, how far back a conversation has been scrolled and when
 * the player last sent a message. None of this is worth a database row — it is
 * dropped when the player logs out.
 */
public final class Session {

    /** Where a chat dialog was opened from, so its Back button returns there. */
    public static final String ORIGIN_DIRECT = "direct";
    public static final String ORIGIN_FRIEND_PAGE = "friend_page";
    public static final String ORIGIN_DMS = "dms";

    private final Map<String, Integer> showMore = new ConcurrentHashMap<>();
    private volatile String search;
    private volatile int page = 1;
    private volatile String chatOrigin = ORIGIN_DIRECT;
    private volatile String backCommand;
    private volatile Instant lastMessage;

    public String search() {
        return this.search;
    }

    public void search(final String search) {
        this.search = search == null || search.isBlank() ? null : search.trim();
    }

    public int page() {
        return this.page;
    }

    public void page(final int page) {
        this.page = Math.max(1, page);
    }

    /** Extra messages revealed by "Show older messages" in a conversation. */
    public int showMore(final String convo) {
        return this.showMore.getOrDefault(convo, 0);
    }

    public void addShowMore(final String convo, final int extra) {
        this.showMore.merge(convo, extra, Integer::sum);
    }

    public void resetShowMore(final String convo) {
        this.showMore.remove(convo);
    }

    public String chatOrigin() {
        return this.chatOrigin;
    }

    public void chatOrigin(final String chatOrigin) {
        this.chatOrigin = chatOrigin;
    }

    /** Command to run when leaving the settings dialog, when it was opened by a command. */
    public String backCommand() {
        return this.backCommand;
    }

    public void backCommand(final String backCommand) {
        this.backCommand = backCommand;
    }

    /** True while the anti-spam cooldown from the last message is still running. */
    public boolean onCooldown(final int seconds) {
        final Instant last = this.lastMessage;
        return seconds > 0 && last != null && Duration.between(last, Instant.now()).getSeconds() < seconds;
    }

    public void markMessageSent() {
        this.lastMessage = Instant.now();
    }
}
