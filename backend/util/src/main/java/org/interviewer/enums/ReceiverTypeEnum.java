package org.interviewer.enums;

/**
 * Message receiver type enum
 */
public enum ReceiverTypeEnum {
    ALL(1, "All Users"),
    ONE(2, "Specified User");

    public final Integer type;
    public final String value;

    ReceiverTypeEnum(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
