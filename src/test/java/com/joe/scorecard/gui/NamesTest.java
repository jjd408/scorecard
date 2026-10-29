package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void teamWithAndWithoutCity() {
        assertEquals("San Francisco Giants", Names.team("San Francisco", "Giants"));
        assertEquals("Athletics", Names.team(null, "Athletics"));
        assertEquals("Athletics", Names.team(" ", "Athletics"));
    }

    @Test
    void playAddsTheFielderHitTo() {
        assertEquals("F 8", Names.play("F", 8));
        assertEquals("1B", Names.play("1B", 0));
        assertEquals("6", Names.play(null, 6));
        assertEquals("", Names.play(null, 0));
        assertEquals("E", Names.play("E", 10));
    }

    @Test
    void gameAddsNumberOnlyForDoubleheaders() {
        LocalDate date = LocalDate.of(2024, 8, 17);
        assertEquals("Oakland Athletics at San Francisco Giants, 2024-08-17",
                Names.game("Oakland Athletics", "San Francisco Giants", date, 1));
        assertEquals("Oakland Athletics at San Francisco Giants, 2024-08-17, game 2",
                Names.game("Oakland Athletics", "San Francisco Giants", date, 2));
    }
}
