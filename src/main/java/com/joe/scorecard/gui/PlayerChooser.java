package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import com.joe.scorecard.data.Player;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * A dialog for picking one of a team's players as of a date. Double-clicking
 * a player or pressing OK picks them; Cancel or closing the dialog picks no
 * one. Add makes a new player for the team, even when it has none yet.
 */
final class PlayerChooser extends JDialog {

    private final Database db;
    private final long teamId;
    private final LocalDate date;
    private final PlayerTableModel model = new PlayerTableModel();
    private final JTable table = new JTable(model);
    private List<Player> players = List.of();
    private Player chosen;

    private PlayerChooser(Window owner, String title, Database db, long teamId, LocalDate date,
            long selectedId) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.db = db;
        this.teamId = teamId;
        this.date = date;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    choose();
                }
            }
        });

        JButton addButton = new JButton("Add");
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        addButton.addActionListener(e -> add());
        okButton.addActionListener(e -> choose());
        cancelButton.addActionListener(e -> dispose());
        table.getSelectionModel().addListSelectionListener(
                e -> okButton.setEnabled(table.getSelectedRow() >= 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(addButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(okButton);
        right.add(cancelButton);
        right.add(Help.button(Help.Topic.PICKING_PLAYERS));
        Help.install(this, Help.Topic.PICKING_PLAYERS);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(okButton);

        reload();
        select(selectedId);
        okButton.setEnabled(table.getSelectedRow() >= 0);

        setPreferredSize(new Dimension(320, 400));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog listing the players who were on the team on
     * {@code date}, and returns the player picked, or null if none was. The
     * player with {@code selectedId} starts out selected, if listed.
     */
    static Player choose(Component parent, String title, Database db, long teamId, LocalDate date,
            long selectedId) {
        PlayerChooser dialog = new PlayerChooser(SwingUtilities.getWindowAncestor(parent), title,
                db, teamId, date, selectedId);
        dialog.setVisible(true);
        return dialog.chosen;
    }

    private void reload() {
        try {
            players = db.players().findByTeam(teamId, date);
        } catch (SQLException e) {
            Errors.show(this, "Could not load the players.", e);
        }
        model.fireTableDataChanged();
    }

    /** Selects the player with this id, if listed. Returns whether they were. */
    private boolean select(long id) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId() == id) {
                int row = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return true;
            }
        }
        return false;
    }

    /**
     * Makes a new player, starting out on this team as of this date, and
     * selects them.
     */
    private void add() {
        Player player = new Player();
        player.setTeamId(teamId);
        try {
            LocalDate from = PlayerEditDialog.edit(this, "New Player", player, db.teams().findAll(),
                    true, date);
            if (from == null) {
                return;
            }
            db.players().insert(player, from);
        } catch (SQLException e) {
            Errors.show(this, "Could not save the player.", e);
            return;
        }
        reload();
        if (!select(player.getId())) {
            // The user moved them to another team, or joined them after this date.
            JOptionPane.showMessageDialog(this, player.getName() + " was added, but wasn't on this team on "
                    + date + ", so isn't listed here.", getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void choose() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            chosen = players.get(table.convertRowIndexToModel(row));
            dispose();
        }
    }

    private class PlayerTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return players.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Name" : "Number";
        }

        @Override
        public Object getValueAt(int row, int column) {
            Player p = players.get(row);
            return column == 0 ? p.getName() : p.getNumber();
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? String.class : Integer.class;
        }
    }
}
