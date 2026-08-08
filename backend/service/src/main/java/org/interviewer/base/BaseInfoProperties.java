package org.interviewer.base;

import com.github.pagehelper.PageInfo;
import org.interviewer.enums.JobTitle;
import org.interviewer.utils.PagedGridResult;
import org.interviewer.utils.RedisOperator;
import org.interviewer.utils.RedisOperator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

public class BaseInfoProperties {

    @Resource
    public RedisOperator redis;

    public static final String DEFAULT_USER_FACE = "";

    public static final String SYMBOL_DOT = ".";       // Small dot, meaningless, optional

    public static final String TOKEN_USER_PREFIX = "app";       // User token prefix for app
    public static final String TOKEN_SAAS_PREFIX = "saas";      // User token prefix for enterprise SaaS platform
    public static final String TOKEN_ADMIN_PREFIX = "admin";    // User token prefix for operation management platform

    public static final String COMMON_USER_JSON = "common-user-json";       // Common user

    public static final String APP_USER_JSON = "app-user-json";       // App user
    public static final String SAAS_USER_JSON = "saas-user-json";      // Enterprise SaaS platform user
    public static final String ADMIN_USER_JSON = "admin-user-json";    // Operation management platform user

    public static final Integer COMMON_START_PAGE = 1;
    public static final Integer COMMON_START_PAGE_ZERO = 0;
    public static final Integer COMMON_PAGE_SIZE = 10;

    public static final Integer SYS_PARAMS_PK = 1001;

    public static final String MOBILE_SMSCODE = "mobile:smscode";
    public static final String MOBILE_SMSCODE_RETRY = "retry:smscode";
    public static final String REDIS_USER_TOKEN = "redis_user_token";
    public static final String REDIS_USER_INFO = "redis_user_info";

    public static final String WEBSITE_ANNOUNCEMENT = "website_announcement";

    public static final String REDIS_BLOG_BOOK_LIST = "blog_book_list";
    public static final String REDIS_BLOG_ARTICLE_LIST = "blog_article_list";

    // User submitted review information, if this key exists, it means the next submission is not allowed
    public static final String REDIS_USER_REVIEW_INFO = "redis_user_review_info";
    public static final String REDIS_USER_REVIEW_FACE = "redis_user_review_face";

    // Dashboard review count key
    public static final String DASHBOARD_PENDING_COUNTS = "dashboard_pending_counts";
    public static final String REVIEW_USER = "review_user";
    public static final String REVIEW_COURSE_COMMENT = "review_course_comment";
    public static final String REVIEW_TOPIC = "review_topic";
    public static final String REVIEW_TOPIC_COMMENT = "review_topic_comment";
    public static final String REVIEW_DAILY_BONUS = "review_daily_bonus";

    public static final String REDIS_ADMIN_TOKEN = "redis_admin_token";
    public static final String REDIS_ADMIN_INFO = "redis_admin_info";

    public static final String INDEX_CAROUSEL_LIST = "index_carousel_list";
    public static final String COURSE_DETAIL_INFO = "course_detail_info";
    public static final String COURSE_INDEX_LIST = "course_index_list";
    public static final String COURSE_PAGE_DETAIL = "course_page_detail";

    // User message unread count
    public static final String USER_UNREAD_MSG_COUNTS = "user_unread_msg_counts";

    public static final String SAAS_PLATFORM_LOGIN_TOKEN = "saas_platform_login_token";
    public static final String SAAS_PLATFORM_LOGIN_TOKEN_READ = "saas_platform_login_token_read";

    public static final String REDIS_SAAS_USER_TOKEN = "redis_saas_user_token";
    public static final String REDIS_SAAS_USER_INFO = "redis_saas_user_info";

    public static final String TOP_INDUSTRY_LIST = "top_industry_list";
    public static final String THIRD_INDUSTRY_LIST = "third_industry_list";

    public static final String DATA_DICTIONARY_LIST_TYPECODE = "data_dictionary_list_typecode";

    // All dictionary list corresponding to a dictionary code
    public static final String REDIS_DATA_DICTIONARY_ITEM_LIST = "redis_data_dictionary_item_list";

    // Company information related
    public static final String REDIS_COMPANY_BASE_INFO = "company_base_info";
    public static final String REDIS_COMPANY_MORE_INFO = "company_more_info";
    public static final String REDIS_COMPANY_PHOTOS = "redis_company_photos";
    public static final String REDIS_COMPANY_IS_VIP = "redis_company_is_vip";
    public static final String REDIS_COMPANY_HR_COUNTS = "redis_company_hr_counts";

    // User resume information
    public static final String REDIS_RESUME_INFO = "redis_resume_info";
    public static final String REDIS_MAX_RESUME_REFRESH_COUNTS = "max_resume_refresh_counts";
    public static final String ZK_MAX_RESUME_REFRESH_COUNTS = "max_resume_refresh_counts";
    public static final String CACHE_MAX_RESUME_REFRESH_COUNTS = "max_resume_refresh_counts";
    public static final String USER_ALREADY_REFRESHED_COUNTS = "user_already_refreshed_counts";
    public static final String REDIS_RESUME_EXPECT = "redis_resume_expect";

