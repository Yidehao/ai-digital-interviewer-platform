package org.interviewer.enums;

/**
 * Gender enum
 */
public enum Sex {
    woman(0, "Female"),
    man(1, "Male"),
    secret(2, "Secret");

    public final Integer type;
    public final String value;

    Sex(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
