package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Atbat;
import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Game;
import com.joe.scorecard.data.Play;
import com.joe.scorecard.data.Player;
import com.joe.scorecard.data.SettingDao;
import com.joe.scorecard.data.Stats;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

/**
 * The main scorecard window: a row of buttons along the top, then a tab for
 * each team in the top half of a split pane, and the teams' batting and
 * pitching stats in the bottom half. Each team's tab is a grid of {@link AtbatPanel} cells. The
 * title, tabs and grids show the game that is open, and pressing a cell's
 * labels edits its at-bat. The game's runs, hits and errors are at the top
 * right, beside the buttons.
 */
public class ScFrame extends JFrame {

    private final Database db;
    private final String appTitle;

    /** The game being scored, or null if none is open. */
    private Game game;

    /**
     * The size of each team's grid of at-bat cells. A grid gets more columns
     * when it needs them; see {@link TeamGrid}.
     */
    private static final int ROWS = 9;
    private static final int COLUMNS = 12;

    /** The most pitches and innings an at-bat can have, enough for any real game. */
    private static final int MAX_PITCHES = 99;
    private static final int MAX_INNING = 99;

    /** Where a cell is: which team's grid, and its row and column. */
    private record CellRef(boolean home, int row, int column) {
    }

    /** The cell last pressed, shaded on its grid, or null if none is. */
    private CellRef selection;

    private final JTabbedPane tabs = new JTabbedPane();

    private final TeamGrid homeGrid = new TeamGrid(true);
    private final TeamGrid awayGrid = new TeamGrid(false);
    private final StatsPanel statsPanel = new StatsPanel("Home", "Away");
    private final ScorelinePanel scoreline = new ScorelinePanel();

    private final JButton teamsButton = new JButton("Teams");
    private final JButton playersButton = new JButton("Players");
    private final JButton gamesButton = new JButton("Games");
    private final JButton abbrevsButton = new JButton("Abbrevs");

