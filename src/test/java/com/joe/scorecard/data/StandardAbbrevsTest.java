package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardAbbrevsTest {

    private Database db;

    @BeforeEach
    void open() throws SQLException {
        db = Database.openInMemory();
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    @Test
    void fileHasNoDuplicatesOrBlankDescriptions() {
        List<Abbrev> abbrevs = StandardAbbrevs.load();
        Set<String> seen = new HashSet<>();
        for (Abbrev a : abbrevs) {
            assertFalse(a.getAbbrev().isEmpty());
            assertFalse(a.getDescription().isEmpty(), a.getAbbrev());
            assertEquals(true, seen.add(a.getAbbrev()), "duplicate " + a.getAbbrev());
        }
    }

    @Test
    void addsAllThenNothingMore() throws SQLException {
        int count = StandardAbbrevs.load().size();
        assertEquals(count, StandardAbbrevs.addMissing(db));
        assertEquals(0, StandardAbbrevs.addMissing(db));
        assertEquals(count, db.abbrevs().findAll().size());
        assertEquals("Fielder's choice", db.abbrevs().findByAbbrev("FC").orElseThrow().getDescription());
    }

    @Test
    void keepsAbbrevsAlreadyEntered() throws SQLException {
        Abbrev k = new Abbrev();
        k.setAbbrev("K");
        k.setDescription("Strikeout (mine)");
        db.abbrevs().insert(k);

        int count = StandardAbbrevs.load().size();
        assertEquals(count - 1, StandardAbbrevs.addMissing(db));
        assertEquals("Strikeout (mine)", db.abbrevs().findByAbbrev("K").orElseThrow().getDescription());
    }
}
