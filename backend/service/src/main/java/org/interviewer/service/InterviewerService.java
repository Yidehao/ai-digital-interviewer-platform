package org.interviewer.service;

import org.interviewer.entity.Interviewer;
import org.interviewer.entity.bo.InterviewerBO;

import java.util.List;

/**
 * InterviewerService
 **/
public interface InterviewerService {

    /**
     * Create or update interviewer information
     * @param interviewerBO
     */
    public void createOrUpdate(InterviewerBO interviewerBO);

    /**
     * Query all interviewer data list
     * @param
     * @return List<Interviewer>
     */
    public List<Interviewer> queryAll();

    /**
     * Delete digital interviewer
     * @param interviewerId
     */
    public void delete(String interviewerId);
}