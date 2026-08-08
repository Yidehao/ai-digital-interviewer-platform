package org.interviewer.service;

import org.interviewer.entity.bo.QuestionLibBO;
import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.utils.PagedGridResult;

import java.util.List;

public interface QuestionLibService {

    /**
     * Create or update question library
     * @param questionLibBO
     */
    public void createOrUpdate(QuestionLibBO questionLibBO);

    /**
     * Paginated query question library list
     * @param aiName
     * @param question
     * @param page
     * @param pageSize
     * @return PagedGridResult
     */
    public PagedGridResult queryList(String aiName, String question, Integer page, Integer pageSize);

    /**
     * Enable or disable a specific interview question
     * @param questionLibId
     * @param isOn
     */
    public void setDisplayOrNot(String questionLibId, Integer isOn);

    /**
     * Delete interview question
     * @param questionLibId
     */
    public void delete(String questionLibId);

    /**
     * Check if all question libraries contain a specific interviewer
     * @param InterviewerId
     * @return boolean
     */
    public boolean isQuestionLibContainInterviewer(String InterviewerId);

    /**
     * Get specified number of random interview questions
     * @param candidateId
     * @param questionNum
     * @return List<InitQuestionsVO>
     */
    public List<InitQuestionsVO> getRandomQuestions(String candidateId, Integer questionNum);
}
