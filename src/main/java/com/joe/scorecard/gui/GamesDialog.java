package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Game;
import com.joe.scorecard.data.Period;
import com.joe.scorecard.data.Team;
import com.joe.scorecard.data.TeamEra;
import java.awt.Window;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JOptionPane;

/**
 * Lists all games, newest first, with buttons to add, delete, edit and open
 * them. A newly entered game is opened right away. Teams are shown with the names and cities they had on the game date.
 */
public class GamesDialog extends ListDialog<Game> {

    /** Each team's name and city history, for showing teams as of a game's date. */
    private final Map<Long, List<TeamEra>> teamHistory = new HashMap<>();

    private final Consumer<Game> opener;

    /**
     * Creates the dialog. {@code opener} is called with a game to open in the
     * main window: a newly entered one, or one picked with the Open button.
     */
    public GamesDialog(Window owner, Database db, Consumer<Game> opener) {
        super(owner, "Games", Help.Topic.GAMES, db, "Date", "Game", "Away", "Home");
        this.opener = opener;
        JButton openButton = new JButton("Open");
        openButton.addActionListener(e -> open(selectedItem()));
        addButton(openButton, true);
        setSizes(720, 400, 90, 45, 200, 200);
        reload();
    }

    /** Opens the game in the main window and closes this dialog. */
    private void open(Game game) {
        if (game != null) {
            dispose();
            opener.accept(game);
        }
    }

    @Override
    protected List<Game> load() throws SQLException {
        teamHistory.clear();
        for (Team t : db.teams().findAll()) {
            teamHistory.put(t.getId(), db.teams().history(t.getId()));
        }
        List<Game> games = db.games().findAll();
        games.sort(Comparator.comparing(Game::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Game::getGameNumber));
        return games;
    }

    @Override
    protected Object valueAt(Game g, int column) {
        return switch (column) {
            case 0 -> g.getDate();
            case 1 -> g.getGameNumber();
            case 2 -> teamName(g.getAwayTeamId(), g.getDate());
            default -> teamName(g.getHomeTeamId(), g.getDate());
        };
    }

    private String teamName(long teamId, LocalDate date) {
        TeamEra era = Period.pick(teamHistory.getOrDefault(teamId, List.of()), date);
        return era == null ? "" : Names.team(era.getCity(), era.getName());
    }

    @Override
    protected long idOf(Game g) {
        return g.getId();
    }

    @Override
    protected void newItem() {
        Game game = new Game();
        game.setDate(LocalDate.now());
        game.setGameNumber(1);
        if (!GameEditDialog.edit(this, "New Game", game, db)) {
            return;
        }
        try {
            db.games().insert(game);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the game.", e);
            reload();
            return;
        }
        open(game);
    }

    @Override
    protected void editItem(Game game) {
        if (!GameEditDialog.edit(this, "Edit Game", game, db)) {
            return;
        }
        try {
            db.games().update(game);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Edit Game", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the game.", e);
        }
        reload();
    }

    @Override
    protected void deleteItem(Game game) {
        String description = Names.game(teamName(game.getAwayTeamId(), game.getDate()),
                teamName(game.getHomeTeamId(), game.getDate()), game.getDate(), game.getGameNumber());
        try {
            int atbats = db.games().countAtbats(game.getId());
            if (atbats > 0) {
                description += "\nThis also deletes its " + atbats + (atbats == 1 ? " at-bat" : " at-bats")
                        + " and their plays.";
            }
        } catch (SQLException e) {
            Errors.show(this, "Could not check the game's at-bats.", e);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Delete the game " + description + "?", "Delete Game",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            db.games().delete(game.getId());
        } catch (SQLException e) {
            Errors.show(this, "Could not delete the game.", e);
        }
        reload();
    }
}
