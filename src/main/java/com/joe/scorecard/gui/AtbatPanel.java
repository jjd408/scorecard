package com.joe.scorecard.gui;

import com.joe.scorecard.data.Abbrev;
import com.joe.scorecard.data.Atbat;
import com.joe.scorecard.data.Play;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

/**
 * One cell of the scorecard grid, drawn like a box on a paper scorecard: the
 * at-bat's labeled values listed down the left side and the base diamond to
 * their right. An inactive at-bat, or an empty cell, lists only whether it's
 * active and its row and column, in gray. An active one lists the rest of its
 * values instead. The batter's and pitcher's names are above the diamond, so
 * they use the cell's full width. On an active at-bat, its plays are drawn on
 * the diamond: the base paths and bases reached safely in green, and each
 * out's number circled in red beside the base it was made at.
 * Each label works as a button: pressing it tells the {@link LabelListener}.
 * So does the diamond of an active at-bat, which tells the diamond listener.
 * The selected cell is shaded, with a heavier border. Resting the mouse on
 * the diamond of an active at-bat lists its plays in a tooltip.
 */
public class AtbatPanel extends JPanel {

    /** The preferred width and height of a cell, in pixels. */
    public static final int SIZE = 150;

    /** The point size of the values listed down the left side. */
    private static final float VALUE_FONT_SIZE = 9f;

    private static final Color LINE = new Color(0x9a, 0xa4, 0xb0);
    private static final Color INK = Color.BLACK;
    private static final Color SAFE = new Color(0x1e, 0x8e, 0x3e);
    private static final Color OUT = new Color(0xd0, 0x21, 0x21);

    /** The background and border of the selected cell. */
    private static final Color SELECTED_FILL = new Color(0xff, 0xf6, 0xd5);
    private static final Color SELECTED_LINE = new Color(0x1f, 0x6f, 0xd0);

    /** The radius of the circle around an out's number, in pixels. */
    private static final int OUT_RADIUS = 6;

    /** The point size of the plays written on the diamond. */
    private static final float PLAY_FONT_SIZE = 8f;

    /** How far inside the diamond a leg's label is, in pixels each way. */
    private static final int LABEL_INSET = 7;

    /** The widest value the space left of the diamond is sized for. */
    private static final String SAMPLE_VALUE = "0000";

    /** The space between the cell's edge and the labels, and around the diamond. */
    private static final int PAD = 3;

    /** How far left of center the diamond is drawn, in pixels. */
    private static final int DIAMOND_SHIFT = 5;

    /** Half the width of a base or home plate, in pixels. */
    private static final int BASE = 5;

    /** The plays tooltip's columns, and how each is lined up. */
    private static final String[] TIP_COLUMNS = {"#", "Play", "Base", "Out", "Fielder", "Count", "By #", "Comment"};
    private static final String[] TIP_ALIGN = {"right", "left", "center", "center", "left", "center", "center",
        "left"};

    /** The bases, by number, as the plays tooltip names them. */
    private static final String[] TIP_BASES = {"", "1st", "2nd", "3rd", "Home"};

    /** The fielding positions' short names, at their scoring numbers. */
    private static final String[] POSITIONS = {"", "P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF"};

    /** The values a cell can list down the left side. */
    public enum Field {
        ROW("Row"),
        COLUMN("Column"),
        BATTER("Batter"),
        PITCHER("Pitcher"),
        PITCHES("Pitches"),
        BALLS("Balls"),
        STRIKES("Strikes"),
        INNING("Inning"),
        ORDER("Order"),
        RBIS("RBIs"),
        ACTIVE("Active");

        private final String label;

