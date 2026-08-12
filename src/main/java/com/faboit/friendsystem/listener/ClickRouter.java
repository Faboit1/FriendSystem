package com.faboit.friendsystem.listener;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.PlayerSettings;
import com.faboit.friendsystem.service.FriendService;
import com.faboit.friendsystem.service.MessageService;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.Session;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.ui.Colors;
import com.faboit.friendsystem.ui.Navigator;
import com.faboit.friendsystem.ui.Routes;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Turns dialog button clicks into actions.
 *
 * <p>Every button carries an identifier such as {@code friendsystem:unfriend/<uuid>};
 * this listener splits that into a route and a target and performs the matching
 * operation, then re-opens whichever dialog the player should end up on.</p>
 */
public final class ClickRouter implements Listener {

    private final DataStore store;
    private final FriendConfig config;
    private final SessionManager sessions;
    private final Navigator navigator;
    private final FriendService friends;
    private final MessageService messages;
    private final Notifier notifier;

    public ClickRouter(final DataStore store, final FriendConfig config, final SessionManager sessions,
                       final Navigator navigator, final FriendService friends, final MessageService messages,
                       final Notifier notifier) {
        this.store = store;
        this.config = config;
        this.sessions = sessions;
        this.navigator = navigator;
        this.friends = friends;
        this.messages = messages;
        this.notifier = notifier;
    }

    @EventHandler
    public void onCustomClick(final PlayerCustomClickEvent event) {
        final Key identifier = event.getIdentifier();
        if (!Routes.NAMESPACE.equals(identifier.namespace())) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        final Player player = connection.getPlayer();
        final Routes.Parsed parsed = Routes.parse(identifier);
        final DialogResponseView response = event.getDialogResponseView();
        this.handle(player, parsed, response);
    }

