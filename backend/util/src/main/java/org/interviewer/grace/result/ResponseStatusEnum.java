package org.interviewer.grace.result;

/**
 * Response result enum, used to provide GraceJSONResult to return to frontend
 * This enum class contains many different status codes for use, can be customized
 * Facilitates more elegant management of status codes, clear at a glance
 */
public enum ResponseStatusEnum {

    SUCCESS(200, true, "Operation successful!"),
    FAILED(500, false, "Operation failed!"),

    BO_FAILED(599, false, "Form data validation failed!"),

    // 50x
    UN_LOGIN(501,false,"Please login before continuing!"),
    TICKET_INVALID(502,false,"Session expired, please login again!"),
    HR_TICKET_INVALID(5021,false,"Mobile session expired, please login again!"),
    NO_AUTH(503,false,"Insufficient permissions, cannot continue!"),
    MOBILE_ERROR(504,false,"SMS sending failed, please try again later!"),
    SMS_NEED_WAIT_ERROR(505,false,"SMS sent too fast~ please try again later!"),
    SMS_CODE_ERROR(506,false,"Verification code expired or does not match, please try again later!"),
    USER_FROZEN(507,false,"User has been frozen, please contact administrator!"),
    USER_UPDATE_ERROR(508,false,"User information update failed, please contact administrator!"),
    USER_INACTIVE_ERROR(509,false,"Please go to [Account Settings] to modify information and activate before continuing!"),
    USER_INFO_UPDATED_ERROR(5091,false,"User information modification failed!"),
    USER_INFO_UPDATED_NICKNAME_EXIST_ERROR(5092,false,"Nickname already exists!"),
    USER_INFO_NOT_EXIST_ERROR(5093,false,"You have no interview invitation!"),
    USER_ALREADY_DID_INTERVIEW_ERROR(5097,false,"You have already been interviewed, please wait for notification!"),
    USER_INFO_WAITING_REVIEW_ERROR(5095,false,"User information waiting for review, please do not submit again!"),
    USER_FACE_WAITING_REVIEW_ERROR(5096,false,"User avatar waiting for review, please do not upload again!"),
    FILE_UPLOAD_NULL_ERROR(510,false,"File cannot be empty, please select a file before uploading!"),
    FILE_UPLOAD_FAILD(511,false,"File upload failed!"),
    FILE_FORMATTER_FAILD(512,false,"File image format not supported!"),
    FILE_MAX_SIZE_500KB_ERROR(5131,false,"Only supports file uploads below 500KB!"),
    FILE_MAX_SIZE_2MB_ERROR(5132,false,"Only supports file uploads below 2MB!"),
    FILE_MAX_SIZE_8MB_ERROR(5132,false,"Trial version only supports file uploads below 8MB!"),
    FILE_MAX_SIZE_100MB_ERROR(5132,false,"Only supports file uploads below 100MB!"),
    FILE_NOT_EXIST_ERROR(514,false,"The file you are viewing does not exist!"),
    USER_STATUS_ERROR(515,false,"User status parameter error!"),
    USER_NOT_EXIST_ERROR(516,false,"User does not exist!"),
    USER_PARAMS_ERROR(517,false,"User request parameter error!"),
    USER_REGISTER_ERROR(518,false,"User registration failed, please try again!"),
    USER_FORBIDDEN_ERROR(519,false,"User access forbidden, please contact administrator!"),

    // Custom system level exceptions 54x
    SYSTEM_INDEX_OUT_OF_BOUNDS(541, false, "System error, array index out of bounds!"),
    SYSTEM_ARITHMETIC_BY_ZERO(542, false, "System error, cannot divide by zero!"),
    SYSTEM_NULL_POINTER(543, false, "System error, null pointer!"),
    SYSTEM_NUMBER_FORMAT(544, false, "System error, number conversion exception!"),
    SYSTEM_PARSE(545, false, "System error, parsing exception!"),
    SYSTEM_IO(546, false, "System error, IO input/output exception!"),
    SYSTEM_FILE_NOT_FOUND(547, false, "System error, file not found!"),
    SYSTEM_CLASS_CAST(548, false, "System error, type cast error!"),
    SYSTEM_PARSER_ERROR(549, false, "System error, parsing error!"),
    SYSTEM_DATE_PARSER_ERROR(550, false, "System error, date parsing error!"),
    SYSTEM_NO_EXPIRE_ERROR(552, false, "System error, missing expiration time!"),

    HTTP_URL_CONNECT_ERROR(551, false, "Target address cannot be requested!"),

    CAN_NOT_DELETE_INTERVIEWER(571, false, "Current digital interviewer is dependent on question library or job, cannot delete!"),
    CAN_NOT_DELETE_JOB(572, false, "Current job is associated with candidates, cannot delete!"),

