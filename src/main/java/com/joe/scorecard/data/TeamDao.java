package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes {@link Team}s, keeping a history of each team's name and
 * city as {@link TeamEra}s, so a team that moves or is renamed still shows
 * its old name and city in earlier games.
 *
 * <p>Methods that take a date return each team with the name and city it had
 * on that date. Methods without one use the current name and city.
 */
public class TeamDao {

    private static final String SELECT = """
            SELECT t.id, e.name, e.city
            FROM team t JOIN team_era e ON e.team_id = t.id""";

    private final Connection conn;

    TeamDao(Connection conn) {
        this.conn = conn;
    }

    /** Saves a new team, with its name and city going back to the beginning, and sets its id. */
    public void insert(Team t) throws SQLException {
        insert(t, null);
    }

    /**
     * Saves a new team with its name and city from {@code from} onward (or
     * from the beginning if null), and sets its id.
     */
    public void insert(Team t, LocalDate from) throws SQLException {
        Dao.atomically(conn, () -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO team DEFAULT VALUES", Statement.RETURN_GENERATED_KEYS);
                try (ResultSet keys = st.getGeneratedKeys()) {
                    keys.next();
                    t.setId(keys.getLong(1));
                }
            }
            insertEra(t, from);
        });
    }

    /**
     * Changes the team's name and city from {@code from} onward. Games before
     * then keep the old ones.
     *
     * @throws IllegalArgumentException if {@code from} is before the current
     *     name and city started
     */
    public void update(Team t, LocalDate from) throws SQLException {
        Dao.atomically(conn, () -> {
            TeamEra current = currentEra(t.getId());
            if (Objects.equals(current.getName(), t.getName()) && Objects.equals(current.getCity(), t.getCity())) {
                return;
            }
            if (current.getStartDate() != null && from.isBefore(current.getStartDate())) {
                throw new IllegalArgumentException(
                        "The change can't take effect before " + current.getStartDate() + ".");
            }
            if (from.equals(current.getStartDate())) {
                setEra(current.getId(), t);
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE team_era SET end_date = ? WHERE id = ?")) {
                    ps.setString(1, Dao.dateText(from));
                    ps.setLong(2, current.getId());
                    ps.executeUpdate();
                }
                insertEra(t, from);
            }
        });
    }

    /**
     * Fixes a mistake in the team's current name and city. Unlike
     * {@link #update}, this changes them for every date the current ones
     * cover, including earlier games.
     */
    public void correct(Team t) throws SQLException {
        setEra(currentEra(t.getId()).getId(), t);
    }

    /**
     * Erases a team and its history. Returns false if there was no such team.
     *
     * @throws SQLException if any player or game refers to the team
     */
    public boolean delete(long id) throws SQLException {
        boolean[] deleted = new boolean[1];
        Dao.atomically(conn, () -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM team_era WHERE team_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM team WHERE id = ?")) {
                ps.setLong(1, id);
                deleted[0] = ps.executeUpdate() > 0;
            }
        });
        return deleted[0];
    }

    /** Returns the team with its current name and city. */
    public Optional<Team> findById(long id) throws SQLException {
        return findById(id, null);
    }

    /**
     * Returns the team with the name and city it had on {@code date}. Before
     * its first name and city, uses those.
     */
    public Optional<Team> findById(long id, LocalDate date) throws SQLException {
        TeamEra era = Period.pick(history(id), date);
        return era == null ? Optional.empty() : Optional.of(toTeam(era));
    }

    /** Returns all teams with their current names and cities. */
    public List<Team> findAll() throws SQLException {
        List<Team> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE e.end_date IS NULL ORDER BY t.id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Team t = new Team();
                t.setId(rs.getLong("id"));
                t.setName(rs.getString("name"));
                t.setCity(rs.getString("city"));
                result.add(t);
            }
        }
        return result;
    }

    /** Returns all teams with the names and cities they had on {@code date}. */
    public List<Team> findAll(LocalDate date) throws SQLException {
        Map<Long, List<TeamEra>> byTeam = new LinkedHashMap<>();
        for (TeamEra era : eras("1 = 1")) {
            byTeam.computeIfAbsent(era.getTeamId(), k -> new ArrayList<>()).add(era);
        }
        List<Team> result = new ArrayList<>();
        for (List<TeamEra> history : byTeam.values()) {
            result.add(toTeam(Period.pick(history, date)));
        }
        return result;
    }

    /** Returns every name and city the team has had, oldest first. */
    public List<TeamEra> history(long teamId) throws SQLException {
        return eras("team_id = ?", teamId);
    }

    /** Returns matching eras grouped by team, each team's oldest first. */
    private List<TeamEra> eras(String where, Object... params) throws SQLException {
        // A null start date sorts before any date.
        String sql = "SELECT id, team_id, name, city, start_date, end_date FROM team_era WHERE " + where
                + " ORDER BY team_id, start_date IS NOT NULL, start_date, id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<TeamEra> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TeamEra e = new TeamEra();
                    e.setId(rs.getLong("id"));
                    e.setTeamId(rs.getLong("team_id"));
                    e.setName(rs.getString("name"));
                    e.setCity(rs.getString("city"));
                    e.setStartDate(Dao.parseDate(rs.getString("start_date")));
                    e.setEndDate(Dao.parseDate(rs.getString("end_date")));
                    result.add(e);
                }
            }
            return result;
        }
    }

    private TeamEra currentEra(long teamId) throws SQLException {
        TeamEra current = Period.pick(history(teamId), null);
        if (current == null) {
            throw new IllegalArgumentException("There is no team with id " + teamId + ".");
        }
        return current;
    }

    private void insertEra(Team t, LocalDate start) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO team_era (team_id, name, city, start_date) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, t.getId());
            ps.setString(2, t.getName());
            ps.setString(3, t.getCity());
            ps.setString(4, Dao.dateText(start));
            ps.executeUpdate();
        }
    }

    private void setEra(long eraId, Team t) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE team_era SET name = ?, city = ? WHERE id = ?")) {
            ps.setString(1, t.getName());
            ps.setString(2, t.getCity());
            ps.setLong(3, eraId);
            ps.executeUpdate();
        }
    }

    private static Team toTeam(TeamEra era) {
        Team t = new Team();
        t.setId(era.getTeamId());
        t.setName(era.getName());
        t.setCity(era.getCity());
        return t;
    }
}
