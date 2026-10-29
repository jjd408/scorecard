package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes one data object type in its table. Subclasses list the
 * table's columns (other than {@code id}) and map objects to and from rows.
 *
 * <p>A foreign key of 0 means "not set" and is stored as NULL.
 */
public abstract class Dao<T> {

    protected final Connection conn;
    private final String table;
    private final List<String> columns;

    protected Dao(Connection conn, String table, String... columns) {
        this.conn = conn;
        this.table = table;
        this.columns = List.of(columns);
    }

    /** Saves a new object and sets its id to the one the database assigned. */
    public void insert(T obj) throws SQLException {
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns)
                + ") VALUES (" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, obj);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                setId(obj, keys.getLong(1));
            }
        }
    }

    /** Saves changes to an existing object. Returns false if its id isn't in the table. */
    public boolean update(T obj) throws SQLException {
        String sql = "UPDATE " + table + " SET " + String.join(" = ?, ", columns) + " = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, obj);
            ps.setLong(columns.size() + 1, getId(obj));
            return ps.executeUpdate() > 0;
        }
    }

    /** Inserts the object if its id is 0, otherwise updates it. */
    public void save(T obj) throws SQLException {
        if (getId(obj) == 0) {
            insert(obj);
        } else {
            update(obj);
        }
    }

    /** Deletes the row with this id. Returns false if there was none. */
    public boolean delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<T> findById(long id) throws SQLException {
        List<T> found = findWhere("id = ?", id);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public List<T> findAll() throws SQLException {
        return findWhere("1 = 1");
    }

    /** Returns the rows matching an SQL {@code where} clause, ordered by id. */
    protected List<T> findWhere(String where, Object... params) throws SQLException {
        String sql = "SELECT id, " + String.join(", ", columns) + " FROM " + table
                + " WHERE " + where + " ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<T> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(read(rs));
                }
            }
            return result;
        }
    }

    /** Sets parameters 1..n of {@code ps} from the object, in column order. */
    protected abstract void bind(PreparedStatement ps, T obj) throws SQLException;

    /** Builds an object from the current row, reading columns by name. */
    protected abstract T read(ResultSet rs) throws SQLException;

    protected abstract long getId(T obj);

    protected abstract void setId(T obj, long id);

    protected static void setForeignKey(PreparedStatement ps, int index, long id) throws SQLException {
        if (id == 0) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, id);
        }
    }

    /** Runs {@code work} in a transaction, or in the caller's if one is already open. */
    static void atomically(Connection conn, Database.Work work) throws SQLException {
        if (!conn.getAutoCommit()) {
            work.run();
            return;
        }
        conn.setAutoCommit(false);
        try {
            work.run();
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /** Dates are stored as ISO text, such as 2026-10-24, which sorts by date. */
    static String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static LocalDate parseDate(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
