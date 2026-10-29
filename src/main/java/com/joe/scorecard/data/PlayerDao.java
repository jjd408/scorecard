package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes {@link Player}s, keeping a history of each player's team
 * and number as {@link PlayerStint}s. Players are never erased: removing one
 * ends their last stint, so earlier games still show them correctly.
 *
 * <p>Methods that take a date return each player with the team and number
 * they had on that date. Methods without one use the current team and number.
 */
public class PlayerDao {

    private static final String SELECT = """
            SELECT p.id, p.name, p.removed, s.team_id, s.number
            FROM player p JOIN player_stint s ON s.player_id = p.id""";

    private static final String COVERS = """
            (s.start_date IS NULL OR s.start_date <= ?)
            AND (s.end_date IS NULL OR s.end_date > ?)""";

    private final Connection conn;

    PlayerDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * Saves a new player, on their team and number from {@code from} onward,
     * and sets the player's id.
     */
    public void insert(Player p, LocalDate from) throws SQLException {
        Dao.atomically(conn, () -> doInsert(p, from));
    }

    private void doInsert(Player p, LocalDate from) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player (name, removed) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getName());
            ps.setString(2, Dao.dateText(p.getRemoved()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                p.setId(keys.getLong(1));
            }
        }
        insertStint(p.getId(), p.getTeamId(), p.getNumber(), from);
    }

    /**
     * Saves changes to a player. A new team or number takes effect on
     * {@code from}; games before then keep the old ones.
     *
     * @throws IllegalArgumentException if the team or number changed and
     *     {@code from} is before the current one started, or the player has
     *     been removed
     */
    public void update(Player p, LocalDate from) throws SQLException {
        Dao.atomically(conn, () -> doUpdate(p, from));
    }

    private void doUpdate(Player p, LocalDate from) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE player SET name = ? WHERE id = ?")) {
            ps.setString(1, p.getName());
            ps.setLong(2, p.getId());
            ps.executeUpdate();
        }

        PlayerStint current = currentStint(p.getId());
        if (current != null && current.getTeamId() == p.getTeamId() && current.getNumber() == p.getNumber()) {
            return;
        }
        if (current == null) {
            throw new IllegalArgumentException("A removed player's team and number can't be changed.");
        }
        if (current.getStartDate() != null && from.isBefore(current.getStartDate())) {
            throw new IllegalArgumentException(
                    "The change can't take effect before " + current.getStartDate() + ".");
        }
        if (from.equals(current.getStartDate())) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player_stint SET team_id = ?, number = ? WHERE id = ?")) {
                Dao.setForeignKey(ps, 1, p.getTeamId());
                ps.setInt(2, p.getNumber());
                ps.setLong(3, current.getId());
                ps.executeUpdate();
            }
        } else {
            endStint(current.getId(), from);
            insertStint(p.getId(), p.getTeamId(), p.getNumber(), from);
        }
    }

    /**
     * Removes a player as of {@code on}. They no longer appear in current
     * lists, but still appear for dates before {@code on}.
     *
     * @throws IllegalArgumentException if {@code on} is before the player's
     *     current team and number started, or they were already removed
     */
    public void remove(long playerId, LocalDate on) throws SQLException {
        Dao.atomically(conn, () -> doRemove(playerId, on));
    }

    private void doRemove(long playerId, LocalDate on) throws SQLException {
        PlayerStint current = currentStint(playerId);
        if (current == null) {
            throw new IllegalArgumentException("That player has already been removed.");
        }
        if (current.getStartDate() != null && on.isBefore(current.getStartDate())) {
            throw new IllegalArgumentException(
                    "The player can't be removed before " + current.getStartDate() + ".");
        }
        endStint(current.getId(), on);
        try (PreparedStatement ps = conn.prepareStatement("UPDATE player SET removed = ? WHERE id = ?")) {
            ps.setString(1, Dao.dateText(on));
            ps.setLong(2, playerId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the player with their current team and number, or their last
     * ones if they have been removed.
     */
    public Optional<Player> findById(long id) throws SQLException {
        return findById(id, null);
    }

    /**
     * Returns the player with the team and number they had on {@code date}.
     * If they weren't on a team that day, uses their nearest stint instead,
     * so a player always shows up in a game they played in.
     */
    public Optional<Player> findById(long id, LocalDate date) throws SQLException {
        Player p;
        try (PreparedStatement ps = conn.prepareStatement("SELECT name, removed FROM player WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                p = new Player();
                p.setId(id);
                p.setName(rs.getString("name"));
                p.setRemoved(Dao.parseDate(rs.getString("removed")));
            }
        }
        PlayerStint stint = Period.pick(history(id), date);
        if (stint != null) {
            p.setTeamId(stint.getTeamId());
            p.setNumber(stint.getNumber());
        }
        return Optional.of(p);
    }

    /** Returns all players who haven't been removed. */
    public List<Player> findCurrent() throws SQLException {
        return query(SELECT + " WHERE p.removed IS NULL AND s.end_date IS NULL ORDER BY p.id");
    }

    /** Returns the team's current players. */
    public List<Player> findByTeam(long teamId) throws SQLException {
        return query(SELECT + " WHERE s.end_date IS NULL AND s.team_id = ? ORDER BY p.id", teamId);
    }

    /** Returns the players who were on the team on {@code date}. */
    public List<Player> findByTeam(long teamId, LocalDate date) throws SQLException {
        String d = Dao.dateText(date);
        return query(SELECT + " WHERE " + COVERS + " AND s.team_id = ? ORDER BY p.id", d, d, teamId);
    }

    /** Returns every team and number the player has had, oldest first. */
    public List<PlayerStint> history(long playerId) throws SQLException {
        String sql = """
                SELECT id, player_id, team_id, number, start_date, end_date FROM player_stint
                WHERE player_id = ? ORDER BY start_date IS NOT NULL, start_date, id""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, playerId);
            List<PlayerStint> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerStint s = new PlayerStint();
                    s.setId(rs.getLong("id"));
                    s.setPlayerId(rs.getLong("player_id"));
                    s.setTeamId(rs.getLong("team_id"));
                    s.setNumber(rs.getInt("number"));
                    s.setStartDate(Dao.parseDate(rs.getString("start_date")));
                    s.setEndDate(Dao.parseDate(rs.getString("end_date")));
                    result.add(s);
                }
            }
            return result;
        }
    }

    /** Returns true if any player, current or past, has been on the team. */
    public boolean hasPlayedFor(long teamId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM player_stint WHERE team_id = ? LIMIT 1")) {
            ps.setLong(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private PlayerStint currentStint(long playerId) throws SQLException {
        for (PlayerStint s : history(playerId)) {
            if (s.getEndDate() == null) {
                return s;
            }
        }
        return null;
    }

    private void insertStint(long playerId, long teamId, int number, LocalDate start) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO player_stint (player_id, team_id, number, start_date) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, playerId);
            Dao.setForeignKey(ps, 2, teamId);
            ps.setInt(3, number);
            ps.setString(4, Dao.dateText(start));
            ps.executeUpdate();
        }
    }

    private void endStint(long stintId, LocalDate end) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE player_stint SET end_date = ? WHERE id = ?")) {
            ps.setString(1, Dao.dateText(end));
            ps.setLong(2, stintId);
            ps.executeUpdate();
        }
    }

    private List<Player> query(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Player> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Player p = new Player();
                    p.setId(rs.getLong("id"));
                    p.setName(rs.getString("name"));
                    p.setRemoved(Dao.parseDate(rs.getString("removed")));
                    p.setTeamId(rs.getLong("team_id"));
                    p.setNumber(rs.getInt("number"));
                    result.add(p);
                }
            }
            return result;
        }
    }
}
