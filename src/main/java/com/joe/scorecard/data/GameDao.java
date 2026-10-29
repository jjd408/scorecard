package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Reads and writes {@link Game}s. Dates are stored as ISO text, such as 2026-10-24. */
public class GameDao extends Dao<Game> {

    GameDao(Connection conn) {
        super(conn, "game", "home_team_id", "away_team_id", "date", "game_number");
    }

    /** Returns the games where the team played at home or away. */
    public List<Game> findByTeam(long teamId) throws SQLException {
        return findWhere("home_team_id = ? OR away_team_id = ?", teamId, teamId);
    }

    /** Returns the games played on {@code date}. */
    public List<Game> findByDate(LocalDate date) throws SQLException {
        return findWhere("date = ?", dateText(date));
    }

    /** Returns the number of at-bats recorded for the game. */
    public int countAtbats(long gameId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM atbat WHERE game_id = ?")) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1);
            }
        }
    }

    /**
     * Saves changes to a game.
     *
     * @throws IllegalArgumentException if the game has at-bats and its teams
     *     were changed
     */
    @Override
    public boolean update(Game g) throws SQLException {
        Optional<Game> stored = findById(g.getId());
        if (stored.isPresent() && countAtbats(g.getId()) > 0
                && (stored.get().getHomeTeamId() != g.getHomeTeamId()
                        || stored.get().getAwayTeamId() != g.getAwayTeamId())) {
            throw new IllegalArgumentException("The teams can't be changed once at-bats are recorded.");
        }
        return super.update(g);
    }

    /** Deletes the game along with its at-bats and their plays. */
    @Override
    public boolean delete(long id) throws SQLException {
        boolean[] deleted = new boolean[1];
        atomically(conn, () -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM play WHERE atbat_id IN (SELECT id FROM atbat WHERE game_id = ?)")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM atbat WHERE game_id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            deleted[0] = super.delete(id);
        });
        return deleted[0];
    }

    @Override
    protected void bind(PreparedStatement ps, Game g) throws SQLException {
        setForeignKey(ps, 1, g.getHomeTeamId());
        setForeignKey(ps, 2, g.getAwayTeamId());
        ps.setString(3, dateText(g.getDate()));
        ps.setInt(4, g.getGameNumber());
    }

    @Override
    protected Game read(ResultSet rs) throws SQLException {
        Game g = new Game();
        g.setId(rs.getLong("id"));
        g.setHomeTeamId(rs.getLong("home_team_id"));
        g.setAwayTeamId(rs.getLong("away_team_id"));
        g.setDate(parseDate(rs.getString("date")));
        g.setGameNumber(rs.getInt("game_number"));
        return g;
    }

    @Override
    protected long getId(Game g) {
        return g.getId();
    }

    @Override
    protected void setId(Game g, long id) {
        g.setId(id);
    }
}
