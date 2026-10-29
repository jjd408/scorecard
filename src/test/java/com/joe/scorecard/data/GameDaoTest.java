package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameDaoTest {

    private static final LocalDate JUL_4 = LocalDate.of(2026, 7, 4);

    private Database db;
    private Team home;
    private Team away;

    @BeforeEach
    void open() throws SQLException {
        db = Database.openInMemory();
        home = team("Giants");
        away = team("Dodgers");
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

    private Game game(LocalDate date, int number) throws SQLException {
        Game g = new Game();
        g.setHomeTeamId(home.getId());
        g.setAwayTeamId(away.getId());
        g.setDate(date);
        g.setGameNumber(number);
        db.games().insert(g);
        return g;
    }

    @Test
    void findByDate() throws SQLException {
        game(JUL_4, 1);
        game(JUL_4, 2);
        game(JUL_4.plusDays(1), 1);
        assertEquals(2, db.games().findByDate(JUL_4).size());
        assertTrue(db.games().findByDate(JUL_4.minusDays(1)).isEmpty());
    }

    @Test
    void deleteRemovesAtbatsAndPlays() throws SQLException {
        Game g = game(JUL_4, 1);
        Game other = game(JUL_4, 2);
        Atbat a = new Atbat();
        a.setGameId(g.getId());
        db.atbats().insert(a);
        Play p = new Play();
        p.setAtbatId(a.getId());
        db.plays().insert(p);
        Atbat keep = new Atbat();
        keep.setGameId(other.getId());
        db.atbats().insert(keep);

        assertEquals(1, db.games().countAtbats(g.getId()));
        assertTrue(db.games().delete(g.getId()));

        assertTrue(db.games().findById(g.getId()).isEmpty());
        assertTrue(db.atbats().findByGame(g.getId()).isEmpty());
        assertTrue(db.plays().findByAtbat(a.getId()).isEmpty());
        assertEquals(1, db.atbats().findByGame(other.getId()).size());
    }

    private void addAtbat(Game g) throws SQLException {
        Atbat a = new Atbat();
        a.setGameId(g.getId());
        db.atbats().insert(a);
    }

    @Test
    void teamsLockedOnceAtbatsRecorded() throws SQLException {
        Game g = game(JUL_4, 1);
        addAtbat(g);
        Team third = team("Padres");

        g.setHomeTeamId(third.getId());
        assertThrows(IllegalArgumentException.class, () -> db.games().update(g));
        assertEquals(home.getId(), db.games().findById(g.getId()).orElseThrow().getHomeTeamId());

        g.setHomeTeamId(home.getId());
        g.setAwayTeamId(third.getId());
        assertThrows(IllegalArgumentException.class, () -> db.games().update(g));
    }

    @Test
    void dateAndNumberEditableWithAtbats() throws SQLException {
        Game g = game(JUL_4, 1);
        addAtbat(g);
        g.setDate(JUL_4.plusDays(1));
        g.setGameNumber(2);
        assertTrue(db.games().update(g));
        Game stored = db.games().findById(g.getId()).orElseThrow();
        assertEquals(JUL_4.plusDays(1), stored.getDate());
        assertEquals(2, stored.getGameNumber());
    }

    @Test
    void teamsEditableWithoutAtbats() throws SQLException {
        Game g = game(JUL_4, 1);
        Team third = team("Padres");
        g.setHomeTeamId(third.getId());
        assertTrue(db.games().update(g));
        assertEquals(third.getId(), db.games().findById(g.getId()).orElseThrow().getHomeTeamId());
    }
}
