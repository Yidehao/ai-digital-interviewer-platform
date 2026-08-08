package org.interviewer.enums;

/**
 * Company review status enum
 */
public enum CompanyReviewStatus {
    /**
     * Review status
    0: Review certification not initiated (not entered review process)
    1: Review certification passed
    2: Review certification failed
    3: Under review (waiting for review)
     */

    NOTHING(0, "Review Certification Not Initiated"),
    SUCCESSFUL(1, "Review Certification Passed"),
    FAILED(2, "Review Certification Failed"),
    REVIEW_ING(3, "Under Review");

    public final Integer type;
    public final String value;

    CompanyReviewStatus(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
