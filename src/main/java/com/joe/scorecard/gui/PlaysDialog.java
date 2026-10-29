package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Atbat;
import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Play;
import com.joe.scorecard.data.Stats;
import java.awt.Window;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;

/**
 * Lists an at-bat's plays in the order they happened, with buttons to add,
 * delete and edit them. The first is usually the batter's own result; the
 * rest move the batter around the bases as a runner, such as a stolen base
 * or being forced out at second.
 */
public class PlaysDialog extends ListDialog<Play> {

    private static final String[] BASES = {"", "1st", "2nd", "3rd", "Home"};

    private final Atbat atbat;
    private final String[] lineup;
    private List<Abbrev> abbrevs = List.of();
    private final Map<Long, Abbrev> abbrevsById = new HashMap<>();
    private List<Play> plays = List.of();

    /**
     * Makes the dialog for {@code atbat}'s plays. {@code lineup} names the
     * batter in each batting-order spot, as {@link PlayEditDialog#edit} takes it.
     */
    public PlaysDialog(Window owner, Database db, Atbat atbat, String[] lineup) {
        super(owner, "Plays", Help.Topic.PLAYS, db, "#", "Play", "Base", "Out", "By #", "Comment");
        this.atbat = atbat;
        this.lineup = lineup;
        setSizes(680, 300, 30, 190, 50, 40, 45, 240);
        reload();
    }

    @Override
    protected List<Play> load() throws SQLException {
        abbrevs = db.abbrevs().findAll();
        abbrevsById.clear();
        for (Abbrev a : abbrevs) {
            abbrevsById.put(a.getId(), a);
        }
        plays = db.plays().findByAtbat(atbat.getId());
        return plays;
    }

    @Override
    protected Object valueAt(Play p, int column) {
        return switch (column) {
            case 0 -> plays.indexOf(p) + 1;
            case 1 -> playText(p);
            case 2 -> BASES[Math.max(0, Math.min(4, p.getBase()))];
            case 3 -> p.getOut() == 0 ? "" : String.valueOf(p.getOut());
            case 4 -> p.getAdvancedBy() == 0 ? "" : String.valueOf(p.getAdvancedBy());
            default -> p.getComment() == null ? "" : p.getComment();
        };
    }

    /** Returns the play as scored, with the fielder it was hit to, and its abbreviation's description: "F 8 \u2013 Flyout". */
    private String playText(Play p) {
        Abbrev ab = abbrevsById.get(p.getAbbrevId());
        String text = Names.play(ab == null ? null : ab.getAbbrev(), p.getField());
        if (ab != null && ab.getDescription() != null && !ab.getDescription().isBlank()) {
            text += " \u2013 " + ab.getDescription();
        }
        return text;
    }

    @Override
    protected long idOf(Play p) {
        return p.getId();
    }

    /** Adds a play that moves the runner from the furthest base they've reached to the next. */
    @Override
    protected void newItem() {
        Play play = new Play();
        play.setAtbatId(atbat.getId());
        play.setBase(Math.min(4, Stats.basesReached(plays) + 1));
        if (!PlayEditDialog.edit(this, "New Play", play, abbrevs, lineup)) {
            return;
        }
        try {
            db.plays().insert(play);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the play.", e);
        }
        reload();
        select(play.getId());
    }

    @Override
    protected void editItem(Play play) {
        if (!PlayEditDialog.edit(this, "Edit Play", play, abbrevs, lineup)) {
            return;
        }
        try {
            db.plays().update(play);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the play.", e);
        }
        reload();
    }

    @Override
    protected void deleteItem(Play play) {
        int answer = JOptionPane.showConfirmDialog(this, "Delete play #" + (plays.indexOf(play) + 1) + "?",
                "Delete Play", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            db.plays().delete(play.getId());
        } catch (SQLException e) {
            Errors.show(this, "Could not delete the play.", e);
        }
        reload();
    }
}
