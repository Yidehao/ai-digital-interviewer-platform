package org.interviewer.enums;

/**
 * VIP member type enum, 0: Common user; 1: Lifetime VIP; 2: Planet member
 */
public enum VIPType {

    COMMON_USER(0, "Common User"),
    LIFE_MEMBER(1, "Lifetime VIP"),
    PLANET_MEMBER(2, "Planet Member");

    public final Integer type;
    public final String value;

    VIPType(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
