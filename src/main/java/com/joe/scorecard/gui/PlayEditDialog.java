package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Play;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/**
 * A form for entering a play: what happened (a scoring abbreviation), the
 * count when it happened, where the ball was played, the base the batter
 * reached and the out it made, and for a runner, which batter's play moved
 * them up.
 */
public class PlayEditDialog extends JDialog {

    private static final String[] BASES = {"None", "First", "Second", "Third", "Home"};
    private static final String[] OUTS = {"None", "1", "2", "3"};

    /** The fielding positions, at their scoring numbers. */
    private static final String[] FIELDS = {"None", "1 \u2013 Pitcher", "2 \u2013 Catcher",
        "3 \u2013 First base", "4 \u2013 Second base", "5 \u2013 Third base", "6 \u2013 Shortstop",
        "7 \u2013 Left field", "8 \u2013 Center field", "9 \u2013 Right field"};

    private final Play play;
    private final JComboBox<AbbrevChoice> abbrevField = new JComboBox<>();
    private final JSpinner pitchesField;
    private final JSpinner ballsField;
    private final JSpinner strikesField;
    private final JComboBox<String> fieldField = new JComboBox<>(FIELDS);
    private final JComboBox<String> advancedByField = new JComboBox<>();
    private final JComboBox<String> baseField = new JComboBox<>(BASES);
    private final JComboBox<String> outField = new JComboBox<>(OUTS);
    private final JTextArea commentField = new JTextArea(3, 24);
    private boolean saved;

    /**
     * Shows the form filled in from {@code play}, offering {@code abbrevs} to
     * pick from, and the batting-order spots with the batters in
     * {@code lineup}: its index is the spot, 1 to 9, and a blank or missing
     * name is a spot with no batter picked. If the user clicks OK, copies the
     * entered values into {@code play} and returns true.
     */
    public static boolean edit(Window owner, String title, Play play, List<Abbrev> abbrevs, String[] lineup) {
        PlayEditDialog dialog = new PlayEditDialog(owner, title, play, abbrevs, lineup);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private PlayEditDialog(Window owner, String title, Play play, List<Abbrev> abbrevs, String[] lineup) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.play = play;

        abbrevField.addItem(new AbbrevChoice(0, ""));
        for (Abbrev a : abbrevs) {
            AbbrevChoice choice = new AbbrevChoice(a.getId(), a.getAbbrev() + " – " + a.getDescription());
            abbrevField.addItem(choice);
            if (a.getId() == play.getAbbrevId()) {
                abbrevField.setSelectedItem(choice);
            }
        }
        // The same limits as the at-bat's own count.
        pitchesField = new JSpinner(new SpinnerNumberModel(clamp(play.getPitches(), 0, 99), 0, 99, 1));
        ballsField = new JSpinner(new SpinnerNumberModel(clamp(play.getBalls(), 0, 4), 0, 4, 1));
        strikesField = new JSpinner(new SpinnerNumberModel(clamp(play.getStrikes(), 0, 3), 0, 3, 1));
        fieldField.setSelectedIndex(clamp(play.getField(), 0, FIELDS.length - 1));
        // A batting-order spot, at its own index, or 0 for none.
        advancedByField.addItem("None");
        for (int spot = 1; spot <= 9; spot++) {
            String name = spot < lineup.length && lineup[spot] != null ? lineup[spot] : "";
            advancedByField.addItem(name.isBlank() ? String.valueOf(spot) : spot + " \u2013 " + name);
        }
        advancedByField.setSelectedIndex(clamp(play.getAdvancedBy(), 0, 9));
        advancedByField.setToolTipText("The batter whose play moved this runner up, by spot in the batting order");
        baseField.setSelectedIndex(clamp(play.getBase(), 0, BASES.length - 1));
        outField.setSelectedIndex(clamp(play.getOut(), 0, OUTS.length - 1));
        commentField.setText(play.getComment());
        commentField.setLineWrap(true);
        commentField.setWrapStyleWord(true);
        JScrollPane commentScroll = new JScrollPane(commentField);
        // A scroll pane's minimum size is tiny, so if the window comes out a
        // little short the form would squeeze it to nothing.
        commentScroll.setMinimumSize(commentScroll.getPreferredSize());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        addRow(form, c, "Play:", abbrevField);
        addRow(form, c, "Pitches:", pitchesField);
        addRow(form, c, "Balls:", ballsField);
        addRow(form, c, "Strikes:", strikesField);
        addRow(form, c, "Played to:", fieldField);
        addRow(form, c, "Base:", baseField);
        addRow(form, c, "Out:", outField);
        addRow(form, c, "By batter:", advancedByField);
        // Puts the label beside the comment's first line rather than its middle.
        c.anchor = GridBagConstraints.NORTHWEST;
        addRow(form, c, "Comment:", commentScroll);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> ok());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        buttons.add(Help.button(Help.Topic.PLAY_FORM));
        Help.install(this, Help.Topic.PLAY_FORM);

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

    private static void addRow(JPanel form, GridBagConstraints c, String label, JComponent field) {
        form.add(new JLabel(label), c);
        form.add(field, c);
        c.gridy++;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void ok() {
        AbbrevChoice abbrev = (AbbrevChoice) abbrevField.getSelectedItem();
        play.setAbbrevId(abbrev == null ? 0 : abbrev.id());
        play.setPitches((Integer) pitchesField.getValue());
        play.setBalls((Integer) ballsField.getValue());
        play.setStrikes((Integer) strikesField.getValue());
        play.setField(fieldField.getSelectedIndex());
        play.setAdvancedBy(advancedByField.getSelectedIndex());
        play.setBase(baseField.getSelectedIndex());
        play.setOut(outField.getSelectedIndex());
        String comment = commentField.getText().trim();
        play.setComment(comment.isEmpty() ? null : comment);
        saved = true;
        dispose();
    }

    /** An abbreviation as listed in the Play field. Id 0 is no abbreviation. */
    private record AbbrevChoice(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
