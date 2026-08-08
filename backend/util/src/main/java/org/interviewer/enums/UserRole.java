package org.interviewer.enums;

/**
 * User role enum, 1: Admin, 2: Common user, 3: Forbidden access, banned
 */
public enum UserRole {

    ADMIN(1, "Admin"),
    COMMON_USER(2, "Common User"),
    FORBIDDEN(0, "Forbidden Access, Banned");

    public final Integer type;
    public final String value;

    UserRole(Integer type, String value) {
        this.type = type;
        this.value = value;
    }
}
