package org.interviewer.enums;

/**
 * Yes or No enum
 */
public enum YesOrNo {
    NO(0, "No"),
    YES(1, "Yes");

    public final Integer type;
    public final String value;

    YesOrNo(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
