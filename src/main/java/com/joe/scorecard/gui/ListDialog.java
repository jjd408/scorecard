package com.joe.scorecard.gui;

import com.joe.scorecard.data.Database;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * A dialog listing one kind of data object in a table, with New, Delete, Edit
 * and Close buttons down the right side. Subclasses load the rows, say what
 * each column shows, and handle the buttons. They call {@link #reload()} at
 * the end of their constructor.
 */
abstract class ListDialog<T> extends JDialog {

    protected final Database db;
    private final ItemTableModel model;
    private final JTable table;

    private final JButton newButton = new JButton("New");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton editButton = new JButton("Edit");
    private final JButton closeButton = new JButton("Close");
    private final JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
    private final List<JButton> selectionButtons = new ArrayList<>();

    /** Makes the dialog; its Help button and F1 show {@code help}. */
    protected ListDialog(Window owner, String title, Help.Topic help, Database db, String... columns) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.db = db;
        model = new ItemTableModel(columns);
        table = new JTable(model);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selectedItem() != null) {
                    editItem(selectedItem());
                }
            }
        });

        newButton.addActionListener(e -> newItem());
        deleteButton.addActionListener(e -> {
            if (selectedItem() != null) {
                deleteItem(selectedItem());
            }
        });
        editButton.addActionListener(e -> {
            if (selectedItem() != null) {
                editItem(selectedItem());
            }
        });
        closeButton.addActionListener(e -> dispose());

        buttons.add(newButton);
        buttons.add(deleteButton);
        buttons.add(editButton);
        buttons.add(closeButton);
        JPanel east = new JPanel(new BorderLayout());
        east.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        east.add(buttons, BorderLayout.NORTH);
        east.add(Help.button(help), BorderLayout.SOUTH);
        Help.install(this, help);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(east, BorderLayout.EAST);
        setContentPane(content);
        getRootPane().setDefaultButton(closeButton);

        setPreferredSize(new Dimension(500, 350));
        pack();
        setLocationRelativeTo(owner);
        updateButtons();
    }

    /**
     * Adds a button below the standard ones. If {@code needsSelection}, it is
     * only enabled while a row is selected.
     */
    protected final void addButton(JButton button, boolean needsSelection) {
        buttons.add(button);
        if (needsSelection) {
            selectionButtons.add(button);
            button.setEnabled(selectedItem() != null);
        }
        pack();
    }

    /** Sets the dialog's size and the preferred width of each column. */
    protected final void setSizes(int width, int height, int... columnWidths) {
        for (int i = 0; i < columnWidths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        setSize(width, height);
        setLocationRelativeTo(getOwner());
    }

    /** Returns the rows to show. */
    protected abstract List<T> load() throws SQLException;

    /** Returns what to show for {@code item} in the given column. */
    protected abstract Object valueAt(T item, int column);

    protected abstract long idOf(T item);

    protected abstract void newItem();

    protected abstract void editItem(T item);

    protected abstract void deleteItem(T item);

    /** Reloads the rows from the database, keeping the selection if possible. */
    protected final void reload() {
        T current = selectedItem();
        try {
            model.setItems(load());
        } catch (SQLException e) {
            Errors.show(this, "Could not load " + getTitle().toLowerCase() + ".", e);
        }
        if (current != null) {
            select(idOf(current));
        }
        updateButtons();
    }

    /** Selects the row with this id, if it is shown. */
    protected final void select(long id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (idOf(model.getItem(i)) == id) {
                int row = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    /** Returns the selected row's item, or null if no row is selected. */
    protected final T selectedItem() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getItem(table.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        deleteButton.setEnabled(selected);
        editButton.setEnabled(selected);
        for (JButton b : selectionButtons) {
            b.setEnabled(selected);
        }
    }

    private class ItemTableModel extends AbstractTableModel {

        private final String[] columns;
        private List<T> items = new ArrayList<>();

        ItemTableModel(String[] columns) {
            this.columns = columns;
        }

        void setItems(List<T> items) {
            this.items = items;
            fireTableDataChanged();
        }

        T getItem(int row) {
            return items.get(row);
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            return valueAt(items.get(row), column);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            // Lets the row sorter sort number columns numerically.
            for (T item : items) {
                Object value = valueAt(item, column);
                if (value != null) {
                    return value.getClass();
                }
            }
            return Object.class;
        }
    }
}
