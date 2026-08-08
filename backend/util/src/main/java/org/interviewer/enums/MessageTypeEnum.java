package org.interviewer.enums;

/**
 * Message type enum
 */
public enum MessageTypeEnum {
    SYS_MSG(1, "System Message"),
    ANNOUNCEMENT(2, "Website Announcement");

    public final Integer type;
    public final String value;

    MessageTypeEnum(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
