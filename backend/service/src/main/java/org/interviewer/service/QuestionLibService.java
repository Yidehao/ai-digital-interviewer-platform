package org.interviewer.service;

import org.interviewer.entity.bo.QuestionLibBO;
import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.utils.PagedGridResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * Random enabled questions for one interviewer, optionally skipping ids already served.
     *
     * @param interviewerId owning digital interviewer
     * @param questionNum   how many to return; fewer come back if the bank is smaller
     * @param excludeIds    question ids to skip, may be null or empty
     * @return List<InitQuestionsVO>
     */
    public List<InitQuestionsVO> getAvailableQuestions(String interviewerId,
                                                       Integer questionNum,
                                                       Collection<String> excludeIds);

    /**
     * Resolve reference answers server-side, keyed by question id.
     *
     * Reference answers are never sent to the candidate, so they cannot be read back off the
     * submitted payload - grading looks them up here instead.
     *
     * @param questionIds question library ids
     * @return map of question id to reference answer; ids with no row are absent
     */
    public Map<String, String> getReferenceAnswers(Collection<String> questionIds);
}
