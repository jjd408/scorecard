package com.joe.scorecard.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batting and pitching stats worked out from at-bats and their plays. An
 * at-bat's first play is the batter's own result, read from its scoring
 * abbreviation; the rest move the batter around the bases as a runner.
 */
public final class Stats {

    private static final Set<String> HITS = Set.of("1B", "2B", "3B", "HR");
    private static final Set<String> WALKS = Set.of("BB", "IBB");
    private static final Set<String> STRIKEOUTS = Set.of("K", "KL");

    /** Results that are a plate appearance but not an at-bat. */
    private static final Set<String> NOT_AT_BATS = Set.of("BB", "IBB", "HBP", "SAC", "SF", "CI");

    private Stats() {
    }

    /** A batter's totals. */
    public static final class Batting {
        public int plateAppearances;
        public int atBats;
        public int runs;
        public int hits;
        public int doubles;
        public int triples;
        public int homeRuns;
        public int rbis;
        public int walks;
        public int strikeouts;
        public int stolenBases;

        public void add(Batting o) {
            plateAppearances += o.plateAppearances;
            atBats += o.atBats;
            runs += o.runs;
            hits += o.hits;
            doubles += o.doubles;
            triples += o.triples;
            homeRuns += o.homeRuns;
            rbis += o.rbis;
            walks += o.walks;
            strikeouts += o.strikeouts;
            stolenBases += o.stolenBases;
        }
    }

    /** A pitcher's totals. */
    public static final class Pitching {
        public int outs;
        public int battersFaced;
        public int hits;
        public int runs;
        public int walks;
        public int strikeouts;
        public int homeRuns;
        public int pitches;

        public void add(Pitching o) {
            outs += o.outs;
            battersFaced += o.battersFaced;
            hits += o.hits;
            runs += o.runs;
            walks += o.walks;
            strikeouts += o.strikeouts;
            homeRuns += o.homeRuns;
            pitches += o.pitches;
        }

        /** Returns innings pitched the usual way, with thirds after the point: 5.2 is 5 2/3. */
        public String innings() {
            return outs / 3 + "." + outs % 3;
        }
    }

    /** Returns the batter's stats for at-bat {@code a}, whose plays are {@code plays}, in order. */
    public static Batting batting(Atbat a, List<Play> plays, Map<Long, String> abbrevs) {
        Batting b = new Batting();
        b.plateAppearances = 1;
        String result = result(plays, abbrevs);
        b.atBats = NOT_AT_BATS.contains(result) ? 0 : 1;
        if (HITS.contains(result)) {
            b.hits = 1;
            b.doubles = result.equals("2B") ? 1 : 0;
            b.triples = result.equals("3B") ? 1 : 0;
            b.homeRuns = result.equals("HR") ? 1 : 0;
        }
        b.walks = WALKS.contains(result) ? 1 : 0;
        b.strikeouts = STRIKEOUTS.contains(result) ? 1 : 0;
        b.runs = basesReached(plays) == 4 ? 1 : 0;
        b.rbis = a.getRbis();
        for (Play p : plays) {
            if ("SB".equals(abbrev(p, abbrevs))) {
                b.stolenBases++;
            }
        }
        return b;
    }

    /**
     * Returns the stats charged to at-bat {@code a}'s pitcher: the batter
     * faced, the pitches thrown, the result, and the outs made and run scored
     * by the batter, even on later plays.
     */
    public static Pitching pitching(Atbat a, List<Play> plays, Map<Long, String> abbrevs) {
        Batting b = batting(a, plays, abbrevs);
        Pitching p = new Pitching();
        p.battersFaced = 1;
        p.hits = b.hits;
        p.homeRuns = b.homeRuns;
        p.walks = b.walks;
        p.strikeouts = b.strikeouts;
        p.runs = b.runs;
        p.pitches = a.getPitches();
        p.outs = outs(plays);
        return p;
    }

    /**
     * Returns how far {@code plays}, taken in order, got the runner safely:
     * 0 not at all, 1 to 3 that base, 4 home. Being out at a base means they
     * got safely to the one before it. Plays after an out don't count.
     */
    public static int basesReached(List<Play> plays) {
        int reached = 0;
        for (Play p : plays) {
            int base = Math.max(0, Math.min(4, p.getBase()));
            if (p.getOut() > 0) {
                return Math.max(reached, base - 1);
            }
            reached = Math.max(reached, base);
        }
        return reached;
    }

    /**
     * Returns the errors made by the fielding team in {@code plays}: each
     * play scored E, whether it put the batter on or moved them up later.
     */
    public static int errors(List<Play> plays, Map<Long, String> abbrevs) {
        int errors = 0;
        for (Play p : plays) {
            if ("E".equals(abbrev(p, abbrevs))) {
                errors++;
            }
        }
        return errors;
    }

    /** Returns 1 if the batter was put out in {@code plays}, otherwise 0. */
    private static int outs(List<Play> plays) {
        for (Play p : plays) {
            if (p.getOut() > 0) {
                return 1;
            }
        }
        return 0;
    }

    /** Returns the abbreviation of the batter's own result, the first play, or blank. */
    private static String result(List<Play> plays, Map<Long, String> abbrevs) {
        return plays.isEmpty() ? "" : abbrev(plays.get(0), abbrevs);
    }

    private static String abbrev(Play p, Map<Long, String> abbrevs) {
        return abbrevs.getOrDefault(p.getAbbrevId(), "").trim().toUpperCase();
    }
}
