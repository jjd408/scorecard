package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads and writes {@link Play}s. */
public class PlayDao extends Dao<Play> {

    PlayDao(Connection conn) {
        super(conn, "play", "atbat_id", "pitches", "balls", "strikes", "abbrev_id",
                "base", "out", "comment", "field", "advanced_by");
    }

    public List<Play> findByAtbat(long atbatId) throws SQLException {
        return findWhere("atbat_id = ?", atbatId);
    }

    @Override
    protected void bind(PreparedStatement ps, Play p) throws SQLException {
        setForeignKey(ps, 1, p.getAtbatId());
        ps.setInt(2, p.getPitches());
        ps.setInt(3, p.getBalls());
        ps.setInt(4, p.getStrikes());
        setForeignKey(ps, 5, p.getAbbrevId());
        ps.setInt(6, p.getBase());
        ps.setInt(7, p.getOut());
        ps.setString(8, p.getComment());
        ps.setInt(9, p.getField());
        ps.setInt(10, p.getAdvancedBy());
    }

    @Override
    protected Play read(ResultSet rs) throws SQLException {
        Play p = new Play();
        p.setId(rs.getLong("id"));
        p.setAtbatId(rs.getLong("atbat_id"));
        p.setPitches(rs.getInt("pitches"));
        p.setBalls(rs.getInt("balls"));
        p.setStrikes(rs.getInt("strikes"));
        p.setAbbrevId(rs.getLong("abbrev_id"));
        p.setBase(rs.getInt("base"));
        p.setOut(rs.getInt("out"));
        p.setComment(rs.getString("comment"));
        p.setField(rs.getInt("field"));
        p.setAdvancedBy(rs.getInt("advanced_by"));
        return p;
    }

    @Override
    protected long getId(Play p) {
        return p.getId();
    }

    @Override
    protected void setId(Play p, long id) {
        p.setId(id);
    }
}
