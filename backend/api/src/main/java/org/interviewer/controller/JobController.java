package org.interviewer.controller;

import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.entity.bo.JobBO;
import org.interviewer.service.JobService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * JobController
 **/
@RestController
@RequestMapping("job")
public class JobController {

    @Resource
    private JobService jobService;

    /**
     * Create or update job information
     * @param jobBO
     * @return GraceJSONResult
     */
    @PostMapping("createOrUpdate")
    public GraceJSONResult createOrUpdate(@RequestBody JobBO jobBO) {
        jobService.createOrUpdate(jobBO);
        return GraceJSONResult.ok();
    }

    /**
     * Paginated query job information list
     * @param page
     * @param pageSize
     * @return GraceJSONResult
     */
    @GetMapping("list")
    public GraceJSONResult list(@RequestParam(defaultValue = "1", name = "page") Integer page,
                                @RequestParam(defaultValue = "10", name = "pageSize") Integer pageSize) {
        return GraceJSONResult.ok(jobService.queryList(page, pageSize));
    }

    /**
     * Query job details
     * @param jobId
     * @return GraceJSONResult
     */
    @GetMapping("detail")
    public GraceJSONResult detail(String jobId) {
        return GraceJSONResult.ok(jobService.getDetail(jobId));
    }

    /**
     * Delete job details
     * @param jobId
     * @return GraceJSONResult
     */
    @PostMapping("delete")
    public GraceJSONResult delete(String jobId) {
        jobService.delete(jobId);
        return GraceJSONResult.ok();
    }

    /**
     * Query and get all job positions list containing names
     * @param
     * @return GraceJSONResult
     */
    @GetMapping("nameList")
    public GraceJSONResult nameList() {
        return GraceJSONResult.ok(jobService.nameList());
    }

}
