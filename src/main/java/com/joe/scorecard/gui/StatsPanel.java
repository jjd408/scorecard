package com.joe.scorecard.gui;

import com.joe.scorecard.data.Stats;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Shows each team's batting and pitching stats for the open game, a tab per
 * team, with the batters beside the pitchers and a total for each.
 */
class StatsPanel extends JPanel {

    private static final String[] BATTING_COLUMNS = {"Batter", "PA", "AB", "R", "H", "2B", "3B", "HR", "RBI",
        "BB", "SO", "SB"};
    private static final String[] PITCHING_COLUMNS = {"Pitcher", "IP", "BF", "H", "R", "BB", "SO", "HR", "NP"};

    /** What each heading means, shown when the mouse rests on it. */
    private static final String[] BATTING_HINTS = {"Batter", "Plate appearances",
        "At-bats (not counting walks, hit by pitch, sacrifices and catcher's interference)", "Runs scored", "Hits", "Doubles", "Triples", "Home runs", "Runs batted in", "Walks (bases on balls)",
        "Strikeouts", "Stolen bases"};
    private static final String[] PITCHING_HINTS = {"Pitcher", "Innings pitched (.1 and .2 are thirds of an inning)",
        "Batters faced", "Hits allowed", "Runs allowed", "Walks (bases on balls)", "Strikeouts",
        "Home runs allowed", "Number of pitches"};

    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultTableModel[] batting = new DefaultTableModel[2];
    private final DefaultTableModel[] pitching = new DefaultTableModel[2];

    /** Makes the panel with a tab for each of {@code teams}. */
    StatsPanel(String... teams) {
        super(new BorderLayout());
        for (int i = 0; i < teams.length; i++) {
            batting[i] = model(BATTING_COLUMNS);
            pitching[i] = model(PITCHING_COLUMNS);
            JPanel tab = new JPanel(new GridLayout(1, 2, 8, 0));
            tab.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            tab.add(section("Batting", batting[i], BATTING_HINTS));
            tab.add(section("Pitching", pitching[i], PITCHING_HINTS));
            tabs.addTab(teams[i], tab);
        }
        add(tabs, BorderLayout.CENTER);
    }

    JTabbedPane tabs() {
        return tabs;
    }

    /**
     * Shows team {@code index}'s stats: its batters' and its pitchers' totals,
     * by name, in the order given.
     */
    void show(int index, String team, List<Map.Entry<String, Stats.Batting>> batters,
            List<Map.Entry<String, Stats.Pitching>> pitchers) {
        tabs.setTitleAt(index, team);
        DefaultTableModel bm = batting[index];
        bm.setRowCount(0);
        Stats.Batting battingTotal = new Stats.Batting();
        for (Map.Entry<String, Stats.Batting> e : batters) {
            bm.addRow(row(e.getKey(), e.getValue()));
            battingTotal.add(e.getValue());
        }
        if (!batters.isEmpty()) {
            bm.addRow(row("Total", battingTotal));
        }
        DefaultTableModel pm = pitching[index];
        pm.setRowCount(0);
        Stats.Pitching pitchingTotal = new Stats.Pitching();
        for (Map.Entry<String, Stats.Pitching> e : pitchers) {
            pm.addRow(row(e.getKey(), e.getValue()));
            pitchingTotal.add(e.getValue());
        }
        if (!pitchers.isEmpty()) {
            pm.addRow(row("Total", pitchingTotal));
        }
    }

    private static Object[] row(String name, Stats.Batting b) {
        return new Object[] {name, b.plateAppearances, b.atBats, b.runs, b.hits, b.doubles, b.triples,
            b.homeRuns, b.rbis, b.walks, b.strikeouts, b.stolenBases};
    }

    private static Object[] row(String name, Stats.Pitching p) {
        return new Object[] {name, p.innings(), p.battersFaced, p.hits, p.runs, p.walks, p.strikeouts,
            p.homeRuns, p.pitches};
    }

    private static DefaultTableModel model(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * Returns a titled table, with the name column wide and the numbers
     * narrow, and each heading's hint shown when the mouse rests on it.
     */
    private static JPanel section(String title, DefaultTableModel model, String[] hints) {
        JTable table = new JTable(model) {
            @Override
            protected JTableHeader createDefaultTableHeader() {
                return new JTableHeader(columnModel) {
                    @Override
                    public String getToolTipText(MouseEvent e) {
                        int column = columnAtPoint(e.getPoint());
                        return column < 0 ? null : hints[convertColumnIndexToModel(column)];
                    }
                };
            }
        };
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        for (int c = 1; c < model.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(32);
        }
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
}
