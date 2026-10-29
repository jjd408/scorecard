package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    private Database db;

    @BeforeEach
    void open() throws SQLException {
        db = Database.openInMemory();
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private Team team(String name, String city) throws SQLException {
        Team t = new Team();
        t.setName(name);
        t.setCity(city);
        db.teams().insert(t);
        return t;
    }

    private Player player(String name, int number, Team team) throws SQLException {
        Player p = new Player();
        p.setName(name);
        p.setNumber(number);
        p.setTeamId(team.getId());
        db.players().insert(p, LocalDate.of(2026, 1, 1));
        return p;
    }

    @Test
    void teamInsertFindCorrectDelete() throws SQLException {
        Team t = team("Giants", "San Francisco");
        assertNotEquals(0, t.getId());

        Team found = db.teams().findById(t.getId()).orElseThrow();
        assertEquals("Giants", found.getName());
        assertEquals("San Francisco", found.getCity());

        t.setCity("Oakland");
        db.teams().correct(t);
        assertEquals("Oakland", db.teams().findById(t.getId()).orElseThrow().getCity());

        assertTrue(db.teams().delete(t.getId()));
        assertTrue(db.teams().findById(t.getId()).isEmpty());
        assertFalse(db.teams().delete(t.getId()));
    }

    @Test
    void fullGameRoundTrip() throws SQLException {
        Team home = team("Giants", "San Francisco");
        Team away = team("Dodgers", "Los Angeles");
        Player batter = player("Mays", 24, home);
        Player pitcher = player("Koufax", 32, away);
        assertEquals(1, db.players().findByTeam(home.getId()).size());

        Game g = new Game();
        g.setHomeTeamId(home.getId());
        g.setAwayTeamId(away.getId());
        g.setDate(LocalDate.of(2026, 10, 24));
        g.setGameNumber(1);
        db.games().insert(g);
        Game foundGame = db.games().findById(g.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 24), foundGame.getDate());
        assertEquals(1, db.games().findByTeam(away.getId()).size());

        Atbat a = new Atbat();
        a.setGameId(g.getId());
        a.setTeamId(home.getId());
        a.setBatterId(batter.getId());
        a.setPitcherId(pitcher.getId());
        a.setPitches(5);
        a.setBalls(2);
        a.setStrikes(2);
        a.setInning(3);
        a.setBattingOrder(4);
        a.setRbis(1);
        a.setRow(3);
        a.setColumn(2);
        a.setActive(true);
        db.atbats().insert(a);
        Atbat foundAtbat = db.atbats().findByGame(g.getId()).get(0);
        assertEquals(home.getId(), foundAtbat.getTeamId());
        assertEquals(pitcher.getId(), foundAtbat.getPitcherId());
        assertEquals(4, foundAtbat.getBattingOrder());
        assertEquals(1, foundAtbat.getRbis());
        assertTrue(foundAtbat.isActive());
        assertEquals(3, foundAtbat.getRow());
        assertEquals(2, foundAtbat.getColumn());

        Abbrev k = new Abbrev();
        k.setAbbrev("K");
        k.setDescription("Strikeout");
        db.abbrevs().insert(k);
        assertEquals(k.getId(), db.abbrevs().findByAbbrev("K").orElseThrow().getId());

        Play p = new Play();
        p.setAtbatId(a.getId());
        p.setAbbrevId(k.getId());
        p.setOut(1);
        p.setField(6);
        p.setAdvancedBy(7);
        p.setComment("looking");
        db.plays().insert(p);
        Play foundPlay = db.plays().findByAtbat(a.getId()).get(0);
        assertEquals(k.getId(), foundPlay.getAbbrevId());
        assertEquals(1, foundPlay.getOut());
        assertEquals(6, foundPlay.getField());
        assertEquals(7, foundPlay.getAdvancedBy());
        assertEquals("looking", foundPlay.getComment());
    }

    @Test
    void unsetForeignKeyIsStoredAsNull() throws SQLException {
        Play p = new Play();
        db.plays().insert(p);
        Play found = db.plays().findById(p.getId()).orElseThrow();
        assertEquals(0, found.getAtbatId());
        assertEquals(0, found.getAbbrevId());
    }

    @Test
    void foreignKeysAreEnforced() throws SQLException {
        Player p = new Player();
        p.setTeamId(999);
        assertThrows(SQLException.class, () -> db.players().insert(p, LocalDate.of(2026, 1, 1)));

        Team t = team("Giants", "San Francisco");
        player("Mays", 24, t);
        assertThrows(SQLException.class, () -> db.teams().delete(t.getId()));
    }

    @Test
    void failedTransactionRollsBack() throws SQLException {
        assertThrows(SQLException.class, () -> db.inTransaction(() -> {
            team("Giants", "San Francisco");
            Player p = new Player();
            p.setTeamId(999);
            db.players().insert(p, LocalDate.of(2026, 1, 1));
        }));
        assertTrue(db.teams().findAll().isEmpty());
    }

    @Test
    void versionThreeDatabaseGetsRowAndColumn(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v3.db").toString();
        long atbatId;
        try (Database first = Database.open(path)) {
            Atbat a = new Atbat();
            a.setInning(5);
            first.atbats().insert(a);
            atbatId = a.getId();
        }
        // Make it look like a file written before row and column existed.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("ALTER TABLE atbat DROP COLUMN card_row");
            st.execute("ALTER TABLE atbat DROP COLUMN card_column");
            st.execute("PRAGMA user_version = 3");
        }
        try (Database upgraded = Database.open(path)) {
            Atbat a = upgraded.atbats().findById(atbatId).orElseThrow();
            assertEquals(5, a.getInning());
            assertEquals(0, a.getRow());
            assertEquals(0, a.getColumn());
            a.setRow(4);
            a.setColumn(7);
            upgraded.atbats().update(a);
            Atbat stored = upgraded.atbats().findById(atbatId).orElseThrow();
            assertEquals(4, stored.getRow());
            assertEquals(7, stored.getColumn());
        }
    }

    @Test
    void versionFourDatabaseGetsTeam(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v4.db").toString();
        // An at-bat table as version 4 wrote it, before the batting team existed.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE atbat (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        game_id       INTEGER,
                        batter_id     INTEGER,
                        pitcher_id    INTEGER,
                        pitches       INTEGER NOT NULL DEFAULT 0,
                        balls         INTEGER NOT NULL DEFAULT 0,
                        strikes       INTEGER NOT NULL DEFAULT 0,
                        inning        INTEGER NOT NULL DEFAULT 0,
                        batting_order INTEGER NOT NULL DEFAULT 0,
                        rbis          INTEGER NOT NULL DEFAULT 0,
                        active        INTEGER NOT NULL DEFAULT 0,
                        card_row      INTEGER NOT NULL DEFAULT 0,
                        card_column   INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("INSERT INTO atbat (inning) VALUES (6)");
            st.execute("PRAGMA user_version = 4");
        }
        try (Database upgraded = Database.open(path)) {
            Atbat a = upgraded.atbats().findAll().get(0);
            assertEquals(6, a.getInning());
            assertEquals(0, a.getTeamId());
            Team t = new Team();
            t.setName("Giants");
            upgraded.teams().insert(t);
            a.setTeamId(t.getId());
            upgraded.atbats().update(a);
            assertEquals(t.getId(), upgraded.atbats().findById(a.getId()).orElseThrow().getTeamId());
        }
    }

    @Test
    void versionFiveDatabaseGetsField(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v5.db").toString();
        long playId;
        try (Database first = Database.open(path)) {
            Play p = new Play();
            p.setOut(2);
            first.plays().insert(p);
            playId = p.getId();
        }
        // Make it look like a file written before the field existed.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("ALTER TABLE play DROP COLUMN field");
            st.execute("PRAGMA user_version = 5");
        }
        try (Database upgraded = Database.open(path)) {
            Play p = upgraded.plays().findById(playId).orElseThrow();
            assertEquals(2, p.getOut());
            assertEquals(0, p.getField());
            p.setField(8);
            upgraded.plays().update(p);
            assertEquals(8, upgraded.plays().findById(playId).orElseThrow().getField());
        }
    }

    @Test
    void versionSixDatabaseGetsAdvancedBy(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v6.db").toString();
        long playId;
        try (Database first = Database.open(path)) {
            Play p = new Play();
            p.setField(4);
            first.plays().insert(p);
            playId = p.getId();
        }
        // Make it look like a file written before advanced_by existed.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("ALTER TABLE play DROP COLUMN advanced_by");
            st.execute("PRAGMA user_version = 6");
        }
        try (Database upgraded = Database.open(path)) {
            Play p = upgraded.plays().findById(playId).orElseThrow();
            assertEquals(4, p.getField());
            assertEquals(0, p.getAdvancedBy());
            p.setAdvancedBy(3);
            upgraded.plays().update(p);
            assertEquals(3, upgraded.plays().findById(playId).orElseThrow().getAdvancedBy());
        }
    }

    @Test
    void saveInsertsThenUpdates() throws SQLException {
        Abbrev a = new Abbrev();
        a.setAbbrev("BB");
        db.abbrevs().save(a);
        long id = a.getId();
        a.setDescription("Walk");
        db.abbrevs().save(a);
        assertEquals(id, a.getId());
        assertEquals(1, db.abbrevs().findAll().size());
        assertEquals("Walk", db.abbrevs().findById(id).orElseThrow().getDescription());
    }
}
