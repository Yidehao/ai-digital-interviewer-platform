package org.interviewer.mapper;

import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.entity.vo.QuestionLibVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * Interview question library table (each digital interviewer corresponds to some interview questions) Mapper interface
 * </p>
 */
public interface QuestionLibMapperCustom {

    public List<QuestionLibVO> queryQuestionLibList(@Param("paramMap") Map<String, Object> map);

    /**
     * Random enabled questions belonging to one interviewer. Returns no reference answers -
     * the result travels to the candidate's device.
     */
    public List<InitQuestionsVO> queryRandomQuestions(@Param("paramMap") Map<String, Object> map);

}
