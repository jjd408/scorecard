package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A SQLite database holding the scorecard data. Opening it creates any missing
 * tables. Use the DAO accessors, such as {@link #teams()}, to read and write
 * each data object.
 */
public class Database implements AutoCloseable {

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS team (
            id INTEGER PRIMARY KEY AUTOINCREMENT
        )""",
        """
        CREATE TABLE IF NOT EXISTS team_era (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            team_id    INTEGER NOT NULL REFERENCES team(id),
            name       TEXT,
            city       TEXT,
            start_date TEXT,
            end_date   TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS player (
            id      INTEGER PRIMARY KEY AUTOINCREMENT,
            name    TEXT,
            removed TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS player_stint (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            player_id  INTEGER NOT NULL REFERENCES player(id),
            team_id    INTEGER REFERENCES team(id),
            number     INTEGER NOT NULL DEFAULT 0,
            start_date TEXT,
            end_date   TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS game (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            home_team_id INTEGER REFERENCES team(id),
            away_team_id INTEGER REFERENCES team(id),
            date         TEXT,
            game_number  INTEGER NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS atbat (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            game_id       INTEGER REFERENCES game(id),
            batter_id     INTEGER REFERENCES player(id),
            pitcher_id    INTEGER REFERENCES player(id),
            pitches       INTEGER NOT NULL DEFAULT 0,
            balls         INTEGER NOT NULL DEFAULT 0,
            strikes       INTEGER NOT NULL DEFAULT 0,
            inning        INTEGER NOT NULL DEFAULT 0,
            batting_order INTEGER NOT NULL DEFAULT 0,
            rbis          INTEGER NOT NULL DEFAULT 0,
            active        INTEGER NOT NULL DEFAULT 0,
            card_row      INTEGER NOT NULL DEFAULT 0,
            card_column   INTEGER NOT NULL DEFAULT 0,
            team_id       INTEGER REFERENCES team(id)
        )""",
        """
        CREATE TABLE IF NOT EXISTS abbrev (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            abbrev      TEXT,
            description TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS play (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            atbat_id  INTEGER REFERENCES atbat(id),
            pitches   INTEGER NOT NULL DEFAULT 0,
            balls     INTEGER NOT NULL DEFAULT 0,
            strikes   INTEGER NOT NULL DEFAULT 0,
            abbrev_id INTEGER REFERENCES abbrev(id),
            base      INTEGER NOT NULL DEFAULT 0,
            out       INTEGER NOT NULL DEFAULT 0,
            comment   TEXT,
            field     INTEGER NOT NULL DEFAULT 0,
            advanced_by INTEGER NOT NULL DEFAULT 0
        )""",
        """
        CREATE TABLE IF NOT EXISTS setting (
            key   TEXT PRIMARY KEY,
            value TEXT
        )""",
        "DROP INDEX IF EXISTS player_team",
        "CREATE INDEX IF NOT EXISTS era_team ON team_era(team_id)",
        "CREATE INDEX IF NOT EXISTS stint_player ON player_stint(player_id)",
        "CREATE INDEX IF NOT EXISTS stint_team ON player_stint(team_id)",
        "CREATE INDEX IF NOT EXISTS atbat_game ON atbat(game_id)",
        "CREATE INDEX IF NOT EXISTS play_atbat ON play(atbat_id)",
    };

    /** Stored in the database file's user_version; raise it when the schema changes. */
    private static final int SCHEMA_VERSION = 7;

    private final Connection conn;
    private final TeamDao teams;
    private final PlayerDao players;
    private final GameDao games;
    private final AtbatDao atbats;
    private final PlayDao plays;
    private final AbbrevDao abbrevs;
    private final SettingDao settings;

    /** Opens (creating if needed) the database file at {@code path}. */
    public static Database open(String path) throws SQLException {
        return new Database(DriverManager.getConnection("jdbc:sqlite:" + path));
    }

    /** Opens a new empty database that lives only in memory, for testing. */
    public static Database openInMemory() throws SQLException {
        return new Database(DriverManager.getConnection("jdbc:sqlite::memory:"));
    }

    private Database(Connection conn) throws SQLException {
        this.conn = conn;
        try (Statement st = conn.createStatement()) {
            if (userVersion(st) < SCHEMA_VERSION) {
                upgrade(st);
            }
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        teams = new TeamDao(conn);
        players = new PlayerDao(conn);
        games = new GameDao(conn);
        atbats = new AtbatDao(conn);
        plays = new PlayDao(conn);
        abbrevs = new AbbrevDao(conn);
        settings = new SettingDao(conn);
    }

    private static int userVersion(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    /**
     * Creates missing tables and converts tables from older versions of the
     * app. Must run before foreign keys are turned on.
     */
    private void upgrade(Statement st) throws SQLException {
        // Keeps a table rename from also repointing other tables' foreign keys
        // at the renamed table; they should keep referring to the new one.
        st.execute("PRAGMA legacy_alter_table = ON");
        conn.setAutoCommit(false);
        try {
            // Before version 1, team and number were kept on the player; before
            // version 2, name and city were kept on the team. Move them into
            // their history tables, going back to the beginning.
            boolean oldPlayer = hasColumn(st, "player", "team_id");
            boolean oldTeam = hasColumn(st, "team", "name");
            if (oldPlayer) {
                st.execute("ALTER TABLE player RENAME TO player_v0");
            }
            if (oldTeam) {
                st.execute("ALTER TABLE team RENAME TO team_v1");
            }
            for (String sql : SCHEMA) {
                st.execute(sql);
            }
            if (oldTeam) {
                st.execute("INSERT INTO team (id) SELECT id FROM team_v1");
                st.execute("INSERT INTO team_era (team_id, name, city) SELECT id, name, city FROM team_v1");
            }
            if (oldPlayer) {
                st.execute("INSERT INTO player (id, name) SELECT id, name FROM player_v0");
                st.execute("""
                        INSERT INTO player_stint (player_id, team_id, number)
                        SELECT id, team_id, number FROM player_v0""");
                st.execute("DROP TABLE player_v0");
            }
            if (oldTeam) {
                st.execute("DROP TABLE team_v1");
            }
            // Version 4 added the at-bat's row and column on the scorecard.
            if (!hasColumn(st, "atbat", "card_row")) {
                st.execute("ALTER TABLE atbat ADD COLUMN card_row INTEGER NOT NULL DEFAULT 0");
                st.execute("ALTER TABLE atbat ADD COLUMN card_column INTEGER NOT NULL DEFAULT 0");
            }
            // Version 5 added the batting team.
            if (!hasColumn(st, "atbat", "team_id")) {
                st.execute("ALTER TABLE atbat ADD COLUMN team_id INTEGER REFERENCES team(id)");
            }
            // Version 6 added the fielding position the ball was played to.
            if (!hasColumn(st, "play", "field")) {
                st.execute("ALTER TABLE play ADD COLUMN field INTEGER NOT NULL DEFAULT 0");
            }
            // Version 7 added the batter whose play moved a runner up.
            if (!hasColumn(st, "play", "advanced_by")) {
                st.execute("ALTER TABLE play ADD COLUMN advanced_by INTEGER NOT NULL DEFAULT 0");
            }
            st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            st.execute("PRAGMA legacy_alter_table = OFF");
        }
    }

    private static boolean hasColumn(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    public TeamDao teams() {
        return teams;
    }

    public PlayerDao players() {
        return players;
    }

    public GameDao games() {
        return games;
    }

    public AtbatDao atbats() {
        return atbats;
    }

    public PlayDao plays() {
        return plays;
    }

    public AbbrevDao abbrevs() {
        return abbrevs;
    }

    public SettingDao settings() {
        return settings;
    }

    /**
     * Runs {@code work} in a single transaction: every change is saved if it
     * finishes, and none are if it throws.
     */
    public void inTransaction(Work work) throws SQLException {
        Dao.atomically(conn, work);
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    /** A block of database work for {@link #inTransaction}. */
    @FunctionalInterface
    public interface Work {
        void run() throws SQLException;
    }
}