    public ScFrame(String title, Database db) {
        super(title);
        this.db = db;
        this.appTitle = title;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    db.close();
                } catch (SQLException ex) {
                    // Exiting anyway.
                }
            }
        });

        setPreferredSize(new Dimension(1000, 780));

        // A dialog may have changed or deleted the open game or its teams.
        teamsButton.addActionListener(e -> {
            new TeamsDialog(this, db).setVisible(true);
            showGame();
        });
        playersButton.addActionListener(e -> {
            new PlayersDialog(this, db).setVisible(true);
            showGame();
        });
        gamesButton.addActionListener(e -> {
            new GamesDialog(this, db, this::openGame).setVisible(true);
            showGame();
        });
        // Plays are labeled with their abbreviations.
        abbrevsButton.addActionListener(e -> {
            new AbbrevsDialog(this, db).setVisible(true);
            showGame();
        });

        tabs.addTab("Home", homeGrid.scrollPane());
        tabs.addTab("Away", awayGrid.scrollPane());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(teamsButton);
        buttonPanel.add(playersButton);
        buttonPanel.add(gamesButton);
        buttonPanel.add(abbrevsButton);
        buttonPanel.add(Help.button(Help.Topic.CONTENTS));
        Help.install(this, Help.Topic.CONTENTS);
        // The buttons on the left, the score on the right.
        JPanel top = new JPanel(new BorderLayout());
        top.add(buttonPanel, BorderLayout.WEST);
        top.add(scoreline, BorderLayout.EAST);
        getContentPane().add(top, BorderLayout.NORTH);

        // The stats show the same team as the grid above them.
        tabs.addChangeListener(e -> statsPanel.tabs().setSelectedIndex(tabs.getSelectedIndex()));
        statsPanel.tabs().addChangeListener(e -> tabs.setSelectedIndex(statsPanel.tabs().getSelectedIndex()));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, statsPanel);
        split.setDividerLocation(480);
        split.setResizeWeight(0.7);
        getContentPane().add(split, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        reopenLastGame();
    }

    /**
     * One team's grid of at-bat cells, with {@link #ROWS} rows. It has at
     * least {@link #COLUMNS} columns, and enough for every at-bat saved in it.
     * Whenever every column has an active at-bat, it gets another column.
     */
    private final class TeamGrid {

        /** Whether this is the home team's grid, rather than the away team's. */
        final boolean home;

        /** The cells, by column and then row. */
        private final List<AtbatPanel[]> columns = new ArrayList<>();
        private final JPanel panel = new JPanel(new GridLayout(ROWS, 0));

        TeamGrid(boolean home) {
            this.home = home;
            setColumnCount(COLUMNS);
        }

        /** Returns the grid in a scroll pane, sized so the cells stay square. */
        JScrollPane scrollPane() {
            // Keeps the cells square instead of stretching them to fill the tab.
            JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            holder.add(panel);
            JScrollPane scroll = new JScrollPane(holder);
            scroll.getVerticalScrollBar().setUnitIncrement(AtbatPanel.SIZE / 4);
            scroll.getHorizontalScrollBar().setUnitIncrement(AtbatPanel.SIZE / 4);
            return scroll;
        }

        int columnCount() {
            return columns.size();
        }

        AtbatPanel cell(int row, int column) {
            return columns.get(column)[row];
        }

        /** Returns the active at-bats, a column at a time from the left, top to bottom: the order they happened. */
        List<Atbat> activeAtbats() {
            List<Atbat> atbats = new ArrayList<>();
            for (AtbatPanel[] column : columns) {
                for (AtbatPanel cell : column) {
                    if (cell.getAtbat() != null && cell.getAtbat().isActive()) {
                        atbats.add(cell.getAtbat());
                    }
                }
            }
            return atbats;
        }

        /** Returns the active at-bats in cells before ({@code row}, {@code column}), in the order they happened. */
        List<Atbat> activeAtbatsBefore(int row, int column) {
            List<Atbat> atbats = new ArrayList<>();
            for (Atbat a : activeAtbats()) {
                if (a.getColumn() < column || a.getColumn() == column && a.getRow() < row) {
                    atbats.add(a);
                }
            }
            return atbats;
        }

        /** Empties every cell and goes back to {@code count} columns. */
        void reset(int count) {
            columns.clear();
            setColumnCount(count);
        }

        /** Adds a column if every column has an active at-bat. */
        void growIfFull() {
            for (AtbatPanel[] column : columns) {
                boolean used = false;
                for (AtbatPanel cell : column) {
                    used |= cell.getAtbat() != null && cell.getAtbat().isActive();
                }
                if (!used) {
                    return;
                }
            }
            setColumnCount(columns.size() + 1);
        }

        /** Adds empty columns until there are {@code count}, and lays the cells out again. */
        private void setColumnCount(int count) {
            while (columns.size() < count) {
                int c = columns.size();
                AtbatPanel[] column = new AtbatPanel[ROWS];
                for (int r = 0; r < ROWS; r++) {
                    AtbatPanel cell = new AtbatPanel(r, c);
                    int row = r;
                    cell.setLabelListener((field, e) -> labelPressed(this, cell, row, c, field, e));
                    cell.setDiamondListener(e -> diamondPressed(this, cell, row, c, e));
                    column[r] = cell;
                }
                columns.add(column);
            }
            // GridLayout fills the grid a row at a time.
            panel.removeAll();
            for (int r = 0; r < ROWS; r++) {
                for (AtbatPanel[] column : columns) {
                    panel.add(column[r]);
                }
            }
            panel.revalidate();
            panel.repaint();
        }
    }

    /**
     * Puts the open game's at-bats in their cells, on the home or away grid
     * by the team at bat. At-bats saved before that was kept go by the
     * batter's team on the game date. Empties every other cell, and gives each
     * grid as many columns as its at-bats need.
     */
    private void showAtbats() throws SQLException {
        Map<TeamGrid, List<Atbat>> atbats = Map.of(homeGrid, new ArrayList<>(), awayGrid, new ArrayList<>());
        if (game != null) {
            for (Atbat a : db.atbats().findByGame(game.getId())) {
                long teamId = a.getTeamId() != 0 ? a.getTeamId()
                        : db.players().findById(a.getBatterId(), game.getDate())
                                .map(Player::getTeamId).orElse(0L);
                TeamGrid grid = teamId == game.getHomeTeamId() ? homeGrid
                        : teamId == game.getAwayTeamId() ? awayGrid
                        : null;
                if (grid != null && a.getRow() >= 0 && a.getRow() < ROWS && a.getColumn() >= 0) {
                    atbats.get(grid).add(a);
                }
            }
        }
        for (TeamGrid grid : List.of(homeGrid, awayGrid)) {
            int count = COLUMNS;
            for (Atbat a : atbats.get(grid)) {
                count = Math.max(count, a.getColumn() + 1);
            }
            grid.reset(count);
            for (Atbat a : atbats.get(grid)) {
                AtbatPanel cell = grid.cell(a.getRow(), a.getColumn());
                cell.setAtbat(a, playerName(a.getBatterId()), playerName(a.getPitcherId()));
                showPlays(cell);
            }
            grid.growIfFull();
        }
        // The cells were just made again, so shade the selected one anew.
        select(selection);
    }

    /** Shades the cell at {@code ref}, and stops shading the one selected before. Null selects none. */
    private void select(CellRef ref) {
        AtbatPanel old = cellAt(selection);
        if (old != null) {
            old.setSelected(false);
        }
        selection = ref;
        AtbatPanel cell = cellAt(ref);
        if (cell != null) {
            cell.setSelected(true);
        }
    }

    /** Returns the cell at {@code ref}, or null if ref is null or the grid has no such cell. */
    private AtbatPanel cellAt(CellRef ref) {
        if (ref == null) {
            return null;
        }
        TeamGrid grid = ref.home() ? homeGrid : awayGrid;
        return ref.row() < ROWS && ref.column() < grid.columnCount() ? grid.cell(ref.row(), ref.column()) : null;
    }

    /**
     * Fills in a newly active at-bat's batter and pitcher, if they aren't
     * picked yet. The batter is the last one in the same spot in the batting
     * order, and the pitcher the one who faced the team's last batter, both
     * from {@code before}: the team's active at-bats before it, in the order
     * they happened.
     */
    static void autofill(List<Atbat> before, Atbat a) {
        for (int i = before.size() - 1; i >= 0 && (a.getBatterId() == 0 || a.getPitcherId() == 0); i--) {
            Atbat prior = before.get(i);
            if (a.getBatterId() == 0 && prior.getBattingOrder() == a.getBattingOrder()) {
                a.setBatterId(prior.getBatterId());
            }
            if (a.getPitcherId() == 0) {
                a.setPitcherId(prior.getPitcherId());
            }
        }
    }

    /**
     * Edits the at-bat in {@code cell} when one of its labels is pressed, and
     * selects the cell. A left click on Active switches the at-bat between
     * active and inactive, filling in its batter and pitcher when it becomes
     * active; see {@link #autofill}. On an active at-bat, the left button
     * picks the batter or pitcher, or adds one to a number; the right button
     * takes one away. Activating an empty cell makes a new at-bat for the team
     * whose grid it's on.
     */
    private void labelPressed(TeamGrid grid, AtbatPanel cell, int row, int column,
            AtbatPanel.Field field, MouseEvent e) {
        boolean left = SwingUtilities.isLeftMouseButton(e);
        if (game == null || !left && !SwingUtilities.isRightMouseButton(e)) {
            return;
        }
        select(new CellRef(grid.home, row, column));
        long battingTeamId = grid.home ? game.getHomeTeamId() : game.getAwayTeamId();
        long pitchingTeamId = grid.home ? game.getAwayTeamId() : game.getHomeTeamId();
        int step = left ? 1 : -1;
        try {
            Atbat a = cell.getAtbat();
            switch (field) {
                case ACTIVE -> {
                    if (!left) {
                        return;
                    }
                    if (a == null) {
                        a = new Atbat();
                        a.setGameId(game.getId());
                        a.setTeamId(battingTeamId);
                        a.setRow(row);
                        a.setColumn(column);
                        // Usually the cell's own inning and spot in the order.
                        a.setInning(column + 1);
                        a.setBattingOrder(row + 1);
                    }
                    a.setActive(!a.isActive());
                    if (a.isActive()) {
                        autofill(grid.activeAtbatsBefore(row, column), a);
                    }
                }
                case BATTER -> {
                    if (!left) {
                        return;
                    }
                    Player batter = choosePlayer("Batter", battingTeamId, a.getBatterId());
                    if (batter == null) {
                        return;
                    }
                    a.setBatterId(batter.getId());
                }
                case PITCHER -> {
                    if (!left) {
                        return;
                    }
                    Player pitcher = choosePlayer("Pitcher", pitchingTeamId, a.getPitcherId());
                    if (pitcher == null) {
                        return;
                    }
                    a.setPitcherId(pitcher.getId());
                }
                // A walk takes four balls and a strikeout three strikes; four
                // runners at most can score on one play.
                case PITCHES -> a.setPitches(limit(a.getPitches() + step, 0, MAX_PITCHES));
                // Each ball or strike is also a pitch, so adding or taking one
                // away does the same to the pitch count.
                case BALLS -> {
                    int balls = limit(a.getBalls() + step, 0, 4);
                    a.setPitches(limit(a.getPitches() + balls - a.getBalls(), 0, MAX_PITCHES));
                    a.setBalls(balls);
                }
                case STRIKES -> {
                    int strikes = limit(a.getStrikes() + step, 0, 3);
                    a.setPitches(limit(a.getPitches() + strikes - a.getStrikes(), 0, MAX_PITCHES));
                    a.setStrikes(strikes);
                }
                case INNING -> a.setInning(limit(a.getInning() + step, 1, MAX_INNING));
                case ORDER -> a.setBattingOrder(limit(a.getBattingOrder() + step, 1, ROWS));
                case RBIS -> a.setRbis(limit(a.getRbis() + step, 0, 4));
                // Row and column aren't buttons.
                case ROW, COLUMN -> {
                    return;
                }
            }
            db.atbats().save(a);
            cell.setAtbat(a, playerName(a.getBatterId()), playerName(a.getPitcherId()));
            showPlays(cell);
            grid.growIfFull();
            showStats();
        } catch (SQLException ex) {
            Errors.show(this, "Could not save the at-bat.", ex);
            showGame();
        }
    }

    /**
     * Opens the at-bat's plays when the diamond in {@code cell} is
     * left-clicked. The first time, it goes straight to entering the batter's
     * own play, starting from the at-bat's count. After that it lists the
     * plays, where more can be added as the batter moves around the bases.
     * It also selects the cell.
     */
    private void diamondPressed(TeamGrid grid, AtbatPanel cell, int row, int column, MouseEvent e) {
        Atbat a = cell.getAtbat();
        if (!SwingUtilities.isLeftMouseButton(e) || a == null) {
            return;
        }
        select(new CellRef(grid.home, row, column));
        try {
            String[] lineup = lineupNames(lineup(grid.activeAtbats(), a));
            if (db.plays().findByAtbat(a.getId()).isEmpty()) {
                Play play = new Play();
                play.setAtbatId(a.getId());
                play.setPitches(a.getPitches());
                play.setBalls(a.getBalls());
                play.setStrikes(a.getStrikes());
                if (PlayEditDialog.edit(this, "New Play", play, db.abbrevs().findAll(), lineup)) {
                    db.plays().insert(play);
                }
            } else {
                new PlaysDialog(this, db, a, lineup).setVisible(true);
            }
            showPlays(cell);
            showStats();
        } catch (SQLException ex) {
            Errors.show(this, "Could not save the play.", ex);
        }
    }

    /**
     * Returns the batter in each spot of the batting order, by spot (1 to
     * {@link #ROWS}; index 0 is unused), as a runner on at-bat {@code a} would
     * see them: the next batter in the spot after {@code a}, since that's who
     * moves a runner up, or if none has batted yet, the last one before it.
     * {@code atbats} are the team's active at-bats, in the order they
     * happened. 0 is a spot with no batter picked.
     */
    static long[] lineup(List<Atbat> atbats, Atbat a) {
        long[] ids = new long[ROWS + 1];
        boolean[] next = new boolean[ROWS + 1];
        for (Atbat x : atbats) {
            int spot = x.getBattingOrder();
            if (spot < 1 || spot > ROWS || x.getBatterId() == 0 || next[spot]) {
                continue;
            }
            ids[spot] = x.getBatterId();
            next[spot] = x.getColumn() > a.getColumn() || x.getColumn() == a.getColumn() && x.getRow() > a.getRow();
        }
        return ids;
    }

    /** Returns the names of the players {@code ids}, at the same indexes, blank for none. */
    private String[] lineupNames(long[] ids) throws SQLException {
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            names[i] = playerName(ids[i]);
        }
        return names;
    }

    /** Draws the plays of the at-bat in {@code cell} on its diamond. */
    private void showPlays(AtbatPanel cell) throws SQLException {
        Map<Long, Abbrev> abbrevs = new HashMap<>();
        for (Abbrev ab : db.abbrevs().findAll()) {
            abbrevs.put(ab.getId(), ab);
        }
        cell.setPlays(db.plays().findByAtbat(cell.getAtbat().getId()), abbrevs);
    }

    /** Returns each scoring abbreviation's text, by its id. */
    private Map<Long, String> abbrevNames() throws SQLException {
        Map<Long, String> names = new HashMap<>();
        for (Abbrev ab : db.abbrevs().findAll()) {
            names.put(ab.getId(), ab.getAbbrev());
        }
        return names;
    }

    /** Returns {@code value}, moved into the range {@code min} to {@code max} if it's outside. */
    private static int limit(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Asks the user to pick a player who was on the team on the open game's
     * date, or add one, starting with {@code selectedId} selected. Returns
     * null if they didn't pick one.
     */
    private Player choosePlayer(String title, long teamId, long selectedId) throws SQLException {
        return PlayerChooser.choose(this, title + " \u2013 " + teamName(teamId), db, teamId,
                game.getDate(), selectedId);
    }

    /** Opens {@code game} for scoring, and remembers it for the next time the app starts. */
    public void openGame(Game game) {
        this.game = game;
        selection = null;
        try {
            db.settings().set(SettingDao.LAST_GAME_ID, Long.toString(game.getId()));
        } catch (SQLException e) {
            Errors.show(this, "Could not remember the open game.", e);
        }
        showGame();
    }

    /** Opens the game that was open when the app last ran, if it still exists. */
    private void reopenLastGame() {
        try {
            Optional<String> id = db.settings().get(SettingDao.LAST_GAME_ID);
            if (id.isPresent()) {
                game = new Game();
                game.setId(Long.parseLong(id.get()));
                showGame();
            }
        } catch (SQLException | NumberFormatException e) {
            // Start with no game open.
            game = null;
        }
    }

    /**
     * Shows the open game in the title and tabs, re-reading it and its teams
     * from the database. If the game has been deleted, closes it.
     */
    private void showGame() {
        String home = "Home";
        String away = "Away";
        String title = appTitle;
        if (game != null) {
            try {
                game = db.games().findById(game.getId()).orElse(null);
                if (game == null) {
                    db.settings().remove(SettingDao.LAST_GAME_ID);
                } else {
                    home = teamName(game.getHomeTeamId());
                    away = teamName(game.getAwayTeamId());
                    title = appTitle + " \u2013 " + Names.game(away, home, game.getDate(), game.getGameNumber());
                }
                showAtbats();
            } catch (SQLException e) {
                Errors.show(this, "Could not load the game.", e);
            }
        }
        setTitle(title);
        tabs.setTitleAt(0, home);
        tabs.setTitleAt(1, away);
        try {
            showStats();
        } catch (SQLException e) {
            Errors.show(this, "Could not work out the stats.", e);
        }
    }

    /**
     * Shows each team's batting and pitching stats from the open game's
     * active at-bats, and the scoreline. A team's pitchers are the ones who
     * faced the other team's batters.
     */
    private void showStats() throws SQLException {
        Map<Long, String> abbrevs = abbrevNames();
        boolean open = game != null;
        statsPanel.show(0, tabs.getTitleAt(0), open ? batting(homeGrid, abbrevs) : List.of(),
                open ? pitching(awayGrid, abbrevs) : List.of());
        statsPanel.show(1, tabs.getTitleAt(1), open ? batting(awayGrid, abbrevs) : List.of(),
                open ? pitching(homeGrid, abbrevs) : List.of());
        if (open) {
            scoreline.show(tabs.getTitleAt(1), line(awayGrid, homeGrid, abbrevs),
                    tabs.getTitleAt(0), line(homeGrid, awayGrid, abbrevs));
        } else {
            scoreline.clear();
        }
    }

    /**
     * Returns a team's runs and hits, from its at-bats on {@code batting},
     * and its errors, made in the field during the other team's at-bats on
     * {@code fielding}.
     */
    private ScorelinePanel.Line line(TeamGrid batting, TeamGrid fielding, Map<Long, String> abbrevs)
            throws SQLException {
        Stats.Batting total = new Stats.Batting();
        for (Atbat a : batting.activeAtbats()) {
            total.add(Stats.batting(a, db.plays().findByAtbat(a.getId()), abbrevs));
        }
        int errors = 0;
        for (Atbat a : fielding.activeAtbats()) {
            errors += Stats.errors(db.plays().findByAtbat(a.getId()), abbrevs);
        }
        return new ScorelinePanel.Line(total.runs, total.hits, errors);
    }

    /** Returns the batting stats of the batters on {@code grid}, in batting order. */
    private List<Map.Entry<String, Stats.Batting>> batting(TeamGrid grid, Map<Long, String> abbrevs)
            throws SQLException {
        List<Atbat> atbats = new ArrayList<>(grid.activeAtbats());
        atbats.sort(Comparator.comparingInt(Atbat::getBattingOrder));
        Map<Long, Stats.Batting> byBatter = new LinkedHashMap<>();
        for (Atbat a : atbats) {
            Stats.Batting b = Stats.batting(a, db.plays().findByAtbat(a.getId()), abbrevs);
            byBatter.computeIfAbsent(a.getBatterId(), id -> new Stats.Batting()).add(b);
        }
        List<Map.Entry<String, Stats.Batting>> rows = new ArrayList<>();
        for (Map.Entry<Long, Stats.Batting> e : byBatter.entrySet()) {
            rows.add(Map.entry(statsName(e.getKey()), e.getValue()));
        }
        return rows;
    }

    /** Returns the pitching stats of the pitchers who faced the batters on {@code grid}, in order of appearance. */
    private List<Map.Entry<String, Stats.Pitching>> pitching(TeamGrid grid, Map<Long, String> abbrevs)
            throws SQLException {
        Map<Long, Stats.Pitching> byPitcher = new LinkedHashMap<>();
        for (Atbat a : grid.activeAtbats()) {
            Stats.Pitching p = Stats.pitching(a, db.plays().findByAtbat(a.getId()), abbrevs);
            byPitcher.computeIfAbsent(a.getPitcherId(), id -> new Stats.Pitching()).add(p);
        }
        List<Map.Entry<String, Stats.Pitching>> rows = new ArrayList<>();
        for (Map.Entry<Long, Stats.Pitching> e : byPitcher.entrySet()) {
            rows.add(Map.entry(statsName(e.getKey()), e.getValue()));
        }
        return rows;
    }

    /** Returns the player's name for the stats, or "(not picked)" for no player. */
    private String statsName(long playerId) throws SQLException {
        String name = playerName(playerId);
        return name.isEmpty() ? "(not picked)" : name;
    }

    /** Returns the player's name, or blank if there's no such player. */
    private String playerName(long playerId) throws SQLException {
        return playerId == 0 ? ""
                : db.players().findById(playerId, game.getDate()).map(Player::getName).orElse("");
    }

    /** Returns the team's name as of the open game's date. */
    private String teamName(long teamId) throws SQLException {
        return db.teams().findById(teamId, game.getDate()).map(Names::team).orElse("");
    }

    /** Switches Swing to the Nimbus look and feel, if this Java has it. */
    public static void useNimbus() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                    // Keep the default look and feel.
                }
                return;
            }
        }
    }
}
