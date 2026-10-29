package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.StandardAbbrevs;
import java.awt.Window;
import java.sql.SQLException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;

/** Lists the scoring abbreviations, with buttons to add, delete and edit them. */
public class AbbrevsDialog extends ListDialog<Abbrev> {

    public AbbrevsDialog(Window owner, Database db) {
        super(owner, "Abbrevs", Help.Topic.ABBREVS, db, "Abbrev", "Description");
        JButton standardButton = new JButton("Add Standard Abbrevs");
        standardButton.addActionListener(e -> addStandardAbbrevs());
        addButton(standardButton, false);
        setSizes(600, 400, 90, 330);
        reload();
    }

    /** Adds the standard abbreviations that aren't in the list yet. */
    private void addStandardAbbrevs() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Add the standard scoring abbreviations? Ones already in the list are skipped.",
                "Add Standard Abbrevs", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int added = StandardAbbrevs.addMissing(db);
            String message = switch (added) {
                case 0 -> "All the standard abbreviations are already in the list.";
                case 1 -> "Added 1 abbreviation.";
                default -> "Added " + added + " abbreviations.";
            };
            JOptionPane.showMessageDialog(this, message, "Add Standard Abbrevs", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not add the standard abbreviations.", e);
        }
        reload();
    }

    @Override
    protected List<Abbrev> load() throws SQLException {
        return db.abbrevs().findAll();
    }

    @Override
    protected Object valueAt(Abbrev a, int column) {
        return column == 0 ? a.getAbbrev() : a.getDescription();
    }

    @Override
    protected long idOf(Abbrev a) {
        return a.getId();
    }

    @Override
    protected void newItem() {
        Abbrev abbrev = new Abbrev();
        if (AbbrevEditDialog.edit(this, "New Abbrev", abbrev, db)) {
            save(abbrev);
        }
    }

    @Override
    protected void editItem(Abbrev abbrev) {
        if (AbbrevEditDialog.edit(this, "Edit Abbrev", abbrev, db)) {
            save(abbrev);
        }
    }

    private void save(Abbrev abbrev) {
        try {
            db.abbrevs().save(abbrev);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the abbreviation.", e);
        }
        reload();
        select(abbrev.getId());
    }

    @Override
    protected void deleteItem(Abbrev abbrev) {
        try {
            int plays = db.abbrevs().countPlays(abbrev.getId());
            if (plays > 0) {
                JOptionPane.showMessageDialog(this,
                        "\"" + abbrev.getAbbrev() + "\" is used by " + plays + (plays == 1 ? " play" : " plays")
                                + ", so it can't be deleted.",
                        "Delete Abbrev", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } catch (SQLException e) {
            Errors.show(this, "Could not check where the abbreviation is used.", e);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete the abbreviation \"" + abbrev.getAbbrev() + "\"?", "Delete Abbrev",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            db.abbrevs().delete(abbrev.getId());
        } catch (SQLException e) {
            Errors.show(this, "Could not delete the abbreviation.", e);
        }
        reload();
    }
}