    private void handle(final Player player, final Routes.Parsed parsed, final DialogResponseView response) {
        final UUID me = player.getUniqueId();
        final Session session = this.sessions.of(me);
        final UUID target = parsed.uuid();

        switch (parsed.route()) {
            // ---------------------------------------------------- navigation
            case Routes.MENU -> this.navigator.menu(player);
            case Routes.FRIENDS -> this.navigator.friends(player);
            case Routes.DMS -> this.navigator.directMessages(player);
            case Routes.REQUESTS -> this.navigator.requests(player);
            case Routes.BLOCKED -> this.navigator.blocked(player);
            case Routes.ADD_FRIEND -> this.navigator.addFriend(player);
            case Routes.BLOCK_ADD -> this.navigator.blockAdd(player);
            case Routes.CLOSE -> this.navigator.close(player);
            case Routes.SETTINGS -> {
                session.backCommand(null);
                this.navigator.settings(player);
            }
            case Routes.SETTINGS_BACK -> {
                // /friends settings opens straight into the settings dialog with no menu
                // behind it, so Back returns to the command that opened it instead.
                final String back = session.backCommand();
                if (back == null) {
                    this.navigator.menu(player);
                } else {
                    session.backCommand(null);
                    this.navigator.close(player);
                    player.performCommand(back);
                }
            }

            // --------------------------------------------------- friend list
            case Routes.PAGE_PREV -> {
                session.page(session.page() - 1);
                this.navigator.friends(player);
            }
            case Routes.PAGE_NEXT -> {
                session.page(session.page() + 1);
                this.navigator.friends(player);
            }
            case Routes.SEARCH_SUBMIT -> {
                session.search(text(response, Routes.INPUT_QUERY));
                session.page(1);
                this.navigator.friends(player);
            }
            case Routes.SEARCH_CLEAR -> {
                session.search(null);
                session.page(1);
                this.navigator.friends(player);
            }
            case Routes.FRIEND_PAGE -> this.withTarget(player, target, () -> this.navigator.friendPage(player, target));

            // -------------------------------------------------------- chats
            case Routes.OPEN_CHAT -> this.openChat(player, target, Session.ORIGIN_FRIEND_PAGE);
            case Routes.DM_OPEN_CHAT -> this.openChat(player, target, Session.ORIGIN_DMS);
            case Routes.CHAT_BACK -> {
                if (target == null) {
                    this.navigator.menu(player);
                } else {
                    switch (session.chatOrigin()) {
                        case Session.ORIGIN_FRIEND_PAGE -> this.navigator.friendPage(player, target);
                        case Session.ORIGIN_DMS -> this.navigator.directMessages(player);
                        default -> this.navigator.menu(player);
                    }
                }
            }
            case Routes.SHOW_OLDER -> this.withTarget(player, target, () -> {
                session.addShowMore(DataStore.convoKey(me, target), 15);
                this.navigator.chat(player, target, text(response, Routes.INPUT_MESSAGE), "");
            });
            case Routes.SEND -> this.withTarget(player, target, () -> this.send(player, target, text(response, Routes.INPUT_MESSAGE)));

            // -------------------------------------------- external commands
            case Routes.PAY -> this.runIntegration(player, target, this.config.pay(), "");
            case Routes.TELEPORT -> this.runIntegration(player, target, this.config.teleport(), "");
            case Routes.STATS -> this.runIntegration(player, target, this.config.stats(), "");

            // ------------------------------------------------ friend actions
            case Routes.IGNORE -> this.withTarget(player, target, () -> {
                this.friends.toggleIgnore(player, target);
                this.navigator.friendPage(player, target);
            });
            case Routes.IGNORE_CHAT -> this.withTarget(player, target, () -> {
                this.friends.toggleIgnore(player, target);
                this.navigator.chat(player, target);
            });
            case Routes.BLOCK_CHAT -> this.withTarget(player, target, () -> {
                this.friends.block(player, target);
                this.navigator.chat(player, target);
            });
            case Routes.UNBLOCK_CHAT -> this.withTarget(player, target, () -> {
                this.friends.unblock(player, target);
                this.navigator.chat(player, target);
            });
            case Routes.AUTO_TPA -> this.toggleAutoTpa(player, target, false);
            case Routes.AUTO_TPA_HERE -> this.toggleAutoTpa(player, target, true);
            case Routes.UNFRIEND -> this.withTarget(player, target, () -> {
                this.friends.unfriend(player, target);
                this.navigator.friends(player);
            });
            case Routes.BLOCK -> this.withTarget(player, target, () -> {
                this.friends.block(player, target);
                this.navigator.friends(player);
            });
            case Routes.UNBLOCK -> this.withTarget(player, target, () -> {
                this.friends.unblock(player, target);
                this.navigator.blocked(player);
            });
            case Routes.ACCEPT -> this.withTarget(player, target, () -> {
                this.friends.accept(player, target);
                if (this.store.requestCount(me) > 0) {
                    this.navigator.requests(player);
                } else {
                    this.navigator.friends(player);
                }
            });
            case Routes.DECLINE -> this.withTarget(player, target, () -> {
                this.friends.decline(player, target);
                this.navigator.requests(player);
            });

            // -------------------------------------------------- name prompts
            case Routes.ADD_FRIEND_SUBMIT -> this.submitAddFriend(player, text(response, Routes.INPUT_NAME));
            case Routes.BLOCK_ADD_SUBMIT -> this.submitBlock(player, text(response, Routes.INPUT_NAME));

            // ------------------------------------------------------ settings
            case Routes.SET_VIEW -> this.updateSettings(player, settings ->
                settings.viewMode(settings.cards() ? PlayerSettings.VIEW_BUTTONS : PlayerSettings.VIEW_CARDS));
            case Routes.SET_TOASTS -> this.updateSettings(player, settings -> settings.toasts(!settings.toasts()));
            case Routes.SET_SOUNDS -> this.updateSettings(player, settings -> settings.sounds(!settings.sounds()));
            case Routes.SET_ACTIONBAR -> this.updateSettings(player, settings -> settings.actionBar(!settings.actionBar()));
            case Routes.SET_REMINDER -> this.updateSettings(player, settings -> settings.reminder(!settings.reminder()));
            case Routes.SET_DM_PRIVACY -> {
                this.notifier.click(player);
                this.updateSettings(player, PlayerSettings::cycleDmPrivacy);
            }
            case Routes.SCALE_OPEN -> this.navigator.guiScale(player);
            case Routes.SCALE -> {
                final int scale = parseInt(parsed.argument());
                if (scale > 0) {
                    this.updateSettings(player, settings -> settings.guiScale(scale));
                } else {
                    this.navigator.settings(player);
                }
            }
            case Routes.COLOR_OPEN -> this.navigator.colorPicker(player);
            case Routes.COLOR -> {
                final String color = parsed.argument();
                if (color != null && Colors.exists(color) && Colors.canUse(player, color)) {
                    this.notifier.sound(player, this.config.soundClick(), 1.0f, 1.2f);
                    this.updateSettings(player, settings -> settings.color(color));
                } else {
                    this.navigator.settings(player);
                }
            }
            default -> {
                // Unknown identifier: nothing to do, the dialog stays as it is.
            }
        }
    }

