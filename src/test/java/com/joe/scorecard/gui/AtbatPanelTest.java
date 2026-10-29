package com.joe.scorecard.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Atbat;
import com.joe.scorecard.data.Play;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AtbatPanelTest {

    /** Paints {@code cell} at its preferred size and returns the picture. */
    private static BufferedImage paint(AtbatPanel cell) {
        cell.setSize(AtbatPanel.SIZE, AtbatPanel.SIZE);
        BufferedImage image = new BufferedImage(AtbatPanel.SIZE, AtbatPanel.SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        cell.paint(g);
        g.dispose();
        return image;
    }

    /** Returns the cell's tooltip with the mouse at (x, y). */
    private static String tipAt(AtbatPanel cell, int x, int y) {
        return cell.getToolTipText(new MouseEvent(cell, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    @Test
    void diamondTooltipListsThePlays() {
        AtbatPanel cell = new AtbatPanel(0, 0);
        cell.setSize(AtbatPanel.SIZE, AtbatPanel.SIZE);
        Atbat a = new Atbat();
        a.setActive(true);
        cell.setAtbat(a, "Batter 1", "Starter Sam");
        int x = AtbatPanel.SIZE * 2 / 3;
        int y = AtbatPanel.SIZE / 2;
        assertEquals("No plays yet. Click to add one.", tipAt(cell, x, y));

        Abbrev single = new Abbrev();
        single.setId(1);
        single.setAbbrev("1B");
        single.setDescription("Single");
        Play play = new Play();
        play.setAbbrevId(1);
        play.setBase(1);
        play.setField(8);
        play.setBalls(2);
        play.setStrikes(1);
        play.setPitches(4);
        play.setComment("Line drive <hard>");
        cell.setPlays(List.of(play), Map.of(1L, single));
        String tip = tipAt(cell, x, y);
        assertTrue(tip.contains("1B 8 – Single"), tip);
        assertTrue(tip.contains("1st"), tip);
        assertTrue(tip.contains("8 CF"), tip);
        assertTrue(tip.contains("2-1, 4 pitches"), tip);
        assertTrue(tip.contains("Line drive &lt;hard&gt;"), tip);
        // No play has an out or a runner moved up by a batter, so those columns are left out.
        assertFalse(tip.contains(">Out<"), tip);
        assertFalse(tip.contains(">By #<"), tip);

        // Away from the diamond, over the labels, there's no tooltip.
        assertNull(tipAt(cell, 5, AtbatPanel.SIZE - 5));
    }

    @Test
    void selectedCellIsShadedAndUnselectingClearsIt() {
        AtbatPanel cell = new AtbatPanel(0, 0);
        // A spot well inside the cell, below the labels and left of the diamond.
        int x = 10;
        int y = AtbatPanel.SIZE - 10;
        assertEquals(Color.WHITE.getRGB(), paint(cell).getRGB(x, y));

        cell.setSelected(true);
        BufferedImage selected = paint(cell);
        assertNotEquals(Color.WHITE.getRGB(), selected.getRGB(x, y));
        // The heavier border.
        assertNotEquals(selected.getRGB(x, y), selected.getRGB(1, AtbatPanel.SIZE / 2));

        cell.setSelected(false);
        assertEquals(Color.WHITE.getRGB(), paint(cell).getRGB(x, y));
    }
}
