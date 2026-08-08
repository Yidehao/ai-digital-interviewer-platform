package org.interviewer.controller;

import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.entity.bo.CandidateBO;
import org.interviewer.entity.bo.JobBO;
import org.interviewer.service.CandidateService;
import org.interviewer.utils.PagedGridResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * CandidateController
 **/
@RestController
@RequestMapping("candidate")
public class CandidateController {

    @Resource
    private CandidateService candidateService;

    /**
     * Create or update candidate information
     * @param candidateBO
     * @return GraceJSONResult
     */
    @PostMapping("createOrUpdate")
    public GraceJSONResult createOrUpdate(@RequestBody CandidateBO candidateBO) {
        candidateService.createOrUpdate(candidateBO);
        return GraceJSONResult.ok();
    }

    /**
     * Search candidate list with conditions
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
        PagedGridResult result = candidateService.queryList(realName, mobile, page, pageSize);
        return GraceJSONResult.ok(result);
    }

    /**
     * Query candidate
     * @param candidateId
     * @return GraceJSONResult
     */
    @GetMapping("detail")
    public GraceJSONResult detail(@RequestParam String candidateId) {
        return GraceJSONResult.ok(candidateService.getDetail(candidateId));
    }

    /**
     * Delete candidate
     * @param candidateId
     * @return GraceJSONResult
     */
    @PostMapping("delete")
    public GraceJSONResult delete(@RequestParam String candidateId) {
        candidateService.delete(candidateId);
        return GraceJSONResult.ok();
    }
}
