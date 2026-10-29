package com.joe.scorecard.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The standard scoring abbreviations, as listed in {@code abbrevs.csv}. */
public final class StandardAbbrevs {

    private StandardAbbrevs() {
    }

    /**
     * Adds every standard abbreviation that isn't already in the database.
     * An abbreviation counts as already there if one with exactly the same
     * text exists. Returns the number added.
     */
    public static int addMissing(Database db) throws SQLException {
        Set<String> existing = new HashSet<>();
        for (Abbrev a : db.abbrevs().findAll()) {
            existing.add(a.getAbbrev());
        }
        int[] added = new int[1];
        db.inTransaction(() -> {
            for (Abbrev a : load()) {
                if (existing.add(a.getAbbrev())) {
                    db.abbrevs().insert(a);
                    added[0]++;
                }
            }
        });
        return added[0];
    }

    /** Reads the file. */
    static List<Abbrev> load() {
        List<Abbrev> abbrevs = new ArrayList<>();
        for (String line : Resources.lines("abbrevs.csv")) {
            String[] f = line.split(",", 2);
            Abbrev a = new Abbrev();
            a.setAbbrev(f[0].strip());
            a.setDescription(f.length > 1 ? f[1].strip() : "");
            abbrevs.add(a);
        }
        return abbrevs;
    }
}
