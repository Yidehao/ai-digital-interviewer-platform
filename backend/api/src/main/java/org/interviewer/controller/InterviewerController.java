package org.interviewer.controller;

import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.entity.Interviewer;
import org.interviewer.entity.bo.InterviewerBO;
import org.interviewer.service.InterviewerService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * InterviewerController
 **/
@RestController
@RequestMapping("interviewer")
public class InterviewerController {

    @Resource
    private InterviewerService interviewerService;

    /**
     * Create or update digital interviewer information
     * @param interviewerBO
     * @return GraceJSONResult
     */
    @PostMapping("createOrUpdate")
    public GraceJSONResult createOrUpdate(@Valid @RequestBody InterviewerBO interviewerBO) {
        interviewerService.createOrUpdate(interviewerBO);
        return GraceJSONResult.ok();
    }

    /**
     * Query all digital interviewer list
     * @param
     * @return GraceJSONResult
     */
    @GetMapping("list")
    public GraceJSONResult list() {
        return GraceJSONResult.ok(interviewerService.queryAll());
    }

//    /**
//     * Delete digital interviewer
//     * @param interviewerId
//     * @return GraceJSONResult
//     */
//    @DeleteMapping("delete")
//    public GraceJSONResult delete(@RequestParam String interviewerId) {
//        interviewerService.delete(interviewerId);
//        return GraceJSONResult.ok();
//    }
}