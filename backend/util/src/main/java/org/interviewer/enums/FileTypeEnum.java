package org.interviewer.enums;

/**
 * File type enum
 */
public enum FileTypeEnum {
    BGIMG(1, "User Background Image"),
    FACE(2, "User Avatar");

    public final Integer type;
    public final String value;

    FileTypeEnum(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
