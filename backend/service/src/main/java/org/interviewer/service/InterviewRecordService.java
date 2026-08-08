package org.interviewer.service;

import org.interviewer.entity.InterviewRecord;
import org.interviewer.utils.PagedGridResult;

public interface InterviewRecordService {

    /**
     * Save interview result
     * @param interviewRecord
     */
    public void save(InterviewRecord interviewRecord);

    /**
     * Check if candidate has been interviewed
     * @param candidateId
     * @return boolean
     */
    public boolean isCandidateRecordExist(String candidateId);

    /**
     * Paginated query interview result list with conditions
     * @param realName
     * @param mobile
     * @param page
     * @param pageSize
     * @return PagedGridResult
     */
    public PagedGridResult queryList(String realName, String mobile, Integer page, Integer pageSize);
}
