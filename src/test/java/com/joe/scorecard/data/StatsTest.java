package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatsTest {

    private static final Map<Long, String> ABBREVS = Map.of(
            1L, "1B", 2L, "2B", 3L, "HR", 4L, "BB", 5L, "K", 6L, "SB", 7L, "SAC", 8L, "G", 9L, "FC");

    /** Returns plays made from {abbrevId, base, out} triples. */
    private static List<Play> plays(int[]... specs) {
        List<Play> plays = new ArrayList<>();
        for (int[] s : specs) {
            Play p = new Play();
            p.setAbbrevId(s[0]);
            p.setBase(s[1]);
            p.setOut(s[2]);
            plays.add(p);
        }
        return plays;
    }

    private static Atbat atbat(int pitches, int rbis) {
        Atbat a = new Atbat();
        a.setPitches(pitches);
        a.setRbis(rbis);
        return a;
    }

    @Test
    void singleStealAndScore() {
        List<Play> plays = plays(new int[] {1, 1, 0}, new int[] {6, 2, 0}, new int[] {0, 4, 0});
        Stats.Batting b = Stats.batting(atbat(4, 0), plays, ABBREVS);
        assertEquals(1, b.atBats);
        assertEquals(1, b.hits);
        assertEquals(0, b.doubles);
        assertEquals(1, b.stolenBases);
        assertEquals(1, b.runs);
        Stats.Pitching p = Stats.pitching(atbat(4, 0), plays, ABBREVS);
        assertEquals(1, p.hits);
        assertEquals(1, p.runs);
        assertEquals(0, p.outs);
        assertEquals(4, p.pitches);
    }

    @Test
    void errorsCountEveryPlayScoredE() {
        Map<Long, String> abbrevs = Map.of(1L, "1B", 10L, "e");
        // Reached on an error, then took second on another one.
        assertEquals(2, Stats.errors(plays(new int[] {10, 1, 0}, new int[] {10, 2, 0}), abbrevs));
        assertEquals(0, Stats.errors(plays(new int[] {1, 1, 0}), abbrevs));
        assertEquals(0, Stats.errors(List.of(), abbrevs));
    }

    @Test
    void homeRunCountsHitRunAndRbi() {
        Stats.Batting b = Stats.batting(atbat(1, 1), plays(new int[] {3, 4, 0}), ABBREVS);
        assertEquals(1, b.homeRuns);
        assertEquals(1, b.hits);
        assertEquals(1, b.runs);
        assertEquals(1, b.rbis);
    }

    @Test
    void walkAndSacrificeAreNotAtBats() {
        assertEquals(0, Stats.batting(atbat(5, 0), plays(new int[] {4, 1, 0}), ABBREVS).atBats);
        assertEquals(1, Stats.batting(atbat(5, 0), plays(new int[] {4, 1, 0}), ABBREVS).walks);
        assertEquals(0, Stats.batting(atbat(2, 1), plays(new int[] {7, 0, 1}), ABBREVS).atBats);
    }

    @Test
    void strikeoutAndForceOutAreOuts() {
        Stats.Pitching k = Stats.pitching(atbat(6, 0), plays(new int[] {5, 0, 1}), ABBREVS);
        assertEquals(1, k.strikeouts);
        assertEquals(1, k.outs);
        // Walked, then forced out at second: an out, and no run.
        Stats.Pitching fc = Stats.pitching(atbat(5, 0), plays(new int[] {4, 1, 0}, new int[] {9, 2, 2}), ABBREVS);
        assertEquals(1, fc.walks);
        assertEquals(1, fc.outs);
        assertEquals(0, fc.runs);
    }

    @Test
    void noPlaysIsAnAtBatWithNothingElse() {
        Stats.Batting b = Stats.batting(atbat(0, 0), List.of(), ABBREVS);
        assertEquals(1, b.plateAppearances);
        assertEquals(1, b.atBats);
        assertEquals(0, b.hits);
    }

    @Test
    void inningsShowThirds() {
        Stats.Pitching p = new Stats.Pitching();
        p.outs = 17;
        assertEquals("5.2", p.innings());
    }

    @Test
    void outAtSecondMeansSafeAtFirst() {
        assertEquals(1, Stats.basesReached(plays(new int[] {1, 2, 1})));
        assertEquals(0, Stats.basesReached(plays(new int[] {8, 1, 1})));
        assertEquals(4, Stats.basesReached(plays(new int[] {3, 4, 0})));
    }
}
