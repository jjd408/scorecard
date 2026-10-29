package com.joe.scorecard.gui;

import com.joe.scorecard.data.Team;
import com.joe.scorecard.data.TeamEra;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * A form for entering a team's name and city. When editing, it also asks
 * whether the edit corrects a mistake or is a real change, such as a move to
 * a new city, that takes effect on a date.
 */
public class TeamEditDialog extends JDialog {

    /**
     * What the user chose when editing: a correction to the current name and
     * city, or a change that takes effect on {@code effective}.
     */
    public record Result(boolean correction, LocalDate effective) {
    }

    private final JTextField nameField = new JTextField(20);
    private final JTextField cityField = new JTextField(20);
    private final JRadioButton correctionButton = new JRadioButton("Correct a mistake (changes every date)");
    private final JRadioButton changeButton = new JRadioButton("Change effective:");
    private final JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
    private final boolean isNew;
    private Result result;

    /**
     * Shows the form filled in from {@code team}. If the user clicks OK, copies
     * the entered values into {@code team} and returns what they chose;
     * returns null if they cancel. {@code history} is the team's past names
     * and cities, or empty for a new team.
     */
    public static Result edit(Window owner, String title, Team team, List<TeamEra> history) {
        TeamEditDialog dialog = new TeamEditDialog(owner, title, team, history);
        dialog.setVisible(true);
        if (dialog.result != null) {
            team.setName(dialog.nameField.getText().trim());
            team.setCity(dialog.cityField.getText().trim());
        }
        return dialog.result;
    }

    private TeamEditDialog(Window owner, String title, Team team, List<TeamEra> history) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        isNew = team.getId() == 0;
        nameField.setText(team.getName());
        cityField.setText(team.getCity());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, new JLabel("Name:"), nameField);
        addRow(form, c, 1, new JLabel("City:"), cityField);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        form.setAlignmentX(LEFT_ALIGNMENT);
        center.add(form);
        if (!isNew) {
            ButtonGroup group = new ButtonGroup();
            group.add(correctionButton);
            group.add(changeButton);
            correctionButton.setSelected(true);
            dateField.setEnabled(false);
            changeButton.addItemListener(e -> dateField.setEnabled(changeButton.isSelected()));

            JPanel changeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            changeRow.add(changeButton);
            changeRow.add(dateField);
            correctionButton.setAlignmentX(LEFT_ALIGNMENT);
            changeRow.setAlignmentX(LEFT_ALIGNMENT);
            center.add(Box.createVerticalStrut(6));
            center.add(correctionButton);
            center.add(changeRow);
            if (history.size() > 1) {
                JTextArea historyArea = new JTextArea(historyText(history));
                historyArea.setEditable(false);
                historyArea.setOpaque(false);
                historyArea.setFont(nameField.getFont());
                historyArea.setBorder(BorderFactory.createTitledBorder("History"));
                historyArea.setAlignmentX(LEFT_ALIGNMENT);
                center.add(Box.createVerticalStrut(6));
                center.add(historyArea);
            }
        }

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> ok());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        buttons.add(Help.button(Help.Topic.TEAMS));
        Help.install(this, Help.Topic.TEAMS);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, Component label, Component field) {
        c.gridy = row;
        form.add(label, c);
        form.add(field, c);
    }

    /** Lists past names and cities, one per line, such as "Athletics, Oakland until 2025-03-01". */
    private static String historyText(List<TeamEra> history) {
        StringBuilder sb = new StringBuilder();
        for (TeamEra era : history) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(era.getName());
            if (era.getCity() != null && !era.getCity().isEmpty()) {
                sb.append(", ").append(era.getCity());
            }
            if (era.getStartDate() != null) {
                sb.append(" from ").append(era.getStartDate());
            }
            if (era.getEndDate() != null) {
                sb.append(" until ").append(era.getEndDate());
            }
        }
        return sb.toString();
    }

    private void ok() {
        if (nameField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter a team name.", getTitle(),
                    JOptionPane.INFORMATION_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        if (isNew || correctionButton.isSelected()) {
            result = new Result(true, null);
        } else {
            LocalDate date = Dates.parse(this, dateField, getTitle());
            if (date == null) {
                return;
            }
            result = new Result(false, date);
        }
        dispose();
    }
}
