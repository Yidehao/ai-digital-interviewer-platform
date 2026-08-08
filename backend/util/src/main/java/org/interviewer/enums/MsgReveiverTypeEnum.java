package org.interviewer.enums;

/**
 * Message receiver type enum
 */
public enum MsgReveiverTypeEnum {

    HR(1, "HR"),
    CANDIDATE(2, "Job Seeker");

    public final Integer type;
    public final String value;

    MsgReveiverTypeEnum(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
