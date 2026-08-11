package com.faboit.friendsystem;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view over {@code config.yml}. Re-created whenever the config is reloaded. */
public final class FriendConfig {

    /** How the plugin talks to its database. */
    public enum StorageType {
        SQLITE("org.sqlite.JDBC"),
        MYSQL("com.mysql.cj.jdbc.Driver"),
        MARIADB("org.mariadb.jdbc.Driver");

        private final String driverClass;

        StorageType(final String driverClass) {
            this.driverClass = driverClass;
        }

        public String driverClass() {
            return this.driverClass;
        }

        public boolean remote() {
            return this != SQLITE;
        }

        static StorageType parse(final String raw) {
            return switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
                case "mysql" -> MYSQL;
                case "mariadb" -> MARIADB;
                default -> SQLITE;
            };
        }
    }

    /** A button that shells out to another plugin's command, e.g. {@code /pay <name>}. */
    public record Integration(boolean enabled, String command) {

        /** Fills {@code %player%} and {@code %value%} in the configured command template. */
        public String format(final String player, final String value) {
            return this.command
                .replace("%player%", player)
                .replace("%value%", value);
        }
    }

    private final StorageType storageType;
    private final String sqliteFile;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String properties;
    private final int poolSize;
    private final int minimumIdle;
    private final long connectionTimeout;

    private final int cooldownSeconds;
    private final int retentionHours;
    private final int maxMessageLength;
    private final boolean toasts;

    private final String soundMessage;
    private final String soundRequest;
    private final String soundAccept;
    private final String soundClick;
    private final String soundError;
    private final String soundBlock;

    private final Integration pay;
    private final Integration teleport;
    private final Integration stats;
    private final Integration autoAcceptTpa;
    private final Integration autoAcceptTpaHere;
    private final Integration settingsBack;

    public FriendConfig(final FileConfiguration cfg) {
        this.storageType = StorageType.parse(cfg.getString("storage.type", "sqlite"));
        this.sqliteFile = cfg.getString("storage.sqlite.file", "friendsystem.db");
        this.host = cfg.getString("storage.remote.host", "localhost");
        this.port = cfg.getInt("storage.remote.port", 3306);
        this.database = cfg.getString("storage.remote.database", "friendsystem");
        this.username = cfg.getString("storage.remote.username", "root");
        this.password = cfg.getString("storage.remote.password", "");
        this.properties = cfg.getString("storage.remote.properties", "");
        this.poolSize = Math.max(1, cfg.getInt("storage.pool.maximum-pool-size", 8));
        this.minimumIdle = Math.max(0, cfg.getInt("storage.pool.minimum-idle", 2));
        this.connectionTimeout = Math.max(250L, cfg.getLong("storage.pool.connection-timeout-ms", 10_000L));

        this.cooldownSeconds = Math.max(0, cfg.getInt("messages.cooldown-seconds", 5));
        this.retentionHours = Math.max(1, cfg.getInt("messages.retention-hours", 72));
        this.maxMessageLength = Math.clamp(cfg.getInt("messages.max-length", 150), 16, 512);
        this.toasts = cfg.getBoolean("toasts", true);

        this.soundMessage = cfg.getString("sounds.message", "block.note_block.bell");
        this.soundRequest = cfg.getString("sounds.request", "entity.player.levelup");
        this.soundAccept = cfg.getString("sounds.accept", "entity.player.levelup");
        this.soundClick = cfg.getString("sounds.click", "ui.button.click");
        this.soundError = cfg.getString("sounds.error", "block.note_block.bass");
        this.soundBlock = cfg.getString("sounds.block", "block.anvil.land");

        this.pay = integration(cfg, "integrations.pay", "pay %player%");
        this.teleport = integration(cfg, "integrations.teleport", "tpa %player%");
        this.stats = integration(cfg, "integrations.stats", "stats %player%");
        this.autoAcceptTpa = integration(cfg, "integrations.auto-accept-tpa", "autoaccepttpafrom %player% %value%");
        this.autoAcceptTpaHere = integration(cfg, "integrations.auto-accept-tpa-here", "autoaccepttpaherefrom %player% %value%");
        final ConfigurationSection back = cfg.getConfigurationSection("integrations.settings-back");
        this.settingsBack = new Integration(
            back != null && back.getBoolean("enabled", false),
            back == null ? "settings" : back.getString("command", "settings"));
    }

    private static Integration integration(final FileConfiguration cfg, final String path, final String fallback) {
        final ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) {
            return new Integration(true, fallback);
        }
        return new Integration(section.getBoolean("enabled", true), section.getString("command", fallback));
    }

    /** The JDBC URL for the configured storage backend. */
    public String jdbcUrl(final java.io.File dataFolder) {
        return switch (this.storageType) {
            case SQLITE -> "jdbc:sqlite:" + new java.io.File(dataFolder, this.sqliteFile).getAbsolutePath();
            case MYSQL -> "jdbc:mysql://" + this.host + ':' + this.port + '/' + this.database + suffix();
            case MARIADB -> "jdbc:mariadb://" + this.host + ':' + this.port + '/' + this.database + suffix();
        };
    }

    private String suffix() {
        return this.properties == null || this.properties.isBlank() ? "" : '?' + this.properties;
    }

    public StorageType storageType() {
        return this.storageType;
    }

    public String username() {
        return this.username;
    }

    public String password() {
        return this.password;
    }

    public int poolSize() {
        return this.storageType.remote() ? this.poolSize : 1;
    }

    public int minimumIdle() {
        return this.storageType.remote() ? Math.min(this.minimumIdle, this.poolSize) : 1;
    }

    public long connectionTimeout() {
        return this.connectionTimeout;
    }

    public int cooldownSeconds() {
        return this.cooldownSeconds;
    }

    public int retentionHours() {
        return this.retentionHours;
    }

    public int maxMessageLength() {
        return this.maxMessageLength;
    }

    public boolean toasts() {
        return this.toasts;
    }

    public String soundMessage() {
        return this.soundMessage;
    }

    public String soundRequest() {
        return this.soundRequest;
    }

    public String soundAccept() {
        return this.soundAccept;
    }

    public String soundClick() {
        return this.soundClick;
    }

    public String soundError() {
        return this.soundError;
    }

    public String soundBlock() {
        return this.soundBlock;
    }

    public Integration pay() {
        return this.pay;
    }

    public Integration teleport() {
        return this.teleport;
    }

    public Integration stats() {
        return this.stats;
    }

    public Integration autoAcceptTpa() {
        return this.autoAcceptTpa;
    }

    public Integration autoAcceptTpaHere() {
        return this.autoAcceptTpaHere;
    }

    /** Where the settings dialog's Back button goes when it was opened by a command. */
    public Integration settingsBack() {
        return this.settingsBack;
    }
}
