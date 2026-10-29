package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Player;
import com.joe.scorecard.data.Team;
import java.awt.BorderLayout;
import java.awt.Window;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Lists the current players, with buttons to add, remove and edit them.
 * Players are never erased: Delete removes them as of a date, so they still
 * appear in games played before then.
 */
public class PlayersDialog extends ListDialog<Player> {

    private List<Team> teams = List.of();
    private final Map<Long, String> teamNames = new HashMap<>();

    public PlayersDialog(Window owner, Database db) {
        super(owner, "Players", Help.Topic.PLAYERS, db, "Name", "Number", "Team");
        reload();
    }

    @Override
    protected List<Player> load() throws SQLException {
        teams = db.teams().findAll();
        teamNames.clear();
        for (Team t : teams) {
            teamNames.put(t.getId(), t.getName());
        }
        return db.players().findCurrent();
    }

    @Override
    protected Object valueAt(Player p, int column) {
        return switch (column) {
            case 0 -> p.getName();
            case 1 -> p.getNumber();
            default -> teamNames.getOrDefault(p.getTeamId(), "");
        };
    }

    @Override
    protected long idOf(Player p) {
        return p.getId();
    }

    @Override
    protected void newItem() {
        Player player = new Player();
        LocalDate from = PlayerEditDialog.edit(this, "New Player", player, teams, true);
        if (from == null) {
            return;
        }
        try {
            db.players().insert(player, from);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the player.", e);
        }
        reload();
        select(player.getId());
    }

    @Override
    protected void editItem(Player player) {
        LocalDate from = PlayerEditDialog.edit(this, "Edit Player", player, teams, false);
        if (from == null) {
            return;
        }
        try {
            db.players().update(player, from);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Edit Player", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the player.", e);
        }
        reload();
    }

    @Override
    protected void deleteItem(Player player) {
        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel("Remove as of:"), BorderLayout.WEST);
        row.add(dateField, BorderLayout.CENTER);
        Object[] message = {
            "Remove \"" + player.getName() + "\"?",
            "They will still appear in games played before this date.",
            row,
        };
        int answer = JOptionPane.showConfirmDialog(this, message, "Delete Player",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        LocalDate on = Dates.parse(this, dateField, "Delete Player");
        if (on == null) {
            return;
        }
        try {
            db.players().remove(player.getId(), on);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Delete Player", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not remove the player.", e);
        }
        reload();
    }
}
