package com.joe.scorecard.data;

import java.time.LocalDate;
import java.util.List;

/**
 * A stretch of time during which some details of a team or player held. It
 * runs from {@code startDate} up to, but not including, {@code endDate}.
 */
public abstract class Period {

    /** Primary key. */
    private long id;

    /** The first day of the period, or null if it goes back to the beginning. */
    private LocalDate startDate;

    /** The day the period ended, or null if it is current. */
    private LocalDate endDate;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    /** Returns true if {@code date} falls within this period. */
    public boolean covers(LocalDate date) {
        return (startDate == null || !startDate.isAfter(date))
                && (endDate == null || endDate.isAfter(date));
    }

    /**
     * Picks the period to use for {@code date} from a history sorted oldest
     * first: the one covering it, else the last one before it, else the first
     * one. A null date picks the latest. Returns null for an empty history.
     */
    public static <P extends Period> P pick(List<P> history, LocalDate date) {
        if (history.isEmpty()) {
            return null;
        }
        if (date == null) {
            return history.get(history.size() - 1);
        }
        P before = history.get(0);
        for (P p : history) {
            if (p.covers(date)) {
                return p;
            }
            if (p.getStartDate() == null || !p.getStartDate().isAfter(date)) {
                before = p;
            }
        }
        return before;
    }
}