        Field(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** What an inactive at-bat lists, in order. Only Active is a button. */
    private static final List<Field> INACTIVE_FIELDS = List.of(Field.ROW, Field.COLUMN, Field.ACTIVE);

    /** What an active at-bat lists, in order. They're all buttons. */
    private static final List<Field> ACTIVE_FIELDS = List.of(Field.BATTER, Field.PITCHER,
            Field.PITCHES, Field.BALLS, Field.STRIKES, Field.INNING, Field.ORDER, Field.RBIS,
            Field.ACTIVE);

    /** Told when one of a cell's labels is pressed. */
    public interface LabelListener {
        void labelPressed(Field field, MouseEvent e);
    }

    private final int row;
    private final int column;
    private Atbat atbat;
    private String batterName = "";
    private String pitcherName = "";
    private List<Play> plays = List.of();
    private Map<Long, Abbrev> abbrevs = Map.of();
    private LabelListener labelListener;
    private Consumer<MouseEvent> diamondListener;
    private boolean selected;

    /** Makes an empty cell for this row and column of the grid. */
    public AtbatPanel(int row, int column) {
        this.row = row;
        this.column = column;
        setPreferredSize(new Dimension(SIZE, SIZE));
        setBackground(Color.WHITE);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Field field = fieldAt(e.getX(), e.getY());
                if (field != null && labelListener != null) {
                    labelListener.labelPressed(field, e);
                } else if (inDiamond(e.getX(), e.getY()) && diamondListener != null) {
                    diamondListener.accept(e);
                }
            }

            // Swing hides a tooltip after a few seconds; the plays tooltip
            // stays until the mouse leaves the diamond. The delay is shared by
            // every component, so it goes back when the mouse leaves the cell.
            private int dismissDelay;

            @Override
            public void mouseEntered(MouseEvent e) {
                dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
                ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(dismissDelay);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean button = fieldAt(e.getX(), e.getY()) != null || inDiamond(e.getX(), e.getY());
                setCursor(button ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        // The plays tooltip; see getToolTipText.
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Returns the at-bat shown in this cell, or null if it's empty. */
    public Atbat getAtbat() {
        return atbat;
    }

    /**
     * Shows {@code atbat} in this cell, with its batter's and pitcher's
     * names, or clears it if null. A null name shows as blank.
     */
    public void setAtbat(Atbat atbat, String batterName, String pitcherName) {
        this.atbat = atbat;
        this.batterName = batterName == null ? "" : batterName;
        this.pitcherName = pitcherName == null ? "" : pitcherName;
        this.plays = List.of();
        repaint();
    }

    /**
     * Draws {@code plays} on the diamond, in order, labeled with their
     * abbreviations from {@code abbrevs} (by abbreviation id). Call after
     * {@link #setAtbat}, which clears them.
     */
    public void setPlays(List<Play> plays, Map<Long, Abbrev> abbrevs) {
        this.plays = List.copyOf(plays);
        this.abbrevs = Map.copyOf(abbrevs);
        repaint();
    }

    public void setLabelListener(LabelListener labelListener) {
        this.labelListener = labelListener;
    }

    /** Sets what to tell when the diamond of an active at-bat is pressed. */
    public void setDiamondListener(Consumer<MouseEvent> diamondListener) {
        this.diamondListener = diamondListener;
    }

    /** Shades this cell as the selected one, or stops shading it. */
    public void setSelected(boolean selected) {
        this.selected = selected;
        setBackground(selected ? SELECTED_FILL : Color.WHITE);
        repaint();
    }

    /** Returns whether there's an at-bat in this cell and it's active. */
    private boolean isActive() {
        return atbat != null && atbat.isActive();
    }

    private List<Field> fields() {
        return isActive() ? ACTIVE_FIELDS : INACTIVE_FIELDS;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            if (selected) {
                g2.setColor(SELECTED_LINE);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRect(1, 1, w - 3, h - 3);
            } else {
                g2.setColor(LINE);
                g2.drawRect(0, 0, w - 1, h - 1);
            }

            // The labels, one per line down the left side and right-justified,
            // each followed by its value.
            g2.setFont(valueFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isActive() ? INK : LINE);
            int labelWidth = labelWidth(fm);
            int valueX = PAD + labelWidth + fm.stringWidth(" ");
            int y = PAD + fm.getAscent();
            for (Field field : fields()) {
                g2.drawString(field.label(), PAD + labelWidth - fm.stringWidth(field.label()), y);
                g2.drawString(fit(fm, value(field), w - PAD - valueX), valueX, y);
                y += fm.getHeight();
            }

            // The diamond, with a base at each corner.
            Polygon diamond = diamond(fm);
            int cx = diamond.xpoints[0];
            int cy = diamond.ypoints[1];
            int r = diamond.ypoints[0] - cy;
            Color ink = isActive() ? INK : LINE;
            g2.setColor(ink);
            g2.setStroke(new BasicStroke(1f));
            g2.drawPolygon(diamond);

            if (isActive()) {
                drawPlays(g2, cx, cy, r, ink);
            } else {
                drawBases(g2, cx, cy, r, ink, 0);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Returns the diamond: home at the bottom, then first, second and third
     * counterclockwise, filling the space right of the values.
     */
    private Polygon diamond(FontMetrics fm) {
        int left = textRight(fm) + PAD;
        int cx = left + (getWidth() - left) / 2 - DIAMOND_SHIFT;
        int cy = getHeight() / 2;
        int r = Math.min(getWidth() - left - PAD, getHeight()) / 2 - BASE;
        return new Polygon(
                new int[] {cx, cx + r, cx, cx - r},
                new int[] {cy + r, cy, cy - r, cy},
                4);
    }

    /** Returns whether (x, y) is inside the diamond of an active at-bat. */
    private boolean inDiamond(int x, int y) {
        return isActive() && diamond(getFontMetrics(valueFont())).contains(x, y);
    }

    /** Returns a base centered on (x, y): a square standing on one corner. */
    private static Polygon base(int x, int y) {
        return new Polygon(
                new int[] {x, x + BASE, x, x - BASE},
                new int[] {y - BASE, y, y + BASE, y},
                4);
    }

    /** Returns home plate centered on (x, y): a pentagon with its point down. */
    private static Polygon homePlate(int x, int y) {
        return new Polygon(
                new int[] {x - BASE, x + BASE, x + BASE, x, x - BASE},
                new int[] {y - BASE, y - BASE, y, y + BASE, y},
                5);
    }

    /**
     * Draws the plays on the diamond, in order, the way a paper scorecard
     * shows a runner's trip around the bases. Each leg run safely is green,
     * and so is each base reached. An out is a red line halfway to the base
     * it was made at, with the out's number circled beside that base. Each
     * play's abbreviation, and the batting-order spot of the batter whose play
     * moved the runner, is written inside the diamond beside the last leg it
     * covered, or in the middle for a play that reached no base, like a K.
     */
    private void drawPlays(Graphics2D g2, int cx, int cy, int r, Color ink) {
        // Where each label and circled out goes, drawn last so the lines don't cross them.
        record Mark(Point at, String text, Color color, boolean circled) {
        }
        List<Mark> marks = new ArrayList<>();
        int reached = 0;
        g2.setStroke(new BasicStroke(2f));
        for (Play p : plays) {
            int base = Math.max(0, Math.min(4, p.getBase()));
            String label = label(p);
            if (p.getOut() > 0) {
                int safe = Math.max(reached, base - 1);
                drawLegs(g2, cx, cy, r, reached, safe);
                if (base > safe) {
                    Point from = corner(cx, cy, r, safe);
                    Point to = corner(cx, cy, r, base);
                    g2.setColor(OUT);
                    g2.drawLine(from.x, from.y, (from.x + to.x) / 2, (from.y + to.y) / 2);
                }
                if (!label.isEmpty()) {
                    marks.add(new Mark(base == 0 ? new Point(cx, cy) : legLabel(cx, cy, r, base), label, OUT, false));
                }
                marks.add(new Mark(outMark(cx, cy, r, base), String.valueOf(p.getOut()), OUT, true));
                reached = safe;
                break;
            }
            if (base > reached) {
                drawLegs(g2, cx, cy, r, reached, base);
                if (!label.isEmpty()) {
                    marks.add(new Mark(legLabel(cx, cy, r, base), label, SAFE, false));
                }
                reached = base;
            } else if (base == 0 && !label.isEmpty()) {
                marks.add(new Mark(new Point(cx, cy), label, ink, false));
            }
        }
        g2.setStroke(new BasicStroke(1f));
        drawBases(g2, cx, cy, r, ink, reached);

        g2.setFont(valueFont().deriveFont(Font.BOLD, PLAY_FONT_SIZE));
        FontMetrics fm = g2.getFontMetrics();
        for (Mark m : marks) {
            int x = m.at().x;
            int y = m.at().y;
            if (m.circled()) {
                g2.setColor(getBackground());
                g2.fillOval(x - OUT_RADIUS, y - OUT_RADIUS, 2 * OUT_RADIUS, 2 * OUT_RADIUS);
                g2.setColor(m.color());
                g2.drawOval(x - OUT_RADIUS, y - OUT_RADIUS, 2 * OUT_RADIUS, 2 * OUT_RADIUS);
            }
            g2.setColor(m.color());
            g2.drawString(m.text(), x - fm.stringWidth(m.text()) / 2, y + (fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    /** Draws the legs run safely from base {@code from} to base {@code to}, in green. */
    private static void drawLegs(Graphics2D g2, int cx, int cy, int r, int from, int to) {
        g2.setColor(SAFE);
        for (int b = from + 1; b <= to; b++) {
            Point a = corner(cx, cy, r, b - 1);
            Point z = corner(cx, cy, r, b);
            g2.drawLine(a.x, a.y, z.x, z.y);
        }
    }

    /** Draws home plate and the bases, filling the ones up to {@code reached} in green. */
    private void drawBases(Graphics2D g2, int cx, int cy, int r, Color ink, int reached) {
        drawShape(g2, homePlate(cx, cy + r), ink, reached >= 4 ? SAFE : getBackground());
        for (int b = 1; b <= 3; b++) {
            Point at = corner(cx, cy, r, b);
            drawShape(g2, base(at.x, at.y), ink, reached >= b ? SAFE : getBackground());
        }
    }

    /** Returns what's written for a play: its abbreviation, then the batter who moved the runner, if any. */
    private String label(Play p) {
        String abbrev = abbrevText(p);
        if (p.getAdvancedBy() <= 0) {
            return abbrev;
        }
        return abbrev.isEmpty() ? String.valueOf(p.getAdvancedBy()) : abbrev + " " + p.getAdvancedBy();
    }

    /** Returns the play's abbreviation with the number of the fielder it was hit to, or blank if it has neither. */
    private String abbrevText(Play p) {
        Abbrev ab = abbrevs.get(p.getAbbrevId());
        return Names.play(ab == null ? null : ab.getAbbrev(), p.getField());
    }

    /**
     * Shows the plays when the mouse rests on the diamond of an active
     * at-bat: a table with a row for each, in order. Columns no play has a
     * value for are left out. With no plays yet, says how to add one.
     */
    @Override
    public String getToolTipText(MouseEvent e) {
        if (!inDiamond(e.getX(), e.getY())) {
            return null;
        }
        if (plays.isEmpty()) {
            return "No plays yet. Click to add one.";
        }
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < plays.size(); i++) {
            Play p = plays.get(i);
            Abbrev ab = abbrevs.get(p.getAbbrevId());
            String play = abbrevText(p);
            if (ab != null && ab.getDescription() != null && !ab.getDescription().isBlank()) {
                play += " – " + ab.getDescription();
            }
            rows.add(new String[] {
                String.valueOf(i + 1),
                escape(play),
                TIP_BASES[Math.max(0, Math.min(4, p.getBase()))],
                p.getOut() > 0 ? String.valueOf(p.getOut()) : "",
                p.getField() >= 1 && p.getField() <= 9 ? p.getField() + " " + POSITIONS[p.getField()] : "",
                count(p),
                p.getAdvancedBy() > 0 ? String.valueOf(p.getAdvancedBy()) : "",
                escape(p.getComment() == null ? "" : p.getComment())});
        }
        boolean[] shown = new boolean[TIP_COLUMNS.length];
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                shown[c] |= !row[c].isEmpty();
            }
        }
        StringBuilder html = new StringBuilder("<html><table cellspacing=0 cellpadding=3><tr>");
        for (int c = 0; c < TIP_COLUMNS.length; c++) {
            if (shown[c]) {
                html.append("<th align=").append(TIP_ALIGN[c]).append('>').append(TIP_COLUMNS[c]).append("</th>");
            }
        }
        html.append("</tr>");
        for (String[] row : rows) {
            html.append("<tr>");
            for (int c = 0; c < row.length; c++) {
                if (shown[c]) {
                    html.append("<td align=").append(TIP_ALIGN[c]).append('>').append(row[c]).append("</td>");
                }
            }
            html.append("</tr>");
        }
        return html.append("</table></html>").toString();
    }

    /** Returns the count and pitches a play came on, like "2-1, 4 pitches", or blank if none were thrown. */
    private static String count(Play p) {
        if (p.getPitches() == 0 && p.getBalls() == 0 && p.getStrikes() == 0) {
            return "";
        }
        return p.getBalls() + "-" + p.getStrikes() + ", " + p.getPitches()
                + (p.getPitches() == 1 ? " pitch" : " pitches");
    }

    /** Returns {@code s} with the characters HTML treats specially written out. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** Returns where a label goes for the leg into {@code base}: inside the diamond, beside its middle. */
    private static Point legLabel(int cx, int cy, int r, int base) {
        Point a = corner(cx, cy, r, base - 1);
        Point z = corner(cx, cy, r, base);
        int mx = (a.x + z.x) / 2;
        int my = (a.y + z.y) / 2;
        return new Point(mx + Integer.signum(cx - mx) * LABEL_INSET, my + Integer.signum(cy - my) * LABEL_INSET);
    }

    /** Returns where an out made at {@code base} is circled: just inside it, or home plate for 0. */
    private static Point outMark(int cx, int cy, int r, int base) {
        Point at = corner(cx, cy, r, base);
        int inward = BASE + OUT_RADIUS + 3;
        return new Point(at.x + Integer.signum(cx - at.x) * inward, at.y + Integer.signum(cy - at.y) * inward);
    }

    /**
     * Returns the corner of the diamond centered on (cx, cy) with radius
     * {@code r} for {@code base}: 1 first, 2 second, 3 third, and 0 or 4 home.
     */
    private static Point corner(int cx, int cy, int r, int base) {
        return switch (base % 4) {
            case 1 -> new Point(cx + r, cy);
            case 2 -> new Point(cx, cy - r);
            case 3 -> new Point(cx - r, cy);
            default -> new Point(cx, cy + r);
        };
    }

    /** Fills {@code shape} with {@code fill}, covering the base paths, then outlines it. */
    private static void drawShape(Graphics2D g2, Polygon shape, Color ink, Color fill) {
        g2.setColor(fill);
        g2.fillPolygon(shape);
        g2.setColor(ink);
        g2.drawPolygon(shape);
    }

    /**
     * Returns the field whose label is a button at (x, y), or null if there's
     * none there.
     */
    private Field fieldAt(int x, int y) {
        FontMetrics fm = getFontMetrics(valueFont());
        if (x < 0 || y < PAD) {
            return null;
        }
        int line = (y - PAD) / fm.getHeight();
        if (line >= fields().size()) {
            return null;
        }
        Field field = fields().get(line);
        if (field == Field.ROW || field == Field.COLUMN) {
            return null;
        }
        // The names run the full width; the other lines stop short of the diamond.
        boolean name = field == Field.BATTER || field == Field.PITCHER;
        return x < (name ? getWidth() : textRight(fm)) ? field : null;
    }

    private Font valueFont() {
        return getFont().deriveFont(VALUE_FONT_SIZE);
    }

    private static int labelWidth(FontMetrics fm) {
        int widest = 0;
        for (Field field : Field.values()) {
            widest = Math.max(widest, fm.stringWidth(field.label()));
        }
        return widest;
    }

    /**
     * Returns the right edge of the labels and values. It's sized for a sample
     * value rather than the real ones, so the diamond is in the same place in
     * every cell.
     */
    private static int textRight(FontMetrics fm) {
        return PAD + labelWidth(fm) + fm.stringWidth(" " + SAMPLE_VALUE);
    }

    /** Returns {@code s}, cut short with an ellipsis if it's wider than {@code width}. */
    private static String fit(FontMetrics fm, String s, int width) {
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        String ellipsis = "\u2026";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return s.substring(0, end) + ellipsis;
    }

    /**
     * Returns the at-bat's value for {@code field}, as listed in the cell. Row
     * and column count from 1, and are the cell's own, so an empty cell has them.
     */
    private String value(Field field) {
        Atbat a = atbat;
        return switch (field) {
            case ACTIVE -> isActive() ? "yes" : "no";
            case ROW -> String.valueOf(row + 1);
            case COLUMN -> String.valueOf(column + 1);
            case BATTER -> batterName;
            case PITCHER -> pitcherName;
            case PITCHES -> String.valueOf(a.getPitches());
            case BALLS -> String.valueOf(a.getBalls());
            case STRIKES -> String.valueOf(a.getStrikes());
            case INNING -> String.valueOf(a.getInning());
            case ORDER -> String.valueOf(a.getBattingOrder());
            case RBIS -> String.valueOf(a.getRbis());
        };
    }
}
