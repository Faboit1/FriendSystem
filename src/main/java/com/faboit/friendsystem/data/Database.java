package com.faboit.friendsystem.data;

import com.faboit.friendsystem.FriendConfig;
import com.faboit.friendsystem.FriendConfig.StorageType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

/**
 * Thin JDBC layer shared by all three supported backends. SQLite is the default;
 * MySQL and MariaDB only differ in the DDL types and the upsert syntax, which is
 * why the few dialect-dependent statements are built through {@link #upsert} and
 * {@link #insertIgnore}.
 *
 * <p>Every method here blocks, so it is only ever called from {@link DataStore}'s
 * single database thread (or from {@code onEnable}, where blocking is fine).</p>
 */
public final class Database implements AutoCloseable {

    private final FriendConfig config;
    private final File dataFolder;
    private HikariDataSource source;

    public Database(final FriendConfig config, final File dataFolder) {
        this.config = config;
        this.dataFolder = dataFolder;
    }

    // ------------------------------------------------------------------ setup

    public void connect() throws SQLException {
        final StorageType type = this.config.storageType();
        final HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("FriendSystem-" + type.name().toLowerCase(java.util.Locale.ROOT));
        hikari.setJdbcUrl(this.config.jdbcUrl(this.dataFolder));
        hikari.setDriverClassName(type.driverClass());
        hikari.setMaximumPoolSize(this.config.poolSize());
        hikari.setMinimumIdle(this.config.minimumIdle());
        hikari.setConnectionTimeout(this.config.connectionTimeout());
        if (type.remote()) {
            hikari.setUsername(this.config.username());
            hikari.setPassword(this.config.password());
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("useServerPrepStmts", "true");
        }
        this.source = new HikariDataSource(hikari);
        if (type == StorageType.SQLITE) {
            try (Connection connection = this.source.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
        }
        this.createSchema();
    }

    private void createSchema() throws SQLException {
        final boolean sqlite = this.config.storageType() == StorageType.SQLITE;
        final String bool = sqlite ? "INTEGER" : "TINYINT";
        final String messageId = sqlite
            ? "id INTEGER PRIMARY KEY AUTOINCREMENT"
            : "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        try (Connection connection = this.source.getConnection(); Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_players (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    name VARCHAR(16),
                    last_seen BIGINT,
                    view_mode VARCHAR(16) NOT NULL,
                    toasts %1$s NOT NULL,
                    sounds %1$s NOT NULL,
                    action_bar %1$s NOT NULL,
                    reminder %1$s NOT NULL,
                    dm_privacy VARCHAR(16) NOT NULL,
                    gui_scale INTEGER NOT NULL,
                    color VARCHAR(16) NOT NULL,
                    last_convo VARCHAR(36)
                )""".formatted(bool));
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_friends (
                    owner VARCHAR(36) NOT NULL,
                    friend VARCHAR(36) NOT NULL,
                    auto_tpa %1$s NOT NULL,
                    auto_tpa_here %1$s NOT NULL,
                    PRIMARY KEY (owner, friend)
                )""".formatted(bool));
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_requests (
                    target VARCHAR(36) NOT NULL,
                    requester VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (target, requester)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_blocked (
                    owner VARCHAR(36) NOT NULL,
                    target VARCHAR(36) NOT NULL,
                    PRIMARY KEY (owner, target)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_ignored (
                    owner VARCHAR(36) NOT NULL,
                    target VARCHAR(36) NOT NULL,
                    PRIMARY KEY (owner, target)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_unread (
                    owner VARCHAR(36) NOT NULL,
                    other VARCHAR(36) NOT NULL,
                    amount INTEGER NOT NULL,
                    PRIMARY KEY (owner, other)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS fs_messages (
                    %s,
                    convo VARCHAR(73) NOT NULL,
                    sender VARCHAR(36) NOT NULL,
                    receiver VARCHAR(36) NOT NULL,
                    content VARCHAR(512) NOT NULL,
                    sent_at BIGINT NOT NULL
                )""".formatted(messageId));
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_fs_messages_convo ON fs_messages (convo, sent_at)");
        }
    }

    @Override
    public void close() {
        if (this.source != null && !this.source.isClosed()) {
            this.source.close();
        }
    }

    // ---------------------------------------------------------------- loading

    /** Reads the whole dataset into memory. Called once during {@code onEnable}. */
    public void loadInto(final DataStore store, final Instant messagesSince) throws SQLException {
        try (Connection connection = this.source.getConnection()) {
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_players")) {
                while (rs.next()) {
                    final UUID uuid = uuid(rs.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    final PlayerSettings settings = new PlayerSettings();
                    settings.viewMode(rs.getString("view_mode"));
                    settings.toasts(rs.getBoolean("toasts"));
                    settings.sounds(rs.getBoolean("sounds"));
                    settings.actionBar(rs.getBoolean("action_bar"));
                    settings.reminder(rs.getBoolean("reminder"));
                    settings.dmPrivacy(rs.getString("dm_privacy"));
                    settings.guiScale(rs.getInt("gui_scale"));
                    settings.color(rs.getString("color"));
                    final long lastSeen = rs.getLong("last_seen");
                    store.loadPlayer(uuid, rs.getString("name"), lastSeen <= 0 ? null : Instant.ofEpochMilli(lastSeen), settings);
                    final UUID convo = uuid(rs.getString("last_convo"));
                    if (convo != null) {
                        store.loadLastConvo(uuid, convo);
                    }
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_friends")) {
                while (rs.next()) {
                    final UUID owner = uuid(rs.getString("owner"));
                    final UUID friend = uuid(rs.getString("friend"));
                    if (owner != null && friend != null) {
                        store.loadFriend(owner, friend, rs.getBoolean("auto_tpa"), rs.getBoolean("auto_tpa_here"));
                    }
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_requests")) {
                while (rs.next()) {
                    final UUID target = uuid(rs.getString("target"));
                    final UUID requester = uuid(rs.getString("requester"));
                    if (target != null && requester != null) {
                        store.loadRequest(target, requester);
                    }
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_blocked")) {
                while (rs.next()) {
                    final UUID owner = uuid(rs.getString("owner"));
                    final UUID target = uuid(rs.getString("target"));
                    if (owner != null && target != null) {
                        store.loadBlocked(owner, target);
                    }
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_ignored")) {
                while (rs.next()) {
                    final UUID owner = uuid(rs.getString("owner"));
                    final UUID target = uuid(rs.getString("target"));
                    if (owner != null && target != null) {
                        store.loadIgnored(owner, target);
                    }
                }
            }
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM fs_unread")) {
                while (rs.next()) {
                    final UUID owner = uuid(rs.getString("owner"));
                    final UUID other = uuid(rs.getString("other"));
                    if (owner != null && other != null) {
                        store.loadUnread(owner, other, rs.getInt("amount"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, convo, sender, content, sent_at FROM fs_messages WHERE sent_at >= ? ORDER BY id ASC")) {
                ps.setLong(1, messagesSince.toEpochMilli());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final UUID sender = uuid(rs.getString("sender"));
                        if (sender == null) {
                            continue;
                        }
                        store.loadMessage(rs.getString("convo"), new Message(
                            rs.getLong("id"), sender, rs.getString("content"), Instant.ofEpochMilli(rs.getLong("sent_at"))));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- writing

    public void savePlayer(final UUID uuid, final String name, final Instant lastSeen, final PlayerSettings settings,
                           final UUID lastConvo) throws SQLException {
        final String sql = upsert(
            "fs_players",
            "uuid, name, last_seen, view_mode, toasts, sounds, action_bar, reminder, dm_privacy, gui_scale, color, last_convo",
            "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
            "uuid",
            "name", "last_seen", "view_mode", "toasts", "sounds", "action_bar", "reminder", "dm_privacy", "gui_scale", "color",
            "last_convo");
        try (Connection connection = this.source.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, lastSeen == null ? 0L : lastSeen.toEpochMilli());
            ps.setString(4, settings.viewMode());
            ps.setBoolean(5, settings.toasts());
            ps.setBoolean(6, settings.sounds());
            ps.setBoolean(7, settings.actionBar());
            ps.setBoolean(8, settings.reminder());
            ps.setString(9, settings.dmPrivacy());
            ps.setInt(10, settings.guiScale());
            ps.setString(11, settings.color());
            ps.setString(12, lastConvo == null ? null : lastConvo.toString());
            ps.executeUpdate();
        }
    }

    public void saveFriend(final UUID owner, final UUID friend, final boolean autoTpa, final boolean autoTpaHere) throws SQLException {
        final String sql = upsert("fs_friends", "owner, friend, auto_tpa, auto_tpa_here", "?, ?, ?, ?",
            "owner, friend", "auto_tpa", "auto_tpa_here");
        try (Connection connection = this.source.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setString(2, friend.toString());
            ps.setBoolean(3, autoTpa);
            ps.setBoolean(4, autoTpaHere);
            ps.executeUpdate();
        }
    }

    public void deleteFriend(final UUID owner, final UUID friend) throws SQLException {
        this.executePair("DELETE FROM fs_friends WHERE owner = ? AND friend = ?", owner, friend);
    }

    public void saveRequest(final UUID target, final UUID requester, final Instant when) throws SQLException {
        final String sql = insertIgnore("fs_requests", "target, requester, created_at", "?, ?, ?");
        try (Connection connection = this.source.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setString(2, requester.toString());
            ps.setLong(3, when.toEpochMilli());
            ps.executeUpdate();
        }
    }

    public void deleteRequest(final UUID target, final UUID requester) throws SQLException {
        this.executePair("DELETE FROM fs_requests WHERE target = ? AND requester = ?", target, requester);
    }

    public void saveBlocked(final UUID owner, final UUID target) throws SQLException {
        this.executePair(insertIgnore("fs_blocked", "owner, target", "?, ?"), owner, target);
    }

    public void deleteBlocked(final UUID owner, final UUID target) throws SQLException {
        this.executePair("DELETE FROM fs_blocked WHERE owner = ? AND target = ?", owner, target);
    }

    public void saveIgnored(final UUID owner, final UUID target) throws SQLException {
        this.executePair(insertIgnore("fs_ignored", "owner, target", "?, ?"), owner, target);
    }

    public void deleteIgnored(final UUID owner, final UUID target) throws SQLException {
        this.executePair("DELETE FROM fs_ignored WHERE owner = ? AND target = ?", owner, target);
    }

    public void saveUnread(final UUID owner, final UUID other, final int amount) throws SQLException {
        final String sql = upsert("fs_unread", "owner, other, amount", "?, ?, ?", "owner, other", "amount");
        try (Connection connection = this.source.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setString(2, other.toString());
            ps.setInt(3, amount);
            ps.executeUpdate();
        }
    }

    public void deleteUnread(final UUID owner, final UUID other) throws SQLException {
        this.executePair("DELETE FROM fs_unread WHERE owner = ? AND other = ?", owner, other);
    }

    /** Inserts a message and returns the generated row id, which doubles as the in-memory id. */
    public long insertMessage(final String convo, final UUID sender, final UUID receiver, final String content, final Instant sentAt)
        throws SQLException {
        try (Connection connection = this.source.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO fs_messages (convo, sender, receiver, content, sent_at) VALUES (?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, convo);
            ps.setString(2, sender.toString());
            ps.setString(3, receiver.toString());
            ps.setString(4, content);
            ps.setLong(5, sentAt.toEpochMilli());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public int deleteMessagesBefore(final Instant cutoff) throws SQLException {
        try (Connection connection = this.source.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM fs_messages WHERE sent_at < ?")) {
            ps.setLong(1, cutoff.toEpochMilli());
            return ps.executeUpdate();
        }
    }

    private void executePair(final String sql, final UUID first, final UUID second) throws SQLException {
        try (Connection connection = this.source.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- dialect

    private String upsert(final String table, final String columns, final String values,
                          final String conflictKey, final String... updated) {
        final StringBuilder sql = new StringBuilder("INSERT INTO ").append(table)
            .append(" (").append(columns).append(") VALUES (").append(values).append(')');
        if (this.config.storageType() == StorageType.SQLITE) {
            sql.append(" ON CONFLICT(").append(conflictKey).append(") DO UPDATE SET ");
            for (int i = 0; i < updated.length; i++) {
                sql.append(i == 0 ? "" : ", ").append(updated[i]).append(" = excluded.").append(updated[i]);
            }
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updated.length; i++) {
                sql.append(i == 0 ? "" : ", ").append(updated[i]).append(" = VALUES(").append(updated[i]).append(')');
            }
        }
        return sql.toString();
    }

    private String insertIgnore(final String table, final String columns, final String values) {
        final String verb = this.config.storageType() == StorageType.SQLITE ? "INSERT OR IGNORE INTO " : "INSERT IGNORE INTO ";
        return verb + table + " (" + columns + ") VALUES (" + values + ')';
    }

    private static UUID uuid(final String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }
}
