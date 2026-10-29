package com.joe.scorecard.data;

/** A single plate appearance by a batter. */
public class Atbat {

    /** Primary key. */
    private long id;

    /** Foreign key to {@link Game#getId()}. */
    private long gameId;

    /**
     * Foreign key to {@link Team#getId()}: the team at bat. It decides which
     * team's grid the at-bat is drawn on, even before the batter is picked.
     */
    private long teamId;

    /** Foreign key to {@link Player#getId()}. */
    private long batterId;

    /** Foreign key to {@link Player#getId()}. */
    private long pitcherId;

    private int pitches;

    private int balls;

    private int strikes;

    private int inning;

    private int battingOrder;

    private int rbis;

    /**
     * The row of the scorecard grid cell the at-bat is drawn in. Row and
     * column are only the at-bat's position on the card; they are separate
     * from {@link #inning} and {@link #battingOrder}, which the user sets.
     * That way, when a team bats around, the extra at-bats can go in the next
     * column while still counting toward the same inning.
     */
    private int row;

    /** The column of the scorecard grid cell the at-bat is drawn in. See {@link #row}. */
    private int column;

    private boolean active;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getGameId() {
        return gameId;
    }

    public void setGameId(long gameId) {
        this.gameId = gameId;
    }

    public long getTeamId() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId = teamId;
    }

    public long getBatterId() {
        return batterId;
    }

    public void setBatterId(long batterId) {
        this.batterId = batterId;
    }

    public long getPitcherId() {
        return pitcherId;
    }

    public void setPitcherId(long pitcherId) {
        this.pitcherId = pitcherId;
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

    public int getInning() {
        return inning;
    }

    public void setInning(int inning) {
        this.inning = inning;
    }

    public int getBattingOrder() {
        return battingOrder;
    }

    public void setBattingOrder(int battingOrder) {
        this.battingOrder = battingOrder;
    }

    public int getRbis() {
        return rbis;
    }

    public void setRbis(int rbis) {
        this.rbis = rbis;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
