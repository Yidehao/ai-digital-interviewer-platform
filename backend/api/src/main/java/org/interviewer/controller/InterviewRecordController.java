package org.interviewer.controller;

import org.interviewer.OllamaTask;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.entity.bo.SubmitAnswerBO;
import org.interviewer.service.InterviewRecordService;
import org.interviewer.utils.PagedGridResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * InterviewRecordController
 **/
@RestController
@RequestMapping("interviewRecord")
public class InterviewRecordController {

    @Resource
    private OllamaTask ollamaTask;

    @Resource
    private InterviewRecordService interviewRecordService;

    /**
     * Submit interview answer content for AI analysis
     * @param submitAnswerBO
     * @return GraceJSONResult
     */
    @PostMapping("collect")
    public GraceJSONResult collect(@RequestBody SubmitAnswerBO submitAnswerBO) {

        ollamaTask.display(submitAnswerBO);

        return GraceJSONResult.ok();
    }

    /**
     * Paginated query with conditions to search interview record list
     * @param realName
     * @param mobile
     * @param page
     * @param pageSize
     * @return GraceJSONResult
     */
    @GetMapping("list")
    public GraceJSONResult list(@RequestParam String realName,
                                @RequestParam String mobile,
                                @RequestParam(defaultValue = "1", name = "page") Integer page,
                                @RequestParam(defaultValue = "10", name = "pageSize") Integer pageSize) {
        PagedGridResult result = interviewRecordService.queryList(realName, mobile, page, pageSize);
        return GraceJSONResult.ok(result);
    }

}