    // Admin management system 56x
    ADMIN_USERNAME_NULL_ERROR(561, false, "Administrator login name cannot be empty!"),
    ADMIN_USERNAME_EXIST_ERROR(562, false, "Administrator account name already exists!"),
    ADMIN_NAME_NULL_ERROR(563, false, "Administrator responsible person cannot be empty!"),
    ADMIN_PASSWORD_ERROR(564, false, "Password cannot be empty or two inputs do not match!"),
    ADMIN_CREATE_ERROR(565, false, "Add administrator failed!"),
    ADMIN_PASSWORD_NULL_ERROR(566, false, "Password cannot be empty!"),
    ADMIN_LOGIN_ERROR(567, false, "Administrator does not exist or password is incorrect!"),
    ADMIN_FACE_NULL_ERROR(568, false, "Face information cannot be empty!"),
    ADMIN_FACE_LOGIN_ERROR(569, false, "Face recognition failed, please try again!"),
    ADMIN_DELETE_ERROR(5691, false, "Delete administrator failed!"),
    CATEGORY_EXIST_ERROR(570, false, "Article category already exists, please use another category name!"),

    // Media center related errors 58x
    ARTICLE_COVER_NOT_EXIST_ERROR(580, false, "Article cover does not exist, please select one!"),
    ARTICLE_CATEGORY_NOT_EXIST_ERROR(581, false, "Please select the correct article field!"),
    ARTICLE_CREATE_ERROR(582, false, "Create article failed, please try again or contact administrator!"),
    ARTICLE_QUERY_PARAMS_ERROR(583, false, "Article list query parameter error!"),
    ARTICLE_DELETE_ERROR(584, false, "Article deletion failed!"),
    ARTICLE_WITHDRAW_ERROR(585, false, "Article withdrawal failed!"),
    ARTICLE_REVIEW_ERROR(585, false, "Article review error!"),
    ARTICLE_ALREADY_READ_ERROR(586, false, "Article duplicate read!"),

    COMPANY_INFO_UPDATED_ERROR(5151,false,"Company information modification failed!"),
    COMPANY_INFO_UPDATED_NO_AUTH_ERROR(5151,false,"Current user does not have permission to modify company information!"),
    COMPANY_IS_NOT_VIP_ERROR(5152,false,"Company is not VIP or VIP privileges have expired, please go to company backend to recharge and renew!"),

    // Face recognition error codes
    FACE_VERIFY_TYPE_ERROR(600, false, "Face comparison verification type is incorrect!"),
    FACE_VERIFY_LOGIN_ERROR(601, false, "Face login failed!"),

    // System error, unexpected error 555
    SYSTEM_ERROR(555, false, "System busy, please try again later!"),
    SYSTEM_OPERATION_ERROR(556, false, "Operation failed, please try again or contact administrator"),
    SYSTEM_RESPONSE_NO_INFO(557, false, ""),
    SYSTEM_ERROR_GLOBAL(558, false, "Global degradation: System busy, please try again later!"),
    SYSTEM_ERROR_FEIGN(559, false, "Client Feign degradation: System busy, please try again later!"),
    SYSTEM_ERROR_ZUUL(560, false, "Request system too busy, please try again later!"),
    SYSTEM_PARAMS_SETTINGS_ERROR(5611, false, "Parameter settings are not standardized!"),
    ZOOKEEPER_BAD_VERSION_ERROR(5612, false, "Data is outdated, please refresh page and try again!"),
    SYSTEM_ERROR_BLACK_IP(5621, false, "Request too frequent, please try again later!"),
    SYSTEM_SMS_FALLBACK_ERROR(5587, false, "SMS service busy, please try again later!"),
    SYS_DATA_ERROR(5588, false, "System parameter is empty, please check system parameter table sys_params!"),
    SYSTEM_ERROR_NOT_BLANK(5599, false, "System error, parameter cannot be empty!"),

    DATA_DICT_EXIST_ERROR(5631, false, "Data dictionary already exists, cannot add or modify repeatedly!"),
    DATA_DICT_DELETE_ERROR(5632, false, "Delete data dictionary failed!"),

    REPORT_RECORD_EXIST_ERROR(5721, false, "Please do not report repeatedly~!"),

    RESUME_MAX_LIMIT_ERROR(5711, false, "Today's resume refresh count has reached the limit!"),

    JWT_SIGNATURE_ERROR(5555, false, "User verification failed, please login again!"),
    JWT_EXPIRE_ERROR(5556, false, "Login validity period has expired, please login again!"),

    SENTINEL_BLOCK_FLOW_LIMIT_ERROR(5801, false, "System access busy, please try again later!"),

    // Payment error related codes
    PAYMENT_USER_INFO_ERROR(5901, false, "User id or password is incorrect!"),
    PAYMENT_ACCOUT_EXPIRE_ERROR(5902, false, "This account authorization access date has expired!"),
    PAYMENT_HEADERS_ERROR(5903, false, "Please carry user id and password required by payment center in header!"),
    PAYMENT_ORDER_CREATE_ERROR(5904, false, "Payment center order creation failed, please contact administrator!"),

    // Admin related error codes
    ADMIN_NOT_EXIST(5101, false, "Administrator does not exist!");

    // Response business status
    private Integer status;
    // Whether the call is successful
    private Boolean success;
    // Response message, can be success or failure message
    private String msg;

    ResponseStatusEnum(Integer status, Boolean success, String msg) {
        this.status = status;
        this.success = success;
        this.msg = msg;
    }

    public Integer status() {
        return status;
    }
    public Boolean success() {
        return success;
    }
    public String msg() {
        return msg;
    }
}