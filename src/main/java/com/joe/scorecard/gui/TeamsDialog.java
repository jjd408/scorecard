package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.MlbTeams;
import com.joe.scorecard.data.Team;
import java.awt.Window;
import java.sql.SQLException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;

/**
 * Lists all teams with their current names and cities, with buttons to add,
 * delete and edit them. A team that moves or is renamed keeps its old name
 * and city for earlier games.
 */
public class TeamsDialog extends ListDialog<Team> {

    public TeamsDialog(Window owner, Database db) {
        super(owner, "Teams", Help.Topic.TEAMS, db, "Name", "City");
        JButton mlbButton = new JButton("Add MLB Teams");
        mlbButton.addActionListener(e -> addMlbTeams());
        addButton(mlbButton, false);
        reload();
    }

    /** Adds the MLB teams that aren't in the list yet. */
    private void addMlbTeams() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Add all 30 MLB teams? Teams already in the list are skipped.", "Add MLB Teams",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int added = MlbTeams.addMissing(db);
            String message = switch (added) {
                case 0 -> "All MLB teams are already in the list.";
                case 1 -> "Added 1 team.";
                default -> "Added " + added + " teams.";
            };
            JOptionPane.showMessageDialog(this, message, "Add MLB Teams", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not add the MLB teams.", e);
        }
        reload();
    }

    @Override
    protected List<Team> load() throws SQLException {
        return db.teams().findAll();
    }

    @Override
    protected Object valueAt(Team t, int column) {
        return column == 0 ? t.getName() : t.getCity();
    }

    @Override
    protected long idOf(Team t) {
        return t.getId();
    }

    @Override
    protected void newItem() {
        Team team = new Team();
        if (TeamEditDialog.edit(this, "New Team", team, List.of()) == null) {
            return;
        }
        try {
            db.teams().insert(team);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the team.", e);
        }
        reload();
        select(team.getId());
    }

    @Override
    protected void editItem(Team team) {
        TeamEditDialog.Result result;
        try {
            result = TeamEditDialog.edit(this, "Edit Team", team, db.teams().history(team.getId()));
        } catch (SQLException e) {
            Errors.show(this, "Could not load the team's history.", e);
            return;
        }
        if (result == null) {
            return;
        }
        try {
            if (result.correction()) {
                db.teams().correct(team);
            } else {
                db.teams().update(team, result.effective());
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Edit Team", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the team.", e);
        }
        reload();
    }

    @Override
    protected void deleteItem(Team team) {
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete the team \"" + team.getName() + "\"?", "Delete Team",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (db.players().hasPlayedFor(team.getId()) || !db.games().findByTeam(team.getId()).isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "\"" + team.getName() + "\" has players or games, so it can't be deleted.",
                        "Delete Team", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            db.teams().delete(team.getId());
        } catch (SQLException e) {
            Errors.show(this, "Could not delete the team.", e);
        }
        reload();
    }
}
