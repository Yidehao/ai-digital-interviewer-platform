package org.interviewer.enums;

/**
 * Report handling status enum
 * Handling status: 0: Pending, 1: Processed, 2: Ignored, no need to process
 */
public enum DealStatus {
    WAITING(0, "Pending"),
    DONE(1, "Processed"),
    IGNORE(2, "Ignored, no need to process");

    public final Integer type;
    public final String value;

    DealStatus(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
