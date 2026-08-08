package org.interviewer.enums;

/**
 * User information modification type enum
 */
public enum UserInfoModifyType {
    NICKNAME(1, "Nickname"),
    IMOOCNUM(2, "User Number"),
    SEX(3, "Gender"),
    BIRTHDAY(4, "Birthday"),
    LOCATION(5, "Location"),
    DESC(6, "Description");

    public final Integer type;
    public final String value;

    UserInfoModifyType(Integer type, String value) {
        this.type = type;
        this.value = value;
    }

//    public static void checkUserInfoTypeIsRight(Integer type) {
//        if (type != UserInfoModifyType.NICKNAME.type &&
//                type != UserInfoModifyType.IMOOCNUM.type &&
//                type != UserInfoModifyType.SEX.type &&
//                type != UserInfoModifyType.BIRTHDAY.type &&
//                type != UserInfoModifyType.LOCATION.type &&
//                type != UserInfoModifyType.DESC.type) {
//            GraceException.display(ResponseStatusEnum.USER_INFO_UPDATED_ERROR);
//        }
//    }
}
