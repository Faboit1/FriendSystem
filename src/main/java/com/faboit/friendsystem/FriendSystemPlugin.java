package com.faboit.friendsystem;

import com.faboit.friendsystem.command.FriendsCommand;
import com.faboit.friendsystem.command.FsOpenCommand;
import com.faboit.friendsystem.command.MessageCommand;
import com.faboit.friendsystem.command.ReplyCommand;
import com.faboit.friendsystem.data.DataStore;
import com.faboit.friendsystem.data.Database;
import com.faboit.friendsystem.listener.ClickRouter;
import com.faboit.friendsystem.listener.ConnectionListener;
import com.faboit.friendsystem.papi.FriendPlaceholders;
import com.faboit.friendsystem.service.FriendService;
import com.faboit.friendsystem.service.MessageService;
import com.faboit.friendsystem.service.Notifier;
import com.faboit.friendsystem.service.PlayerLookup;
import com.faboit.friendsystem.service.Scheduling;
import com.faboit.friendsystem.service.SessionManager;
import com.faboit.friendsystem.service.ToastService;
import com.faboit.friendsystem.service.UnreadTags;
import com.faboit.friendsystem.ui.DialogFactory;
import com.faboit.friendsystem.ui.Navigator;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Friends, direct messages and player blocking, driven entirely by 1.21.6+ dialogs.
 *
 * <p>Converted from the {@code friendsystem.sk} Skript. State lives in memory and is
 * written back to SQLite (or MySQL/MariaDB) on a background thread, and everything
 * that touches a player goes through that player's scheduler, so the plugin runs
 * unchanged on both Paper and Folia.</p>
 */
public final class FriendSystemPlugin extends JavaPlugin {

    private static final long REMINDER_PERIOD_SECONDS = 60L;
    private static final long PRUNE_PERIOD_MINUTES = 30L;

    private Database database;
    private DataStore store;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        final FriendConfig config = new FriendConfig(this.getConfig());

        this.database = new Database(config, this.getDataFolder());
        try {
            this.database.connect();
        } catch (final SQLException exception) {
            this.getLogger().log(Level.SEVERE, "Could not open the "
                + config.storageType().name().toLowerCase(java.util.Locale.ROOT) + " database — disabling.", exception);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.store = new DataStore(this.database, this.getLogger());
        final SessionManager sessions = new SessionManager();
        final ToastService toasts = new ToastService(this.store, this.getLogger(), config.toasts());
        final Notifier notifier = new Notifier(this.store, config, toasts);
        final UnreadTags tags = new UnreadTags(this, this.store);
        final PlayerLookup lookup = new PlayerLookup(this.store);
        final MessageService messages = new MessageService(this, this.store, config, notifier, sessions, tags);

        try {
            this.database.loadInto(this.store, messages.retentionCutoff());
        } catch (final SQLException exception) {
            this.getLogger().log(Level.SEVERE, "Could not load stored friends data — disabling.", exception);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final FriendService friends = new FriendService(this, this.store, config, notifier, lookup, tags);
        final DialogFactory dialogs = new DialogFactory(this.store, config, sessions);
        final Navigator navigator = new Navigator(dialogs, messages);

        toasts.register();

        this.getServer().getPluginManager().registerEvents(
            new ClickRouter(this.store, config, sessions, navigator, friends, messages, notifier), this);
        this.getServer().getPluginManager().registerEvents(
            new ConnectionListener(this, this.store, sessions, tags, notifier), this);

        this.bind("friends", new FriendsCommand(this.store, config, sessions, navigator, friends, notifier, lookup));
        this.bind("message", new MessageCommand(this.store, sessions, navigator, messages, notifier, lookup));
        this.bind("reply", new ReplyCommand(this.store, sessions, navigator, messages, notifier));
        this.bind("fsopen", new FsOpenCommand(this.store, navigator));

        this.startTasks(messages, tags);
        this.registerPlaceholders();

        this.getLogger().info("FriendSystem enabled using "
            + config.storageType().name().toLowerCase(java.util.Locale.ROOT) + " storage.");
    }

    @Override
    public void onDisable() {
        Bukkit.getAsyncScheduler().cancelTasks(this);
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        if (this.store != null) {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                this.store.markSeen(player.getUniqueId());
            }
            this.store.shutdown();
        }
        if (this.database != null) {
            this.database.close();
        }
    }

    private void bind(final String name, final CommandExecutor executor) {
        final PluginCommand command = this.getCommand(name);
        if (command == null) {
            this.getLogger().warning("Command /" + name + " is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    /**
     * Both background jobs run on the async scheduler: pruning is pure data work, and
     * the reminder hops onto each player's own thread before touching them.
     */
    private void startTasks(final MessageService messages, final UnreadTags tags) {
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                final UUID uuid = player.getUniqueId();
                tags.refresh(uuid);
                if (!this.store.settings(uuid).reminder()) {
                    continue;
                }
                final int unread = this.store.totalUnread(uuid, true);
                if (unread > 0) {
                    Scheduling.onPlayer(this, player, target -> target.sendActionBar(DialogFactory.mm(
                        "<gray>You have <white>" + unread + "</white> unread message(s)</gray> <dark_gray>/friends</dark_gray>")));
                }
            }
        }, REMINDER_PERIOD_SECONDS, REMINDER_PERIOD_SECONDS, TimeUnit.SECONDS);

        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            final int removed = messages.prune();
            if (removed > 0) {
                this.getLogger().fine("Pruned " + removed + " expired message(s).");
            }
        }, PRUNE_PERIOD_MINUTES, PRUNE_PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    private void registerPlaceholders() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new FriendPlaceholders(this, this.store).register();
            this.getLogger().info("Registered PlaceholderAPI placeholders.");
        } catch (final LinkageError | RuntimeException error) {
            this.getLogger().log(Level.WARNING, "Could not register PlaceholderAPI placeholders.", error);
        }
    }
}
