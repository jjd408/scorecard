package com.joe.scorecard.gui;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

/**
 * The user manual, shown in its own window at the section for whichever
 * window asked. The main window and every dialog have a Help button, and F1
 * does the same. The help window stays usable while a dialog is open.
 */
final class Help {

    /** The manual's sections, by their anchors in {@code help/manual.html}. */
    enum Topic {
        CONTENTS("contents"),
        MAIN_WINDOW("main-window"),
        TEAMS("teams"),
        PLAYERS("players"),
        ABBREVS("abbrevs"),
        GAMES("games"),
        SCORING("scoring"),
        PICKING_PLAYERS("picking-players"),
        PLAYS("plays"),
        PLAY_FORM("play-form"),
        STATS("stats");

        private final String anchor;

        Topic(String anchor) {
            this.anchor = anchor;
        }

        String anchor() {
            return anchor;
        }
    }

    static final String MANUAL = "help/manual.html";

    private static JFrame window;
    private static JEditorPane page;

    /** The anchor to scroll to once the manual has loaded, or null. */
    private static String pending;

    private Help() {
    }

    /** Returns a Help button that shows {@code topic}. */
    static JButton button(Topic topic) {
        JButton button = new JButton("Help");
        button.setToolTipText("Show the manual for this window (F1)");
        button.addActionListener(e -> show(button, topic));
        return button;
    }

    /** Makes F1 show {@code topic} while {@code window} has the focus. */
    static void install(RootPaneContainer window, Topic topic) {
        JComponent root = window.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help");
        root.getActionMap().put("help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                show(root, topic);
            }
        });
    }

    /** Opens the manual at {@code topic}, beside the window holding {@code parent}. */
    static void show(Component parent, Topic topic) {
        if (window == null) {
            if (!create(parent)) {
                return;
            }
            pending = topic.anchor();
        } else {
            page.scrollToReference(topic.anchor());
        }
        window.setVisible(true);
        window.toFront();
    }

    /** Makes the help window and starts loading the manual. Returns false if it can't be found. */
    private static boolean create(Component parent) {
        URL url = Help.class.getResource(MANUAL);
        if (url == null) {
            JOptionPane.showMessageDialog(parent, "The manual is missing from this build.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        page = new JEditorPane();
        page.setEditable(false);
        page.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription().startsWith("#")) {
                page.scrollToReference(e.getDescription().substring(1));
            }
        });
        // The page loads in the background; scroll to the asked-for section once it has.
        page.addPropertyChangeListener("page", e -> {
            if (pending != null) {
                String anchor = pending;
                pending = null;
                SwingUtilities.invokeLater(() -> page.scrollToReference(anchor));
            }
        });
        try {
            page.setPage(url);
        } catch (IOException e) {
            Errors.show(parent, "Could not open the manual.", e);
            page = null;
            return false;
        }
        window = new JFrame("Scorecard Help");
        // Usable even while a dialog that asked for help blocks the rest of the app.
        window.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        window.add(new JScrollPane(page));
        // Wide enough for the main-window screenshot at full size; Swing blurs images it scales.
        window.setPreferredSize(new Dimension(1070, 760));
        window.pack();
        Window owner = SwingUtilities.getWindowAncestor(parent);
        window.setLocationRelativeTo(owner == null ? parent : owner);
        return true;
    }
}
