package org.interviewer.enums;

/**
 * User information review status enum
 */
public enum UserInfoReviewStatus {
    PENDING(0, "Pending Review"),
    PASS(1, "Review Passed"),
    FAILED(2, "Review Failed");

    public final Integer type;
    public final String value;

    UserInfoReviewStatus(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
