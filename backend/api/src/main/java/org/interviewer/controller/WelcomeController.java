package org.interviewer.controller;

import org.interviewer.base.BaseInfoProperties;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.entity.Candidate;
import org.interviewer.entity.bo.VerifySMSBO;
import org.interviewer.entity.vo.CandidateVO;
import org.interviewer.service.CandidateService;
import org.interviewer.service.InterviewRecordService;
import org.interviewer.utils.JsonUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * WelcomeController
 **/
@RestController
@RequestMapping("welcome")
public class WelcomeController extends BaseInfoProperties {

    @Resource
    private CandidateService candidateService;

    @Resource
    private InterviewRecordService interviewRecordService;

    /**
     * Get SMS verification code
     * @param mobile
     * @return GraceJSONResult
     */
    @PostMapping("getSMSCode")
    public GraceJSONResult getSMSCode(String mobile) {

        if (StringUtils.isBlank(mobile)) return GraceJSONResult.error();

        // Generate 6-digit verification code locally
        String code = String.format("%06d", (int) ((Math.random() * 9 + 1) * 100000));
        // Print to backend for local/dev use (same flow as SMS: user gets code and enters it)
        System.out.println("[Verification Code] mobile=" + mobile + ", code=" + code);

        // Store verification code in Redis for subsequent interview verification (10 min TTL)
        redis.set(MOBILE_SMSCODE + ":" + mobile, code, 10 * 60);

        return GraceJSONResult.ok();
    }

    /**
     * Verify if user can enter interview process
     * @param verifySMSBO
     * @return GraceJSONResult
     */
    @PostMapping("verify")
    public GraceJSONResult verify(@Validated @RequestBody VerifySMSBO verifySMSBO) {

        String mobile = verifySMSBO.getMobile();
        String code = verifySMSBO.getSmsCode();

        // 1. Get verification code from Redis and verify if it matches
        String redisCode  = redis.get(MOBILE_SMSCODE + ":" + mobile);
        if (StringUtils.isBlank(redisCode) || !redisCode.equalsIgnoreCase(code)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SMS_CODE_ERROR);
        }

        // 2. Query database by mobile to check if user exists and is a candidate
        Candidate candidate = candidateService.queryMobileIsExist(mobile);
        if (candidate == null) {
            // 2.1 If queried user is null, it means the user is not a candidate and cannot enter interview process
            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_INFO_NOT_EXIST_ERROR);
        }

        // 2.2 If candidate exists, check if they have already completed an interview; if so, do not allow login again
        if (interviewRecordService.isCandidateRecordExist(candidate.getId())) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_ALREADY_DID_INTERVIEW_ERROR);
        }

        // 3. Save user token information, save distributed session to Redis (3-hour interview)
        String uToken = UUID.randomUUID().toString();
        redis.set(REDIS_USER_TOKEN + ":" + candidate.getId(), uToken, 3 * 60 * 60);

        CandidateVO candidateVO = new CandidateVO();
        BeanUtils.copyProperties(candidate, candidateVO);
        candidateVO.setUserToken(uToken);
        candidateVO.setCandidateId(candidate.getId());

        // 4. After user enters interview process (after login), delete verification code from Redis
        redis.del(MOBILE_SMSCODE + ":" + mobile);

        // 5. (Optional) Save user information to server Redis, save for 3 hours
        redis.set(REDIS_USER_INFO + ":" + candidate.getId(), JsonUtils.objectToJson(candidateVO), 3 * 60 * 60);

        // 6. Return user information to frontend
        return GraceJSONResult.ok(candidateVO);
    }

}
