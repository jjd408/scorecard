package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes {@link Atbat}s. */
public class AtbatDao extends Dao<Atbat> {

    AtbatDao(Connection conn) {
        super(conn, "atbat", "game_id", "batter_id", "pitcher_id", "pitches", "balls",
                "strikes", "inning", "batting_order", "rbis", "active", "card_row", "card_column",
                "team_id");
    }

    public List<Atbat> findByGame(long gameId) throws SQLException {
        return findWhere("game_id = ?", gameId);
    }

    public List<Atbat> findByBatter(long playerId) throws SQLException {
        return findWhere("batter_id = ?", playerId);
    }

    public List<Atbat> findByPitcher(long playerId) throws SQLException {
        return findWhere("pitcher_id = ?", playerId);
    }

    @Override
    protected void bind(PreparedStatement ps, Atbat a) throws SQLException {
        setForeignKey(ps, 1, a.getGameId());
        setForeignKey(ps, 2, a.getBatterId());
        setForeignKey(ps, 3, a.getPitcherId());
        ps.setInt(4, a.getPitches());
        ps.setInt(5, a.getBalls());
        ps.setInt(6, a.getStrikes());
        ps.setInt(7, a.getInning());
        ps.setInt(8, a.getBattingOrder());
        ps.setInt(9, a.getRbis());
        ps.setBoolean(10, a.isActive());
        ps.setInt(11, a.getRow());
        ps.setInt(12, a.getColumn());
        setForeignKey(ps, 13, a.getTeamId());
    }

    @Override
    protected Atbat read(ResultSet rs) throws SQLException {
        Atbat a = new Atbat();
        a.setId(rs.getLong("id"));
        a.setGameId(rs.getLong("game_id"));
        a.setBatterId(rs.getLong("batter_id"));
        a.setPitcherId(rs.getLong("pitcher_id"));
        a.setPitches(rs.getInt("pitches"));
        a.setBalls(rs.getInt("balls"));
        a.setStrikes(rs.getInt("strikes"));
        a.setInning(rs.getInt("inning"));
        a.setBattingOrder(rs.getInt("batting_order"));
        a.setRbis(rs.getInt("rbis"));
        a.setActive(rs.getBoolean("active"));
        a.setRow(rs.getInt("card_row"));
        a.setColumn(rs.getInt("card_column"));
        a.setTeamId(rs.getLong("team_id"));
        return a;
    }

    @Override
    protected long getId(Atbat a) {
        return a.getId();
    }

    @Override
    protected void setId(Atbat a, long id) {
        a.setId(id);
    }
}
