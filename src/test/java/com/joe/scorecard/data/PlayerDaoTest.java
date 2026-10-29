package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDaoTest {

    private static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);
    private static final LocalDate JUN_1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate AUG_1 = LocalDate.of(2026, 8, 1);

    private Database db;
    private Team giants;
    private Team dodgers;

    @BeforeEach
    void open() throws SQLException {
        db = Database.openInMemory();
        giants = team("Giants");
        dodgers = team("Dodgers");
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private Team team(String name) throws SQLException {
        Team t = new Team();
        t.setName(name);
        db.teams().insert(t);
        return t;
    }

    private Player player(String name, int number, Team team, LocalDate from) throws SQLException {
        Player p = new Player();
        p.setName(name);
        p.setNumber(number);
        p.setTeamId(team.getId());
        db.players().insert(p, from);
        return p;
    }

    private Player asOf(Player p, LocalDate date) throws SQLException {
        return db.players().findById(p.getId(), date).orElseThrow();
    }

    @Test
    void tradeKeepsOldTeamForEarlierDates() throws SQLException {
        Player p = player("Mays", 24, giants, APR_1);
        p.setTeamId(dodgers.getId());
        p.setNumber(8);
        db.players().update(p, JUN_1);

        assertEquals(giants.getId(), asOf(p, LocalDate.of(2026, 5, 31)).getTeamId());
        assertEquals(24, asOf(p, LocalDate.of(2026, 5, 31)).getNumber());
        assertEquals(dodgers.getId(), asOf(p, JUN_1).getTeamId());
        assertEquals(8, asOf(p, JUN_1).getNumber());
        assertEquals(dodgers.getId(), db.players().findById(p.getId()).orElseThrow().getTeamId());

        assertEquals(1, db.players().findByTeam(giants.getId(), APR_1).size());
        assertTrue(db.players().findByTeam(giants.getId()).isEmpty());
        assertEquals(1, db.players().findByTeam(dodgers.getId()).size());
        assertEquals(2, db.players().history(p.getId()).size());
    }

    @Test
    void nameOnlyChangeKeepsOneStint() throws SQLException {
        Player p = player("Mays", 24, giants, APR_1);
        p.setName("Willie Mays");
        db.players().update(p, JUN_1);
        assertEquals("Willie Mays", asOf(p, APR_1).getName());
        assertEquals(1, db.players().history(p.getId()).size());
    }

    @Test
    void changeOnStartDateCorrectsStintInPlace() throws SQLException {
        Player p = player("Mays", 42, giants, APR_1);
        p.setNumber(24);
        db.players().update(p, APR_1);
        assertEquals(1, db.players().history(p.getId()).size());
        assertEquals(24, asOf(p, APR_1).getNumber());
    }

    @Test
    void changeBeforeCurrentStintIsRejected() throws SQLException {
        Player p = player("Mays", 24, giants, JUN_1);
        p.setNumber(8);
        assertThrows(IllegalArgumentException.class, () -> db.players().update(p, APR_1));
        assertEquals(24, asOf(p, JUN_1).getNumber());
    }

    @Test
    void removedPlayerStillShowsInEarlierGames() throws SQLException {
        Player p = player("Mays", 24, giants, APR_1);
        db.players().remove(p.getId(), JUN_1);

        assertTrue(db.players().findCurrent().isEmpty());
        assertTrue(db.players().findByTeam(giants.getId()).isEmpty());
        assertTrue(db.players().findByTeam(giants.getId(), JUN_1).isEmpty());
        assertEquals(1, db.players().findByTeam(giants.getId(), APR_1).size());

        Player old = asOf(p, APR_1);
        assertEquals(24, old.getNumber());
        assertEquals(JUN_1, old.getRemoved());
        // Even after removal, a lookup for a later date finds their last team.
        assertEquals(giants.getId(), asOf(p, AUG_1).getTeamId());

        assertThrows(IllegalArgumentException.class, () -> db.players().remove(p.getId(), AUG_1));
        p.setNumber(8);
        assertThrows(IllegalArgumentException.class, () -> db.players().update(p, AUG_1));
    }

    @Test
    void dateBeforeFirstStintUsesFirstStint() throws SQLException {
        Player p = player("Mays", 24, giants, JUN_1);
        assertEquals(24, asOf(p, APR_1).getNumber());
        assertEquals(giants.getId(), asOf(p, APR_1).getTeamId());
    }

    @Test
    void failedInsertLeavesNothingBehind() throws SQLException {
        Player p = new Player();
        p.setName("Ghost");
        p.setTeamId(999);
        assertThrows(SQLException.class, () -> db.players().insert(p, APR_1));
        // The player row was written before the stint failed, then rolled back.
        assertNotEquals(0, p.getId());
        assertTrue(db.players().findById(p.getId()).isEmpty());
    }

    @Test
    void hasPlayedForIncludesPastPlayers() throws SQLException {
        Player p = player("Mays", 24, giants, APR_1);
        db.players().remove(p.getId(), JUN_1);
        assertTrue(db.players().hasPlayedFor(giants.getId()));
        assertThrows(SQLException.class, () -> db.teams().delete(giants.getId()));
    }

    @Test
    void upgradesVersionZeroDatabase(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("old.db").toString();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE team (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, city TEXT)");
            st.execute("""
                    CREATE TABLE player (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT,
                        number INTEGER NOT NULL DEFAULT 0, team_id INTEGER REFERENCES team(id))""");
            st.execute("""
                    CREATE TABLE game (id INTEGER PRIMARY KEY AUTOINCREMENT,
                        home_team_id INTEGER REFERENCES team(id),
                        away_team_id INTEGER REFERENCES team(id))""");
            st.execute("""
                    CREATE TABLE atbat (id INTEGER PRIMARY KEY AUTOINCREMENT,
                        game_id INTEGER REFERENCES game(id),
                        batter_id INTEGER REFERENCES player(id),
                        pitcher_id INTEGER REFERENCES player(id))""");
            st.execute("INSERT INTO team (id, name, city) VALUES (7, 'Giants', 'San Francisco')");
            st.execute("INSERT INTO player (id, name, number, team_id) VALUES (3, 'Mays', 24, 7)");
        }

        try (Database upgraded = Database.open(path)) {
            Player p = upgraded.players().findById(3, APR_1).orElseThrow();
            assertEquals("Mays", p.getName());
            assertEquals(24, p.getNumber());
            assertEquals(7, p.getTeamId());
            assertNull(upgraded.players().history(3).get(0).getStartDate());
            assertEquals(List.of(3L), upgraded.players().findByTeam(7, APR_1).stream().map(Player::getId).toList());

            Team t = upgraded.teams().findById(7, APR_1).orElseThrow();
            assertEquals("Giants", t.getName());
            assertEquals("San Francisco", t.getCity());
            assertNull(upgraded.teams().history(7).get(0).getStartDate());

            // New rows must not reuse the ids that were copied over.
            Team next = new Team();
            next.setName("Dodgers");
            upgraded.teams().insert(next);
            assertTrue(next.getId() > 7);
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'atbat'")) {
                String atbat = rs.getString(1);
                assertTrue(atbat.contains("REFERENCES player(id)"), atbat);
            }
            try (ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'player_stint'")) {
                String stint = rs.getString(1);
                assertTrue(stint.contains("REFERENCES team(id)"), stint);
            }
            try (ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'game'")) {
                String game = rs.getString(1);
                assertTrue(!game.contains("team_v1"), game);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE name IN ('player_v0', 'team_v1')")) {
                assertTrue(!rs.next(), "old tables should be dropped");
            }
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertEquals(7, rs.getInt(1));
            }
        }
    }

    @Test
    void reopeningAnUpgradedDatabaseKeepsData(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("s.db").toString();
        long id;
        try (Database first = Database.open(path)) {
            Team t = new Team();
            t.setName("Giants");
            first.teams().insert(t);
            Player p = new Player();
            p.setName("Mays");
            p.setTeamId(t.getId());
            first.players().insert(p, APR_1);
            id = p.getId();
        }
        try (Database second = Database.open(path)) {
            assertEquals("Mays", second.players().findById(id).orElseThrow().getName());
        }
    }
}
