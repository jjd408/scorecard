package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Reads and writes {@link Abbrev}s. */
public class AbbrevDao extends Dao<Abbrev> {

    AbbrevDao(Connection conn) {
        super(conn, "abbrev", "abbrev", "description");
    }

    /** Looks up an abbreviation by its text, such as "K". */
    public Optional<Abbrev> findByAbbrev(String abbrev) throws SQLException {
        List<Abbrev> found = findWhere("abbrev = ?", abbrev);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Returns the number of plays that use the abbreviation. */
    public int countPlays(long abbrevId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM play WHERE abbrev_id = ?")) {
            ps.setLong(1, abbrevId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1);
            }
        }
    }

    @Override
    protected void bind(PreparedStatement ps, Abbrev a) throws SQLException {
        ps.setString(1, a.getAbbrev());
        ps.setString(2, a.getDescription());
    }

    @Override
    protected Abbrev read(ResultSet rs) throws SQLException {
        Abbrev a = new Abbrev();
        a.setId(rs.getLong("id"));
        a.setAbbrev(rs.getString("abbrev"));
        a.setDescription(rs.getString("description"));
        return a;
    }

    @Override
    protected long getId(Abbrev a) {
        return a.getId();
    }

    @Override
    protected void setId(Abbrev a, long id) {
        a.setId(id);
    }
}
