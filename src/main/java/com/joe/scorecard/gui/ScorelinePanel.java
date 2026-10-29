package com.joe.scorecard.gui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The game's line score, cut down to the totals: each team's runs, hits and
 * errors, the away team above the home team as in a box score.
 */
class ScorelinePanel extends JPanel {

    /** A team's totals. Its errors are the ones it made in the field. */
    record Line(int runs, int hits, int errors) {
    }

    private static final String[] HEADINGS = {"R", "H", "E"};

    /** The team names, away then home. */
    private final JLabel[] names = new JLabel[2];

    /** The runs, hits and errors, away then home. */
    private final JLabel[][] numbers = new JLabel[2][HEADINGS.length];

    ScorelinePanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 6, 0, 6);
        c.anchor = GridBagConstraints.EAST;
        for (int col = 0; col < HEADINGS.length; col++) {
            c.gridx = col + 1;
            c.gridy = 0;
            add(bold(new JLabel(HEADINGS[col], SwingConstants.RIGHT)), c);
        }
        for (int row = 0; row < 2; row++) {
            c.gridy = row + 1;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            names[row] = bold(new JLabel());
            add(names[row], c);
            c.anchor = GridBagConstraints.EAST;
            for (int col = 0; col < HEADINGS.length; col++) {
                c.gridx = col + 1;
                numbers[row][col] = new JLabel("", SwingConstants.RIGHT);
                add(numbers[row][col], c);
            }
        }
        clear();
    }

    /** Shows the two teams' totals. */
    void show(String away, Line awayLine, String home, Line homeLine) {
        show(0, away, awayLine);
        show(1, home, homeLine);
    }

    /** Shows no game: placeholder team names, with dashes for the numbers. */
    void clear() {
        show(0, "Away", null);
        show(1, "Home", null);
    }

    /** Shows {@code line} in row {@code row}, or dashes if it's null. */
    private void show(int row, String team, Line line) {
        names[row].setText(team);
        int[] values = line == null ? null : new int[] {line.runs(), line.hits(), line.errors()};
        for (int col = 0; col < HEADINGS.length; col++) {
            numbers[row][col].setText(values == null ? "–" : String.valueOf(values[col]));
        }
    }

    private static JLabel bold(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }
}
