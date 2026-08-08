package org.interviewer.service;

import org.interviewer.entity.Candidate;
import org.interviewer.entity.bo.CandidateBO;
import org.interviewer.utils.PagedGridResult;

public interface CandidateService {

    /**
     * Create or update candidate information
     * @param candidateBO
     */
    public void createOrUpdate(CandidateBO candidateBO);

    /**
     * Query candidate list
     * @param realName
     * @param mobile
     * @param page
     * @param pageSize
     * @return PagedGridResult
     */
    public PagedGridResult queryList(String realName, String mobile, Integer page, Integer pageSize);

    /**
     * Get candidate information details
     * @param candidateId
     */
    public Candidate getDetail(String candidateId);

    /**
     * Delete candidate
     * @param candidateId
     */
    public void delete(String candidateId);

    /**
     * Check if user exists, return user information if exists, return null if not exists
     * @param mobile
     * @return Candidate
     */
    public Candidate queryMobileIsExist(String mobile);
}
