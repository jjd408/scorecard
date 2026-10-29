package com.joe.scorecard.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Reads and writes app settings stored in the database, such as the last open game. */
public class SettingDao {

    /** The id of the game that was open when the app last ran. */
    public static final String LAST_GAME_ID = "last_game_id";

    private final Connection conn;

    SettingDao(Connection conn) {
        this.conn = conn;
    }

    public Optional<String> get(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM setting WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void set(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO setting (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public void remove(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM setting WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }
}
