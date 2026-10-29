package com.joe.scorecard.gui;

import com.joe.scorecard.data.Player;
import com.joe.scorecard.data.Team;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
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

/**
 * A form for entering a player's name, number and team, plus the date the
 * player joins (for a new player) or a team or number change takes effect.
 */
public class PlayerEditDialog extends JDialog {

    private final JTextField nameField = new JTextField(20);
    private final JSpinner numberField = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
    private final JComboBox<TeamChoice> teamField = new JComboBox<>();
    private final JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
    private LocalDate savedDate;

    /**
     * Shows the form filled in from {@code player}. If the user clicks OK,
     * copies the entered values into {@code player} and returns the date they
     * take effect; returns null if the user cancels.
     */
    public static LocalDate edit(Window owner, String title, Player player, List<Team> teams, boolean isNew) {
        return edit(owner, title, player, teams, isNew, LocalDate.now());
    }

    /** Like {@link #edit(Window, String, Player, List, boolean)}, with the date starting at {@code date}. */
    public static LocalDate edit(Window owner, String title, Player player, List<Team> teams, boolean isNew,
            LocalDate date) {
        PlayerEditDialog dialog = new PlayerEditDialog(owner, title, player, teams, isNew);
        dialog.dateField.setText(date.toString());
        dialog.setVisible(true);
        if (dialog.savedDate != null) {
            player.setName(dialog.nameField.getText().trim());
            player.setNumber((Integer) dialog.numberField.getValue());
            player.setTeamId(((TeamChoice) dialog.teamField.getSelectedItem()).id());
        }
        return dialog.savedDate;
    }

    private PlayerEditDialog(Window owner, String title, Player player, List<Team> teams, boolean isNew) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        nameField.setText(player.getName());
        numberField.setValue(player.getNumber());
        teamField.addItem(new TeamChoice(0, "(none)"));
        for (Team t : teams) {
            TeamChoice choice = new TeamChoice(t.getId(), t.getName());
            teamField.addItem(choice);
            if (t.getId() == player.getTeamId()) {
                teamField.setSelectedItem(choice);
            }
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, "Name:", nameField);
        addRow(form, c, 1, "Number:", numberField);
        addRow(form, c, 2, "Team:", teamField);
        addRow(form, c, 3, isNew ? "Joined on:" : "Team/number change effective:", dateField);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> ok());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        buttons.add(Help.button(Help.Topic.PLAYERS));
        Help.install(this, Help.Topic.PLAYERS);

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

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label,
            Component field) {
        c.gridy = row;
        form.add(new JLabel(label), c);
        form.add(field, c);
    }

    private void ok() {
        if (nameField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter the player's name.", getTitle(),
                    JOptionPane.INFORMATION_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        LocalDate date = Dates.parse(this, dateField, getTitle());
        if (date == null) {
            return;
        }
        savedDate = date;
        dispose();
    }

    /** A team as shown in the team list. */
    private record TeamChoice(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
