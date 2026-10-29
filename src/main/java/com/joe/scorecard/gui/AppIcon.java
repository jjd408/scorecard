package com.joe.scorecard.gui;

import java.awt.Image;
import java.awt.Toolkit;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The Scorecard icon for window title bars and taskbars, at several sizes so
 * each system can pick a sharp one. Without it, windows show Java's icon.
 * Dialogs take theirs from the window that owns them.
 */
final class AppIcon {

    /** The sizes in {@code icons/}, made from {@code scripts/scorecard.svg}. */
    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() {
    }

    /** Returns the icon at every size there is. */
    static List<Image> images() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            URL url = AppIcon.class.getResource("icons/scorecard-" + size + ".png");
            if (url != null) {
                images.add(Toolkit.getDefaultToolkit().getImage(url));
            }
        }
        return images;
    }
}
