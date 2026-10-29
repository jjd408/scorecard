package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamDaoTest {

    private static final LocalDate SACRAMENTO = LocalDate.of(2025, 3, 1);
    private static final LocalDate LAS_VEGAS = LocalDate.of(2028, 3, 1);

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

    private String cityOn(Team t, LocalDate date) throws SQLException {
        return db.teams().findById(t.getId(), date).orElseThrow().getCity();
    }

    /** The A's: Oakland, then Sacramento from 2025, then Las Vegas from 2028. */
    private Team athletics() throws SQLException {
        Team as = team("Athletics", "Oakland");
        as.setCity("Sacramento");
        db.teams().update(as, SACRAMENTO);
        as.setCity("Las Vegas");
        db.teams().update(as, LAS_VEGAS);
        return as;
    }

    @Test
    void relocationKeepsOldCityForEarlierGames() throws SQLException {
        Team as = athletics();

        assertEquals("Oakland", cityOn(as, LocalDate.of(1989, 10, 28)));
        assertEquals("Oakland", cityOn(as, SACRAMENTO.minusDays(1)));
        assertEquals("Sacramento", cityOn(as, SACRAMENTO));
        assertEquals("Sacramento", cityOn(as, LAS_VEGAS.minusDays(1)));
        assertEquals("Las Vegas", cityOn(as, LAS_VEGAS));
        assertEquals("Las Vegas", db.teams().findById(as.getId()).orElseThrow().getCity());
        assertEquals(3, db.teams().history(as.getId()).size());
    }

    @Test
    void sameTeamIdThroughEveryMove() throws SQLException {
        Team as = athletics();
        assertEquals(List.of(as.getId()), db.teams().findAll().stream().map(Team::getId).toList());
        assertEquals("Las Vegas", db.teams().findAll().get(0).getCity());
    }

    @Test
    void findAllAsOfDate() throws SQLException {
        Team as = athletics();
        Team giants = team("Giants", "San Francisco");

        List<Team> in2026 = db.teams().findAll(LocalDate.of(2026, 7, 4));
        assertEquals(2, in2026.size());
        assertEquals(as.getId(), in2026.get(0).getId());
        assertEquals("Sacramento", in2026.get(0).getCity());
        assertEquals(giants.getId(), in2026.get(1).getId());
        assertEquals("San Francisco", in2026.get(1).getCity());
    }

    @Test
    void correctionFixesCurrentNameForAllItsDates() throws SQLException {
        Team as = athletics();
        as.setCity("Las Vegsa");
        db.teams().correct(as);
        as.setCity("Las Vegas");
        db.teams().correct(as);

        assertEquals("Las Vegas", cityOn(as, LAS_VEGAS));
        assertEquals("Sacramento", cityOn(as, SACRAMENTO));
        assertEquals(3, db.teams().history(as.getId()).size());
    }

    @Test
    void correctionOnTeamWithNoHistoryAppliesToEveryDate() throws SQLException {
        Team t = team("Gaints", "San Francisco");
        t.setName("Giants");
        db.teams().correct(t);
        assertEquals("Giants", db.teams().findById(t.getId(), LocalDate.of(1958, 4, 15)).orElseThrow().getName());
    }

    @Test
    void renameKeepsCity() throws SQLException {
        Team t = team("Devil Rays", "Tampa Bay");
        t.setName("Rays");
        db.teams().update(t, LocalDate.of(2008, 1, 1));
        assertEquals("Devil Rays", db.teams().findById(t.getId(), LocalDate.of(2007, 6, 1)).orElseThrow().getName());
        assertEquals("Rays", db.teams().findById(t.getId()).orElseThrow().getName());
        assertEquals("Tampa Bay", db.teams().findById(t.getId(), LocalDate.of(2007, 6, 1)).orElseThrow().getCity());
    }

    @Test
    void unchangedUpdateAddsNoHistory() throws SQLException {
        Team t = team("Giants", "San Francisco");
        db.teams().update(t, LocalDate.of(2026, 1, 1));
        assertEquals(1, db.teams().history(t.getId()).size());
    }

    @Test
    void changeOnStartDateCorrectsInPlace() throws SQLException {
        Team as = athletics();
        as.setCity("Vegas");
        db.teams().update(as, LAS_VEGAS);
        assertEquals(3, db.teams().history(as.getId()).size());
        assertEquals("Vegas", cityOn(as, LAS_VEGAS));
    }

    @Test
    void changeBeforeCurrentEraIsRejected() throws SQLException {
        Team as = athletics();
        as.setCity("Somewhere");
        assertThrows(IllegalArgumentException.class, () -> db.teams().update(as, LocalDate.of(2027, 1, 1)));
        assertEquals("Las Vegas", db.teams().findById(as.getId()).orElseThrow().getCity());
    }

    @Test
    void deleteErasesHistory() throws SQLException {
        Team as = athletics();
        assertTrue(db.teams().delete(as.getId()));
        assertTrue(db.teams().findById(as.getId()).isEmpty());
        assertTrue(db.teams().history(as.getId()).isEmpty());
        assertFalse(db.teams().delete(as.getId()));
    }

    @Test
    void deleteOfTeamInUseLeavesHistoryAlone() throws SQLException {
        Team as = athletics();
        Player p = new Player();
        p.setName("Henderson");
        p.setTeamId(as.getId());
        db.players().insert(p, LocalDate.of(1979, 6, 24));

        assertThrows(SQLException.class, () -> db.teams().delete(as.getId()));
        assertEquals(3, db.teams().history(as.getId()).size());
    }

    @Test
    void upgradesVersionOneDatabase(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v1.db").toString();
        // A version 1 file: player history already split out, team still flat.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE team (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, city TEXT)");
            st.execute("CREATE TABLE player (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, removed TEXT)");
            st.execute("""
                    CREATE TABLE player_stint (id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_id INTEGER NOT NULL REFERENCES player(id),
                        team_id INTEGER REFERENCES team(id), number INTEGER NOT NULL DEFAULT 0,
                        start_date TEXT, end_date TEXT)""");
            st.execute("INSERT INTO team (id, name, city) VALUES (4, 'Athletics', 'Oakland')");
            st.execute("INSERT INTO player (id, name) VALUES (1, 'Henderson')");
            st.execute("INSERT INTO player_stint (player_id, team_id, number) VALUES (1, 4, 24)");
            st.execute("PRAGMA user_version = 1");
        }

        try (Database upgraded = Database.open(path)) {
            Team as = upgraded.teams().findById(4).orElseThrow();
            assertEquals("Athletics", as.getName());
            assertEquals("Oakland", as.getCity());
            assertEquals(4, upgraded.players().findById(1).orElseThrow().getTeamId());
            // Foreign keys still point at the team table after the rebuild.
            assertThrows(SQLException.class, () -> upgraded.teams().delete(4));
        }
    }
}
