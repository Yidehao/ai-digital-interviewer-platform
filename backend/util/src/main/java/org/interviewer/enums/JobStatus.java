package org.interviewer.enums;

/**
 * Job status enum
 */
public enum JobStatus {
    OPEN(1, "Recruiting"),
    CLOSE(2, "Closed"),
    DELETE(3, "Violation Deleted");

    public final Integer type;
    public final String value;

    JobStatus(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
