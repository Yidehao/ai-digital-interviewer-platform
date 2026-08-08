package org.interviewer.enums;

public enum AggsUsersType {
    USER(1, "New Registered Common User"),
    HR(2, "Successfully Settled HR User"),
    COMPANY(3, "Successfully Reviewed Company User"),
    ENTRY(4, "Successfully Hired User"),
    SEND(5, "User Who Sent Resume");

    public final Integer type;
    public final String value;

    AggsUsersType(Integer type, String value) {
        this.type = type;
        this.value = value;
    }

}
