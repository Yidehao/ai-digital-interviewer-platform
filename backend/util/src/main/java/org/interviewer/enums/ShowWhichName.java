package org.interviewer.enums;

/**
 * Display user name enum
 */
public enum ShowWhichName {
    realname(1, "Real Name"),
    nickname(2, "Nickname");

    public final Integer type;
    public final String value;

    ShowWhichName(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
