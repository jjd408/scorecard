package com.joe.scorecard.data;

/**
 * A play that occurs during an at-bat. An at-bat's plays happen in id order:
 * the first is usually the batter's own result, and the rest are the batter
 * moving around the bases as a runner afterward.
 */
public class Play {

    /** Primary key. */
    private long id;

    /** Foreign key to {@link Atbat#getId()}. */
    private long atbatId;

    private int pitches;

    private int balls;

    private int strikes;

    /** Foreign key to {@link Abbrev#getId()}. */
    private long abbrevId;

    private int base;

    private int out;

    private String comment;

    /**
     * Where the ball was played, by the standard scoring number of the
     * fielder's position: 1 pitcher, 2 catcher, 3 first base, 4 second base,
     * 5 third base, 6 shortstop, 7 left field, 8 center field, 9 right field.
     * 0 if none.
     */
    private int field;

    /**
     * The batting-order spot (1 to 9) of the batter whose play moved this
     * runner up, such as the batter who hit the single that sent them to
     * third. 0 if none, such as for a stolen base or the batter's own play.
     */
    private int advancedBy;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAtbatId() {
        return atbatId;
    }

    public void setAtbatId(long atbatId) {
        this.atbatId = atbatId;
    }

    public int getPitches() {
        return pitches;
    }

    public void setPitches(int pitches) {
        this.pitches = pitches;
    }

    public int getBalls() {
        return balls;
    }

    public void setBalls(int balls) {
        this.balls = balls;
    }

    public int getStrikes() {
        return strikes;
    }

    public void setStrikes(int strikes) {
        this.strikes = strikes;
    }

    public long getAbbrevId() {
        return abbrevId;
    }

    public void setAbbrevId(long abbrevId) {
        this.abbrevId = abbrevId;
    }

    public int getBase() {
        return base;
    }

    public void setBase(int base) {
        this.base = base;
    }

    public int getOut() {
        return out;
    }

    public void setOut(int out) {
        this.out = out;
    }

    public int getField() {
        return field;
    }

    public void setField(int field) {
        this.field = field;
    }

    public int getAdvancedBy() {
        return advancedBy;
    }

    public void setAdvancedBy(int advancedBy) {
        this.advancedBy = advancedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
