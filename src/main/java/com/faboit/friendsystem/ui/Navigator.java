package com.faboit.friendsystem.ui;

import com.faboit.friendsystem.service.MessageService;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Opens dialogs and performs the side effects that come with them — mainly marking a
 * conversation as read the moment it is shown.
 */
public final class Navigator {

    private final DialogFactory dialogs;
    private final MessageService messages;

    public Navigator(final DialogFactory dialogs, final MessageService messages) {
        this.dialogs = dialogs;
        this.messages = messages;
    }

    public void menu(final Player player) {
        player.showDialog(this.dialogs.menu(player));
    }

    public void friends(final Player player) {
        player.showDialog(this.dialogs.friends(player));
    }

    public void directMessages(final Player player) {
        player.showDialog(this.dialogs.directMessages(player));
    }

    public void friendPage(final Player player, final UUID friend) {
        player.showDialog(this.dialogs.friendPage(player, friend));
    }

    /** Opens a conversation, which also clears its unread counter. */
    public void chat(final Player player, final UUID friend, final String prefill, final String warning) {
        this.messages.markRead(player.getUniqueId(), friend);
        player.showDialog(this.dialogs.chat(player, friend, prefill, warning));
    }

    public void chat(final Player player, final UUID friend) {
        this.chat(player, friend, "", "");
    }

    public void requests(final Player player) {
        player.showDialog(this.dialogs.requests(player));
    }

    public void blocked(final Player player) {
        player.showDialog(this.dialogs.blocked(player));
    }

    public void addFriend(final Player player) {
        player.showDialog(this.dialogs.addFriend());
    }

    public void blockAdd(final Player player) {
        player.showDialog(this.dialogs.blockAdd());
    }

    public void settings(final Player player) {
        player.showDialog(this.dialogs.settings(player));
    }

    public void guiScale(final Player player) {
        player.showDialog(this.dialogs.guiScale());
    }

    public void colorPicker(final Player player) {
        player.showDialog(this.dialogs.colorPicker(player));
    }

    public void close(final Player player) {
        player.closeDialog();
    }

    public DialogFactory dialogs() {
        return this.dialogs;
    }
}
