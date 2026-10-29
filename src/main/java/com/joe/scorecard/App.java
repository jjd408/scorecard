package com.joe.scorecard;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.gui.ScFrame;
import java.awt.EventQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JOptionPane;

/** Entry point for the scorecard application. */
public class App {

    /** Where the scorecard data is kept: ~/.scorecard/scorecard.db */
    static final Path DB_FILE = Path.of(System.getProperty("user.home"), ".scorecard", "scorecard.db");

    public static void main(String[] args) {
        ScFrame.useNimbus();
        Database db;
        try {
            Files.createDirectories(DB_FILE.getParent());
            db = Database.open(DB_FILE.toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open " + DB_FILE + "\n\n" + e.getMessage(),
                    greeting(), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        EventQueue.invokeLater(() -> new ScFrame(greeting(), db).setVisible(true));
    }

    static String greeting() {
        return "Scorecard";
    }
}
