package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class AbbrevDaoTest {

    @Test
    void countPlaysAndLookup() throws SQLException {
        try (Database db = Database.openInMemory()) {
            Abbrev k = new Abbrev();
            k.setAbbrev("K");
            k.setDescription("Strikeout");
            db.abbrevs().insert(k);
            Abbrev bb = new Abbrev();
            bb.setAbbrev("BB");
            db.abbrevs().insert(bb);

            for (int i = 0; i < 2; i++) {
                Play p = new Play();
                p.setAbbrevId(k.getId());
                db.plays().insert(p);
            }

            assertEquals(2, db.abbrevs().countPlays(k.getId()));
            assertEquals(0, db.abbrevs().countPlays(bb.getId()));
            assertEquals(k.getId(), db.abbrevs().findByAbbrev("K").orElseThrow().getId());
            assertTrue(db.abbrevs().findByAbbrev("k").isEmpty(), "lookups are case-sensitive");
        }
    }
}
