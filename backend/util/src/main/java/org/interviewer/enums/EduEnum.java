package org.interviewer.enums;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Education level enum
 */
public enum EduEnum {
    JUNIOR("Junior High School",1),
    TECHNICAL("Technical School", 2),
    HIGH("High School", 3),
    JUNIOR_COLLEGE("Associate Degree", 4),
    UNDER_GRADUATE("Bachelor's Degree", 5),
    MASTER("Master's Degree", 6),
    MBA("MBA/EMBA", 7),
    DOCTOR("Doctorate", 8);

    public final String edu;
    public final Integer index;

    EduEnum(String edu, Integer index) {
        this.edu = edu;
        this.index = index;
    }

    // Get education list based on index, e.g., index=5 is Bachelor's Degree, then all education levels higher than Bachelor's can be used as query conditions, because Doctorate is definitely higher than Bachelor's
    public static List getEduList(Integer index) {
        if (index == 0) {
            return null;
        }

        List<String> eduList = new ArrayList<>();

        EduEnum[] eduEnum = EduEnum.values();
        for (EduEnum e : eduEnum) {
            if (e.index >= index) {
                eduList.add(e.edu);
            }
        }

        return eduList;
    }

    public static Integer getEduIndex(String eduStr) {
        if (StringUtils.isBlank(eduStr)) {
            return 0;
        }

        EduEnum[] eduEnum = EduEnum.values();
        for (EduEnum e : eduEnum) {
            if (e.edu.equalsIgnoreCase(eduStr)) {
                return e.index;
            }
        }
        // If no match, use default 0, 0 means none, SQL query does not use as condition
        return 0;
    }
}
