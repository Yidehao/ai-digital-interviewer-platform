package org.interviewer.controller;

import org.interviewer.base.BaseInfoProperties;
import org.interviewer.enums.YesOrNo;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.entity.bo.QuestionLibBO;
import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.service.QuestionLibService;
import org.interviewer.utils.PagedGridResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * QuestionLibController
 **/
@RestController
@RequestMapping("questionLib")
public class  QuestionLibController extends BaseInfoProperties {

    @Resource
    private QuestionLibService questionLibService;

    @PostMapping("createOrUpdate")
    public GraceJSONResult createOrUpdate(@RequestBody QuestionLibBO questionLibBO) {
        questionLibService.createOrUpdate(questionLibBO);
        return GraceJSONResult.ok();
    }

    /**
     * Paginated query interview question library list
     * @param aiName
     * @param question
     * @param page
     * @param pageSize
     * @return GraceJSONResult
     */
    @GetMapping("list")
    public GraceJSONResult list(@RequestParam String aiName,
                                @RequestParam String question,
                                @RequestParam(defaultValue = "1", name = "page") Integer page,
                                @RequestParam(defaultValue = "10", name = "pageSize") Integer pageSize) {

        PagedGridResult result = questionLibService.queryList(aiName, question, page, pageSize);
        return GraceJSONResult.ok(result);
    }

    /**
     * Set a specific interview question to display (enable)
     * @param questionLibId
     * @return GraceJSONResult
     */
    @PostMapping("show")
    public GraceJSONResult show(@RequestParam String questionLibId) {
        if (StringUtils.isBlank(questionLibId)) return GraceJSONResult.error();
        questionLibService.setDisplayOrNot(questionLibId, YesOrNo.YES.type);
        return GraceJSONResult.ok();
    }

    /**
     * Set a specific interview question to hide (disable)
     * @param questionLibId
     * @return GraceJSONResult
     */
    @PostMapping("hide")
    public GraceJSONResult hide(@RequestParam String questionLibId) {
        if (StringUtils.isBlank(questionLibId)) return GraceJSONResult.error();
        questionLibService.setDisplayOrNot(questionLibId, YesOrNo.NO.type);
        return GraceJSONResult.ok();
    }

    /**
     * Delete specified interview question
     * @param questionLibId
     * @return GraceJSONResult
     */
    @PostMapping("delete")
    public GraceJSONResult delete(@RequestParam String questionLibId) {
        if (StringUtils.isBlank(questionLibId)) return GraceJSONResult.error();
        questionLibService.delete(questionLibId);
        return GraceJSONResult.ok();
    }

    /**
     * Prepare interview questions, randomly get a certain number of interview questions and return to frontend
     * @param candidateId
     * @return GraceJSONResult
     */
    @GetMapping("prepareQuestion")
    public GraceJSONResult prepareQuestion(@RequestParam String candidateId) {

        // Check if candidate is in session, limit interface from malicious calls
        String candidateInfo = redis.get(REDIS_USER_INFO + ":" + candidateId);
        String userToken = redis.get(REDIS_USER_TOKEN + ":" + candidateId);
        if (StringUtils.isBlank(candidateInfo) || StringUtils.isBlank(userToken)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.USER_INFO_NOT_EXIST_ERROR);
        }

        List<InitQuestionsVO> result = questionLibService.getRandomQuestions(candidateId, 3);
        return GraceJSONResult.ok(result);
    }

}
