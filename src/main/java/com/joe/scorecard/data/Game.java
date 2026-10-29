package com.joe.scorecard.data;

import java.time.LocalDate;

/** A game between two teams. */
public class Game {

    /** Primary key. */
    private long id;

    /** Foreign key to {@link Team#getId()}. */
    private long homeTeamId;

    /** Foreign key to {@link Team#getId()}. */
    private long awayTeamId;

    private LocalDate date;

    private int gameNumber;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getHomeTeamId() {
        return homeTeamId;
    }

    public void setHomeTeamId(long homeTeamId) {
        this.homeTeamId = homeTeamId;
    }

    public long getAwayTeamId() {
        return awayTeamId;
    }

    public void setAwayTeamId(long awayTeamId) {
        this.awayTeamId = awayTeamId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getGameNumber() {
        return gameNumber;
    }

    public void setGameNumber(int gameNumber) {
        this.gameNumber = gameNumber;
    }
}
