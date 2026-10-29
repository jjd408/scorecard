package com.joe.scorecard.gui;

import java.awt.Component;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/** Reads dates typed by the user as YYYY-MM-DD. */
final class Dates {

    private Dates() {
    }

    /**
     * Returns the date in {@code field}, or tells the user it isn't valid,
     * focuses the field and returns null.
     */
    static LocalDate parse(Component parent, JTextField field, String title) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(parent,
                    "\"" + field.getText().trim() + "\" isn't a date. Please use YYYY-MM-DD, such as "
                            + LocalDate.now() + ".",
                    title, JOptionPane.INFORMATION_MESSAGE);
            field.requestFocusInWindow();
            return null;
        }
    }
}
