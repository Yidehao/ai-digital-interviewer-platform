package org.interviewer.enums;

/**
 * Delay time enum
 */
public enum DelayTimes {
//    first(1, 5 * 60 * 1000),                // 5 minutes
//    second(2, 15 * 60 * 1000),              // 15 minutes
//    third(3, 30 * 60 * 1000),               // 30 minutes
//    fourth(4, 60 * 60 * 1000),              // 60 minutes
//    fifth(5, 3 * 60 * 60 * 1000),           // 3 hours
//    sixth(6, 6 * 60 * 60 * 1000),           // 6 hours
//    seventh(7, 12 * 60 * 60 * 1000),        // 12 hours
//    eighth(8, 24 * 60 * 60 * 1000),         // 24 hours
//    ninth(9, 2 * 24 * 60 * 60 * 1000);      // 2 days

    first(1, 3 * 1000),
    second(2, 5 * 1000),
    third(3, 8 * 1000),
    fourth(4, 10 * 1000),
    fifth(5, 15 * 1000),
    sixth(6, 20 * 1000),
    seventh(7, 25 * 1000),
    eighth(8, 30 * 1000),
    ninth(9, 40 * 1000);

    public final Integer counts;
    public final Integer times;

    DelayTimes(Integer counts, Integer times) {
        this.counts = counts;
        this.times = times;
    }

    public static Integer getDelayTimes(Integer counts) {
        DelayTimes[] delayTimes = DelayTimes.values();
        for (DelayTimes dt :delayTimes) {
            if (dt.counts == counts) {
                return dt.times;
            }
        }
        // If no match, use default 5 minutes
        return delayTimes[delayTimes.length-1].times;
    }
}
