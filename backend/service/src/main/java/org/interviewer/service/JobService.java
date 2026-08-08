package org.interviewer.service;

import org.interviewer.entity.Job;
import org.interviewer.entity.bo.JobBO;
import org.interviewer.utils.PagedGridResult;

import java.util.HashMap;
import java.util.List;

public interface JobService {

    /**
     * Create or update job information
     * 
     * @param jobBO
     */
    public void createOrUpdate(JobBO jobBO);

    /**
     * Paginated query job list
     * 
     * @param page
     * @param pageSize
     * @return PagedGridResult
     */
    public PagedGridResult queryList(Integer page, Integer pageSize);

    /**
     * Query job details
     * 
     * @param id
     * @return Job
     */
    public Job getDetail(String id);

    /**
     * Delete job details
     * 
     * @param id
     */
    public void delete(String id);

    /**
     * Check if all jobs contain a specific interviewer
     * 
     * @param InterviewerId
     * @return boolean
     */
    public boolean isJobContainInterviewer(String InterviewerId);

    /**
     * Get list of all job position names
     * 
     * @param
     * @return List<HashMap<String,String>>
     */
    public List<HashMap<String, String>> nameList();
}
