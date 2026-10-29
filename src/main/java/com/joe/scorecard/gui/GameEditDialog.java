package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Game;
import com.joe.scorecard.data.Team;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A form for entering a game's date, teams and game number. The team lists
 * show each team's name and city as of the date entered. Once a game has
 * at-bats, only its date and game number can be changed.
 */
public class GameEditDialog extends JDialog {

    private final Database db;
    private final Game game;
    private final JTextField dateField = new JTextField(10);
    private final JComboBox<TeamChoice> awayField = new JComboBox<>();
    private final JComboBox<TeamChoice> homeField = new JComboBox<>();
    private final JSpinner numberField = new JSpinner(new SpinnerNumberModel(1, 1, 9, 1));
    private LocalDate teamsDate;
    private boolean saved;

    /**
     * Shows the form filled in from {@code game}. If the user clicks OK, copies
     * the entered values into {@code game} and returns true.
     */
    public static boolean edit(Window owner, String title, Game game, Database db) {
        GameEditDialog dialog = new GameEditDialog(owner, title, game, db);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private GameEditDialog(Window owner, String title, Game game, Database db) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.game = game;
        LocalDate date = game.getDate() == null ? LocalDate.now() : game.getDate();
        dateField.setText(date.toString());
        numberField.setValue(Math.max(1, game.getGameNumber()));
        boolean teamsLocked = hasAtbats(game);
        loadTeams(date, game.getAwayTeamId(), game.getHomeTeamId());

        // When a full date is typed, show the teams' names as of that date.
        dateField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                dateChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                dateChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                dateChanged();
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, "Date:", dateField);
        addRow(form, c, 1, "Away:", awayField);
        addRow(form, c, 2, "Home:", homeField);
        addRow(form, c, 3, "Game number:", numberField);
        c.gridy = 4;
        c.gridx = 1;
        JLabel hint = new JLabel("2 for the second game of a doubleheader");
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));
        form.add(hint, c);
        if (teamsLocked) {
            awayField.setEnabled(false);
            homeField.setEnabled(false);
            c.gridy = 5;
            form.add(new JLabel("The teams can't be changed once at-bats are recorded."), c);
        }

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> ok());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        buttons.add(Help.button(Help.Topic.GAMES));
        Help.install(this, Help.Topic.GAMES);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Returns true if the game is already saved and has at-bats, so its teams are fixed. */
    private boolean hasAtbats(Game game) {
        if (game.getId() == 0) {
            return false;
        }
        try {
            return db.games().countAtbats(game.getId()) > 0;
        } catch (SQLException e) {
            Errors.show(this, "Could not check the game's at-bats.", e);
            return true;
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridy = row;
        c.gridx = GridBagConstraints.RELATIVE;
        form.add(new JLabel(label), c);
        form.add(field, c);
    }

    private void dateChanged() {
        try {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            if (!date.equals(teamsDate)) {
                loadTeams(date, selectedId(awayField), selectedId(homeField));
                pack();
            }
        } catch (DateTimeParseException e) {
            // Still typing; keep the current lists.
        }
    }

    /** Fills both team lists with the teams as of {@code date}, keeping the chosen teams. */
    private void loadTeams(LocalDate date, long awayId, long homeId) {
        List<Team> teams;
        try {
            teams = db.teams().findAll(date);
        } catch (SQLException e) {
            Errors.show(this, "Could not load the teams.", e);
            return;
        }
        teams.sort((a, b) -> Names.team(a).compareToIgnoreCase(Names.team(b)));
        teamsDate = date;
        fill(awayField, teams, awayId);
        fill(homeField, teams, homeId);
    }

    private static void fill(JComboBox<TeamChoice> field, List<Team> teams, long selectedId) {
        field.removeAllItems();
        field.addItem(new TeamChoice(0, ""));
        for (Team t : teams) {
            TeamChoice choice = new TeamChoice(t.getId(), Names.team(t));
            field.addItem(choice);
            if (t.getId() == selectedId) {
                field.setSelectedItem(choice);
            }
        }
    }

    private static long selectedId(JComboBox<TeamChoice> field) {
        TeamChoice choice = (TeamChoice) field.getSelectedItem();
        return choice == null ? 0 : choice.id();
    }

    private void ok() {
        LocalDate date = Dates.parse(this, dateField, getTitle());
        if (date == null) {
            return;
        }
        long awayId = selectedId(awayField);
        long homeId = selectedId(homeField);
        int number = (Integer) numberField.getValue();
        String problem = null;
        if (awayId == 0 || homeId == 0) {
            problem = "Please choose both teams.";
        } else if (awayId == homeId) {
            problem = "The away and home teams must be different.";
        } else if (isDuplicate(date, awayId, homeId, number)) {
            problem = "That game is already entered. For a doubleheader, use game number 2.";
        }
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, getTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        game.setDate(date);
        game.setAwayTeamId(awayId);
        game.setHomeTeamId(homeId);
        game.setGameNumber(number);
        saved = true;
        dispose();
    }

    /** Returns true if another game has the same date, teams and game number. */
    private boolean isDuplicate(LocalDate date, long awayId, long homeId, int number) {
        try {
            for (Game other : db.games().findByDate(date)) {
                if (other.getId() != game.getId() && other.getGameNumber() == number
                        && other.getAwayTeamId() == awayId && other.getHomeTeamId() == homeId) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            Errors.show(this, "Could not check for a duplicate game.", e);
            return false;
        }
    }

    /** A team as shown in a team list. */
    private record TeamChoice(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