    public static final String DELAY_ERROR_RETRY_COUNTS = "delay_error_retry_counts";

    public static final String HR_COLLECT_RESUME_COUNTS = "hr_collect_resume_counts";
    public static final String HR_READ_RESUME_RECORD_COUNTS = "hr_read_resume_record_counts";
    public static final String WHO_LOOK_ME_COUNTS = "who_look_me_counts";
    public static final String CAND_FOLLOW_HR_COUNTS = "cand_follow_hr_counts";
    public static final String CAND_COLLECT_JOB_COUNTS = "cand_collect_job_counts";

    // HR interview record count
    public static final String HR_INTERVIEW_RECORD_COUNTS = "hr_interview_record_counts";
    // Candidate interview record count
    public static final String CAND_INTERVIEW_RECORD_COUNTS = "cand_interview_record_counts";

    // Job information
    public static final String REDIS_JOB_DETAIL = "redis_job_detail";
    public static final String HR_ALL_JOB_COUNTS = "hr_all_job_counts";

    public static final String CHAT_MSG_LIST = "chat_msg_list";

    // Article read total count
    public static final String REDIS_ARTICLE_READ_COUNTS = "redis_article_read_counts";
    // Mark user read, relationship with article
    public static final String REDIS_USER_READ_ARTICLE = "redis_user_read_article";

    // Short video comment total count
    public static final String REDIS_VLOG_COMMENT_COUNTS = "redis_vlog_comment_counts";
    // Short video comment like count
    public static final String REDIS_VLOG_COMMENT_LIKED_COUNTS = "redis_vlog_comment_liked_counts";
    // User like comment
    public static final String REDIS_USER_LIKE_COMMENT = "redis_user_like_comment";

    // My follow total count
    public static final String REDIS_MY_FOLLOWS_COUNTS = "redis_my_follows_counts";
    // My fans total count
    public static final String REDIS_MY_FANS_COUNTS = "redis_my_fans_counts";
    // Relationship between blogger and fans, used to determine if they follow each other
    public static final String REDIS_FANS_AND_VLOGGER_RELATIONSHIP = "redis_fans_and_vlogger_relationship";

    // Video and publisher like count
    public static final String REDIS_VLOG_BE_LIKED_COUNTS = "redis_vlog_be_liked_counts";
    public static final String REDIS_VLOGER_BE_LIKED_COUNTS = "redis_vloger_be_liked_counts";

    // Whether user likes/votes video, replaces database relationship, 1: like, 0: dislike (default) redis_user_like_vlog:{userId}:{vlogId}
    public static final String REDIS_USER_LIKE_VLOG = "redis_user_like_vlog";


    // Payment center address - create merchant order
    public static final String PAYMENT_URL_CREATE_MERCHANT_ORDER = "http://hirecompany.t.test.com:9060/payment/createMerchantOrder";		// prod
    public static final String PAYMENT_URL_GET_WXPAY_QRCODE = "http://hirecompany.t.test.com:9060/payment/getWXPayQRCode";		// prod

    // Payment callback notification API interface address after payment (exposed interface request address of the project)
    public static final String PAY_RETURN_URL = "http://test.natappfree.cc/tradeOrder/notifyMerchantOrderPaid";             // dev


//    public Map<String, String> getErrors(BindingResult result) {
//        Map<String, String> map = new HashMap<>();
//        List<FieldError> errorList = result.getFieldErrors();
//        for (FieldError ff : errorList) {
//            // Error corresponding attribute field name
//            String field = ff.getField();
//            // Error information
//            String msg = ff.getDefaultMessage();
//            map.put(field, msg);
//        }
//        return map;
//    }

    public PagedGridResult setterPagedGrid(List<?> list,
                                           Integer page) {
        PageInfo<?> pageList = new PageInfo<>(list);
        PagedGridResult gridResult = new PagedGridResult();
        gridResult.setRows(list);
        gridResult.setPage(page);
        gridResult.setRecords(pageList.getTotal());
        gridResult.setTotal(pageList.getPages());
        return gridResult;
    }

    /**
     * Account needs to be opened to call payment center
     * @return
     */
    public HttpHeaders getHeadersForWxPay() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("userId", "test");
        headers.add("password", "test");
        return headers;
    }

//    public Integer getCountsConvent(String redisCountsKey) {
//        String countsStr = redis.get(redisCountsKey);
//        if (StringUtils.isNotBlank(countsStr)) {
//            return Integer.valueOf(countsStr);
//        }
//        return 0;
//    }
//
//    public Integer getHashCountsConvent(String redisCountsKey, String innerField) {
//        String countsStr = redis.getHashValue(redisCountsKey, innerField);
//        if (StringUtils.isNotBlank(countsStr)) {
//            return Integer.valueOf(countsStr);
//        }
//        return 0;
//    }

    /**
     * Get enum list
     * @return
     */
    public List<String> getAllJobTitles() {
        List<String> jobTitleList = new ArrayList<>();
        for (JobTitle title : JobTitle.values()) {
            jobTitleList.add(title.value);
        }
        return jobTitleList;
    }
}
