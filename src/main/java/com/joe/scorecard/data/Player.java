package com.joe.scorecard.data;

import java.time.LocalDate;

/**
 * A player on a team. A player's team and number can change over time (see
 * {@link PlayerStint}); {@link PlayerDao} fills in the ones in effect on the
 * date the player was loaded for.
 */
public class Player {

    /** Primary key. */
    private long id;

    private String name;

    private int number;

    /** Foreign key to {@link Team#getId()}. */
    private long teamId;

    /** The date the player was removed, or null if they are current. */
    private LocalDate removed;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public long getTeamId() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId = teamId;
    }

    public LocalDate getRemoved() {
        return removed;
    }

    public void setRemoved(LocalDate removed) {
        this.removed = removed;
    }
}
