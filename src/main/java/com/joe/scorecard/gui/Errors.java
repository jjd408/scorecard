package com.joe.scorecard.gui;

import java.awt.Component;
import javax.swing.JOptionPane;

/** Shows error messages to the user. */
final class Errors {

    private Errors() {
    }

    static void show(Component parent, String message, Exception e) {
        JOptionPane.showMessageDialog(parent, message + "\n\n" + e.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
    }
}
