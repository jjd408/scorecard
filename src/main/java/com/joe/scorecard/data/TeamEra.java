package com.joe.scorecard.data;

/** A period during which a team had one name and city. */
public class TeamEra extends Period {

    /** Foreign key to {@link Team#getId()}. */
    private long teamId;

    private String name;

    private String city;

    public long getTeamId() {
        return teamId;
    }

    public void setTeamId(long teamId) {
        this.teamId = teamId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
