package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.joe.scorecard.data.Atbat;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineupTest {

    private static Atbat atbat(int column, int row, int order, long batterId) {
        Atbat a = new Atbat();
        a.setColumn(column);
        a.setRow(row);
        a.setBattingOrder(order);
        a.setBatterId(batterId);
        a.setActive(true);
        return a;
    }

    @Test
    void usesTheNextBatterInEachSpotAfterTheAtbat() {
        Atbat first = atbat(0, 0, 1, 101);
        Atbat second = atbat(0, 1, 2, 102);
        // Spot 1 came up again next inning, with a pinch hitter.
        Atbat pinch = atbat(1, 0, 1, 111);
        Atbat later = atbat(2, 0, 1, 121);
        long[] lineup = ScFrame.lineup(List.of(first, second, pinch, later), second);
        assertEquals(111, lineup[1]);
        assertEquals(102, lineup[2]);
    }

    @Test
    void fallsBackToTheLastBatterBeforeIt() {
        Atbat a = atbat(0, 0, 1, 101);
        Atbat b = atbat(1, 0, 1, 111);
        Atbat runner = atbat(1, 1, 2, 102);
        long[] lineup = ScFrame.lineup(List.of(a, b, runner), runner);
        assertEquals(111, lineup[1]);
        assertEquals(102, lineup[2]);
        assertEquals(0, lineup[3]);
    }

    @Test
    void skipsAtbatsWithNoBatterPicked() {
        Atbat runner = atbat(0, 0, 1, 101);
        Atbat blank = atbat(0, 1, 2, 0);
        Atbat picked = atbat(1, 1, 2, 202);
        assertEquals(202, ScFrame.lineup(List.of(runner, blank, picked), runner)[2]);
    }
}
