package org.interviewer.enums;

/**
 * User information review type enum
 */
public enum UserInfoReviewType {
    USER_FACE(1, "User Avatar", "face"),
    USER_INFO(2, "User Information", "info");

    public final Integer type;
    public final String value;
    public final String words;

    UserInfoReviewType(Integer type, String value, String words) {
        this.type = type;
        this.value = value;
        this.words = words;
    }
}