    // ----------------------------------------------------------------- pieces

    private void openChat(final Player player, final UUID target, final String origin) {
        this.withTarget(player, target, () -> {
            final Session session = this.sessions.of(player.getUniqueId());
            session.resetShowMore(DataStore.convoKey(player.getUniqueId(), target));
            session.chatOrigin(origin);
            this.navigator.chat(player, target);
        });
    }

    private void send(final Player player, final UUID target, final String text) {
        final MessageService.Delivery result = this.messages.deliver(player, target, text);
        switch (result) {
            case COOLDOWN -> this.navigator.chat(player, target, text, "Please wait a bit before sending another message");
            case BLOCKED_BY_YOU -> {
                this.notifier.feedback(player, "<red>You've blocked this player — unblock them to chat.</red>");
                this.navigator.chat(player, target);
            }
            case BLOCKED_BY_THEM -> {
                this.notifier.feedback(player, "<red>Your message could not be delivered.</red>");
                this.navigator.chat(player, target);
            }
            case PRIVACY -> {
                this.notifier.feedback(player, "<yellow>This player isn't accepting messages right now.</yellow>");
                this.navigator.chat(player, target);
            }
            case OK -> {
                this.notifier.sound(player, this.config.soundClick(), 0.4f, 1.0f);
                this.navigator.chat(player, target);
            }
            default -> this.navigator.chat(player, target);
        }
    }

    private void submitAddFriend(final Player player, final String name) {
        if (name == null || name.isBlank()) {
            this.navigator.addFriend(player);
            return;
        }
        final FriendService.AddResult result = this.friends.requestFriend(player, name);
        this.friends.feedback(player, result, name);
        switch (result) {
            case SENT, ALREADY_FRIENDS, ACCEPTED -> this.navigator.friends(player);
            default -> this.navigator.addFriend(player);
        }
    }

    private void submitBlock(final Player player, final String name) {
        if (name == null || name.isBlank()) {
            this.navigator.blockAdd(player);
            return;
        }
        final var resolved = this.friends.lookup().resolve(name);
        if (resolved == null) {
            this.notifier.feedback(player, "<red>No player named '" + name + "' has joined this server.</red>");
            this.navigator.blockAdd(player);
            return;
        }
        if (resolved.uuid().equals(player.getUniqueId())) {
            this.notifier.feedback(player, "<yellow>You can't block yourself.</yellow>");
            this.navigator.blockAdd(player);
            return;
        }
        this.store.rememberName(resolved.uuid(), resolved.name());
        this.friends.block(player, resolved.uuid());
        this.navigator.friends(player);
    }

    private void toggleAutoTpa(final Player player, final UUID target, final boolean here) {
        this.withTarget(player, target, () -> {
            final boolean enabled = this.store.toggleAutoTpa(player.getUniqueId(), target, here);
            this.notifier.click(player);
            final FriendConfig.Integration integration = here ? this.config.autoAcceptTpaHere() : this.config.autoAcceptTpa();
            if (integration.enabled()) {
                player.performCommand(integration.format(this.store.name(target), String.valueOf(enabled)));
            }
            this.navigator.friendPage(player, target);
        });
    }

    /** Closes the dialog and hands the click over to another plugin's command. */
    private void runIntegration(final Player player, final UUID target, final FriendConfig.Integration integration,
                                final String value) {
        if (target == null || !integration.enabled()) {
            return;
        }
        this.navigator.close(player);
        player.performCommand(integration.format(this.store.name(target), value));
    }

    private void updateSettings(final Player player, final java.util.function.Consumer<PlayerSettings> change) {
        change.accept(this.store.settings(player.getUniqueId()));
        this.store.persistPlayer(player.getUniqueId());
        this.navigator.settings(player);
    }

    /** Runs an action that needs a target, falling back to the menu when the id was malformed. */
    private void withTarget(final Player player, final UUID target, final Runnable action) {
        if (target == null) {
            this.navigator.menu(player);
            return;
        }
        action.run();
    }

    private static String text(final DialogResponseView response, final String key) {
        if (response == null) {
            return "";
        }
        final String value = response.getText(key);
        return value == null ? "" : value;
    }

    private static int parseInt(final String raw) {
        try {
            return raw == null ? -1 : Integer.parseInt(raw);
        } catch (final NumberFormatException ignored) {
            return -1;
        }
    }
}
