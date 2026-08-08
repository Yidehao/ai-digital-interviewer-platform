package org.interviewer.enums;

public enum JobTitle {

    JUNIOR_ENGINEER(1, "Junior Engineer"),
    SENIOR_ENGINEER(2, "Senior Engineer"),
    JAVA_ENGINEER(3, "Java Engineer"),
    FULL_STACK_ENGINEER(4, "Full Stack Engineer"),
    FRONTEND_ENGINEER(5, "Frontend Engineer"),
    BACKEND_ENGINEER(6, "Backend Engineer"),
    GAME_ENGINEER(7, "Game Engineer"),
    TEST_ENGINEER(8, "Test Engineer"),
    NET_ENGINEER(9, "Network Engineer"),
    OPERATION_MAINTENANCE_ENGINEER(10, "DevOps Engineer"),
    SYSTEM_ENGINEER(11, "System Engineer"),
    BIG_DATA_ENGINEER(12, "Big Data Engineer"),
    DBA(13, "DBA"),
    PRODUCT_MANAGER(14, "Product Manager"),
    PROJECT_MANAGER(15, "Project Manager"),
    ARCHITECT(16, "Architect"),
    TECHNICAL_MANAGER(17, "Technical Manager"),
    TECHNICAL_DIRECTOR(18, "Technical Director");

    public final Integer type;
    public final String value;

    JobTitle(Integer type, String value) {
        this.type = type;
        this.value = value;
    }

}
