package com.joe.scorecard.data;

/** A scorekeeping abbreviation, such as K or 6-3. */
public class Abbrev {

    /** Primary key. */
    private long id;

    private String abbrev;

    private String description;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAbbrev() {
        return abbrev;
    }

    public void setAbbrev(String abbrev) {
        this.abbrev = abbrev;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
