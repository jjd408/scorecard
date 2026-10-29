package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Database;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A small form for entering a scoring abbreviation and its description. */
public class AbbrevEditDialog extends JDialog {

    private final Database db;
    private final Abbrev abbrev;
    private final JTextField abbrevField = new JTextField(8);
    private final JTextField descriptionField = new JTextField(30);
    private boolean saved;

    /**
     * Shows the form filled in from {@code abbrev}. If the user clicks OK,
     * copies the entered values into {@code abbrev} and returns true.
     */
    public static boolean edit(Window owner, String title, Abbrev abbrev, Database db) {
        AbbrevEditDialog dialog = new AbbrevEditDialog(owner, title, abbrev, db);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private AbbrevEditDialog(Window owner, String title, Abbrev abbrev, Database db) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.abbrev = abbrev;
        abbrevField.setText(abbrev.getAbbrev());
        descriptionField.setText(abbrev.getDescription());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        form.add(new JLabel("Abbrev:"), c);
        form.add(abbrevField, c);
        c.gridy = 1;
        form.add(new JLabel("Description:"), c);
        form.add(descriptionField, c);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> ok());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        buttons.add(Help.button(Help.Topic.ABBREVS));
        Help.install(this, Help.Topic.ABBREVS);

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

    private void ok() {
        String text = abbrevField.getText().trim();
        String problem = null;
        if (text.isEmpty()) {
            problem = "Please enter the abbreviation.";
        } else {
            try {
                Optional<Abbrev> existing = db.abbrevs().findByAbbrev(text);
                if (existing.isPresent() && existing.get().getId() != abbrev.getId()) {
                    problem = "\"" + text + "\" is already entered.";
                }
            } catch (SQLException e) {
                Errors.show(this, "Could not check for a duplicate abbreviation.", e);
                return;
            }
        }
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, getTitle(), JOptionPane.INFORMATION_MESSAGE);
            abbrevField.requestFocusInWindow();
            return;
        }
        abbrev.setAbbrev(text);
        abbrev.setDescription(descriptionField.getText().trim());
        saved = true;
        dispose();
    }
}
