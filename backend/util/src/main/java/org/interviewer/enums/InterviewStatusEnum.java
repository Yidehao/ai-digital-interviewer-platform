package org.interviewer.enums;

/**
 * Interview invitation status enum
 *          1: Waiting for candidate to accept interview
 *          2: Candidate has accepted interview
 *          3: Candidate has refused interview
 *          4: HR has cancelled interview
 *          5: Interview passed
 */
public enum InterviewStatusEnum {
    WAITING(1, "Waiting for candidate to accept interview"),
    ACCEPT(2, "Candidate has accepted interview"),
    REFUSE(3, "Candidate has refused interview"),
    CANCEL(4, "HR has cancelled interview"),
    SUCCESS(5, "Interview passed");     // This status is unused, the platform boundary is only for HR to find job seekers for interviews, the situation after the interview is irrelevant, and it belongs to the internal recruitment management of the enterprise

    public final Integer type;
    public final String value;

    InterviewStatusEnum(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
