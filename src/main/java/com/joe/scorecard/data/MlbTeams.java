package com.joe.scorecard.data;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Major League Baseball teams, as listed in {@code mlb-teams.csv}. */
public final class MlbTeams {

    private MlbTeams() {
    }

    /** One line of the file: a name and city, and when they took effect. */
    record Era(String name, String city, LocalDate from) {
    }

    /**
     * Adds every MLB team that isn't already in the database, with its history
     * of names and cities. A team counts as already there if a current team
     * has its current name. Returns the number of teams added.
     */
    public static int addMissing(Database db) throws SQLException {
        Set<String> existing = new HashSet<>();
        for (Team t : db.teams().findAll()) {
            if (t.getName() != null) {
                existing.add(t.getName().toLowerCase(Locale.ROOT));
            }
        }
        int[] added = new int[1];
        db.inTransaction(() -> {
            for (List<Era> history : load().values()) {
                Era current = history.get(history.size() - 1);
                if (existing.contains(current.name().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Team team = new Team();
                Era first = history.get(0);
                team.setName(first.name());
                team.setCity(first.city());
                db.teams().insert(team, first.from());
                for (Era era : history.subList(1, history.size())) {
                    team.setName(era.name());
                    team.setCity(era.city());
                    db.teams().update(team, era.from());
                }
                added[0]++;
            }
        });
        return added[0];
    }

    /** Reads the file into each team's history, oldest first, keyed by team. */
    static Map<String, List<Era>> load() {
        Map<String, List<Era>> teams = new LinkedHashMap<>();
        for (String line : Resources.lines("mlb-teams.csv")) {
            String[] f = line.split(",", -1);
            LocalDate from = f[3].isBlank() ? null : LocalDate.parse(f[3].strip());
            teams.computeIfAbsent(f[0].strip(), k -> new ArrayList<>())
                    .add(new Era(f[1].strip(), f[2].strip(), from));
        }
        return teams;
    }
}
