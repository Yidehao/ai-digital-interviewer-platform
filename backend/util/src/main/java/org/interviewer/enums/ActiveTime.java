package org.interviewer.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * User resume activity enum
 */
public enum ActiveTime {
    just("just",3 * 60 * 60),                        // 3 hours ago
    today("today", 24 * 60 * 60),                    // 1 day ago, 24 hours ago
    threeDays("threeDays", 3 * 24 * 60 * 60),        // 3 days ago
    thisWeek("thisWeek", 7 * 24 * 60 * 60),          // 7 days ago
    thisMonth("thisMonth", 30 * 24 * 60 * 60);       // 30 days ago

    public final String active;
    public final Integer times;

    ActiveTime(String active, Integer times) {
        this.active = active;
        this.times = times;
    }

    public static Integer getActiveTimes(String active) {
        if (StringUtils.isBlank(active)) {
            return 0;
        }

        ActiveTime[] times = ActiveTime.values();
        for (ActiveTime at :times) {
            if (at.active.equalsIgnoreCase(active)) {
                return at.times;
            }
        }
        // If no match, use default thisMonth
        return times[times.length-1].times;
    }
}
