package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MlbTeamsTest {

    private Database db;

    @BeforeEach
    void open() throws SQLException {
        db = Database.openInMemory();
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private Team byName(String name) throws SQLException {
        return db.teams().findAll().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void fileListsThirtyTeamsWithHistoryInOrder() {
        Map<String, List<MlbTeams.Era>> teams = MlbTeams.load();
        assertEquals(30, teams.size());
        for (List<MlbTeams.Era> history : teams.values()) {
            assertNull(history.get(0).from(), "a team's first name should go back to the beginning");
            for (int i = 1; i < history.size(); i++) {
                LocalDate prev = history.get(i - 1).from();
                assertTrue(prev == null || prev.isBefore(history.get(i).from()));
            }
        }
    }

    @Test
    void addsAllTeamsToANewDatabase() throws SQLException {
        assertEquals(30, MlbTeams.addMissing(db));
        assertEquals(30, db.teams().findAll().size());
        assertEquals("San Francisco", byName("Giants").getCity());
    }

    @Test
    void addsHistory() throws SQLException {
        MlbTeams.addMissing(db);
        Team as = byName("Athletics");
        assertEquals("Sacramento", as.getCity());
        assertEquals("Oakland", db.teams().findById(as.getId(), LocalDate.of(1989, 10, 28)).orElseThrow().getCity());

        Team guardians = byName("Guardians");
        assertEquals("Indians", db.teams().findById(guardians.getId(), LocalDate.of(2016, 11, 2)).orElseThrow().getName());
    }

    @Test
    void runningTwiceAddsNothingMore() throws SQLException {
        MlbTeams.addMissing(db);
        assertEquals(0, MlbTeams.addMissing(db));
        assertEquals(30, db.teams().findAll().size());
    }

    @Test
    void skipsTeamsAlreadyEntered() throws SQLException {
        Team giants = new Team();
        giants.setName("giants");
        giants.setCity("SF");
        db.teams().insert(giants);

        assertEquals(29, MlbTeams.addMissing(db));
        assertEquals(30, db.teams().findAll().size());
        assertEquals("SF", byName("giants").getCity());
    }
}
