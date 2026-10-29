package com.joe.scorecard.data;

/** A period during which a player had one team and number. */
public class PlayerStint extends Period {

    /** Foreign key to {@link Player#getId()}. */
    private long playerId;

    /** Foreign key to {@link Team#getId()}. */
    private long teamId;

    private int number;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getTeamId() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId = teamId;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
