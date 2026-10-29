package com.joe.scorecard.gui;

import com.joe.scorecard.data.Team;
import java.time.LocalDate;

/** Formats team and game names for display. */
final class Names {

    private Names() {
    }

    /** Returns "City Name", such as "San Francisco Giants", or just the name if there's no city. */
    static String team(String city, String name) {
        if (name == null) {
            name = "";
        }
        return city == null || city.isBlank() ? name : city + " " + name;
    }

    static String team(Team t) {
        return team(t.getCity(), t.getName());
    }

    /**
     * Returns a play as scored: its abbreviation followed by the number of
     * the fielder it was hit to, such as "F 8" or "E 6". Either part may be
     * missing; a field outside 1 to 9 is none.
     */
    static String play(String abbrev, int field) {
        String s = abbrev == null ? "" : abbrev;
        if (field < 1 || field > 9) {
            return s;
        }
        return s.isEmpty() ? String.valueOf(field) : s + " " + field;
    }

    /**
     * Describes a game, such as "Oakland Athletics at San Francisco Giants,
     * 2024-08-17", adding ", game 2" for the second game of a doubleheader.
     */
    static String game(String away, String home, LocalDate date, int gameNumber) {
        String s = away + " at " + home + ", " + date;
        return gameNumber > 1 ? s + ", game " + gameNumber : s;
    }
}
