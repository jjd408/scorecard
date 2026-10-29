package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.joe.scorecard.data.Atbat;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutofillTest {

    private static Atbat atbat(int order, long batterId, long pitcherId) {
        Atbat a = new Atbat();
        a.setBattingOrder(order);
        a.setBatterId(batterId);
        a.setPitcherId(pitcherId);
        a.setActive(true);
        return a;
    }

    @Test
    void fillsBatterFromSameSpotAndPitcherFromLastAtbat() {
        List<Atbat> before = List.of(atbat(1, 101, 900), atbat(2, 102, 900), atbat(3, 103, 901));
        Atbat a = atbat(2, 0, 0);
        ScFrame.autofill(before, a);
        assertEquals(102, a.getBatterId());
        assertEquals(901, a.getPitcherId());
    }

    @Test
    void usesTheLatestBatterInTheSpot() {
        // A pinch hitter took over the 1 spot in the second inning.
        List<Atbat> before = List.of(atbat(1, 101, 900), atbat(1, 111, 900));
        Atbat a = atbat(1, 0, 0);
        ScFrame.autofill(before, a);
        assertEquals(111, a.getBatterId());
    }

    @Test
    void skipsAtbatsWithNoPitcherPicked() {
        List<Atbat> before = List.of(atbat(1, 101, 900), atbat(2, 102, 0));
        Atbat a = atbat(3, 0, 0);
        ScFrame.autofill(before, a);
        assertEquals(0, a.getBatterId());
        assertEquals(900, a.getPitcherId());
    }

    @Test
    void keepsPlayersAlreadyPicked() {
        List<Atbat> before = List.of(atbat(1, 101, 900));
        Atbat a = atbat(1, 555, 777);
        ScFrame.autofill(before, a);
        assertEquals(555, a.getBatterId());
        assertEquals(777, a.getPitcherId());
    }

    @Test
    void leavesBlankWithNothingBefore() {
        Atbat a = atbat(1, 0, 0);
        ScFrame.autofill(List.of(), a);
        assertEquals(0, a.getBatterId());
        assertEquals(0, a.getPitcherId());
    }
}
