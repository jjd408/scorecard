package com.joe.scorecard.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingDaoTest {

    @Test
    void setGetReplaceRemove() throws SQLException {
        try (Database db = Database.openInMemory()) {
            SettingDao settings = db.settings();
            assertTrue(settings.get(SettingDao.LAST_GAME_ID).isEmpty());

            settings.set(SettingDao.LAST_GAME_ID, "7");
            assertEquals(Optional.of("7"), settings.get(SettingDao.LAST_GAME_ID));

            settings.set(SettingDao.LAST_GAME_ID, "12");
            assertEquals(Optional.of("12"), settings.get(SettingDao.LAST_GAME_ID));

            settings.remove(SettingDao.LAST_GAME_ID);
            assertTrue(settings.get(SettingDao.LAST_GAME_ID).isEmpty());
        }
    }

    @Test
    void survivesReopening(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("s.db").toString();
        try (Database db = Database.open(path)) {
            db.settings().set(SettingDao.LAST_GAME_ID, "3");
        }
        try (Database db = Database.open(path)) {
            assertEquals(Optional.of("3"), db.settings().get(SettingDao.LAST_GAME_ID));
        }
    }

    @Test
    void versionTwoDatabaseGetsSettingTable(@TempDir Path dir) throws SQLException {
        String path = dir.resolve("v2.db").toString();
        try (Database db = Database.open(path)) {
            Team t = new Team();
            t.setName("Giants");
            db.teams().insert(t);
        }
        // Make it look like a file written before settings existed.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
                Statement st = c.createStatement()) {
            st.execute("DROP TABLE setting");
            st.execute("PRAGMA user_version = 2");
        }
        try (Database db = Database.open(path)) {
            db.settings().set(SettingDao.LAST_GAME_ID, "1");
            assertEquals(Optional.of("1"), db.settings().get(SettingDao.LAST_GAME_ID));
            assertEquals("Giants", db.teams().findAll().get(0).getName());
        }
    }
}
