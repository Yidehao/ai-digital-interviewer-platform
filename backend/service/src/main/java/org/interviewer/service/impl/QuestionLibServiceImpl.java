package org.interviewer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import org.interviewer.base.BaseInfoProperties;
import org.interviewer.enums.YesOrNo;
import org.interviewer.mapper.QuestionLibMapper;
import org.interviewer.mapper.QuestionLibMapperCustom;
import org.interviewer.entity.Candidate;
import org.interviewer.entity.Job;
import org.interviewer.entity.QuestionLib;
import org.interviewer.entity.bo.QuestionLibBO;
import org.interviewer.entity.vo.InitQuestionsVO;
import org.interviewer.entity.vo.QuestionLibVO;
import org.interviewer.service.CandidateService;
import org.interviewer.service.JobService;
import org.interviewer.service.QuestionLibService;
import org.interviewer.utils.PagedGridResult;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * QuestionLibServiceImpl
 * @Description QuestionLibServiceImpl
 **/
@Service
public class QuestionLibServiceImpl extends BaseInfoProperties implements QuestionLibService {

    @Resource
    private QuestionLibMapper questionLibMapper;

    @Resource
    private QuestionLibMapperCustom questionLibMapperCustom;

    @Resource
    private CandidateService candidateService;

    @Resource
    private JobService jobService;

    @Override
    public void createOrUpdate(QuestionLibBO questionLibBO) {

        QuestionLib questionLib = new QuestionLib();
        BeanUtils.copyProperties(questionLibBO, questionLib);
        questionLib.setUpdatedTime(LocalDateTime.now());

        if (StringUtils.isBlank(questionLib.getId())) {
            questionLib.setIsOn(YesOrNo.YES.type);
            questionLib.setCreateTime(LocalDateTime.now());
            questionLibMapper.insert(questionLib);
        } else {
            questionLibMapper.updateById(questionLib);
        }

    }

    @Override
    public PagedGridResult queryList(String aiName, String question, Integer page, Integer pageSize) {

        PageHelper.startPage(page, pageSize);

        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isNotBlank(aiName)) {
            map.put("aiName", aiName);
        }
        if (StringUtils.isNotBlank(question)) {
            map.put("question", question);
        }

        List<QuestionLibVO> list =  questionLibMapperCustom.queryQuestionLibList(map);

        return setterPagedGrid(list, page);
    }

    @Override
    public void setDisplayOrNot(String questionLibId, Integer isOn) {

        QuestionLib questionLib = new QuestionLib();
        questionLib.setId(questionLibId);
        questionLib.setIsOn(isOn);
        questionLib.setUpdatedTime(LocalDateTime.now());

        questionLibMapper.updateById(questionLib);
    }

    @Override
    public void delete(String questionLibId) {
        questionLibMapper.deleteById(questionLibId);
    }

    @Override
    public boolean isQuestionLibContainInterviewer(String InterviewerId) {
        QueryWrapper<QuestionLib> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("interviewer_id", InterviewerId);

        Long counts = questionLibMapper.selectCount(queryWrapper);

        return counts > 0 ? true : false;
    }

    @Override
    public List<InitQuestionsVO> getRandomQuestions(String candidateId, Integer questionNum) {

        Candidate candidate = candidateService.getDetail(candidateId);
        if (candidate == null || StringUtils.isBlank(candidate.getJobId())) {
            return new ArrayList<>();
        }

        Job job = jobService.getDetail(candidate.getJobId());
        if (job == null || StringUtils.isBlank(job.getInterviewerId())) {
            return new ArrayList<>();
        }

        return getAvailableQuestions(job.getInterviewerId(), questionNum, null);
    }

    @Override
    public List<InitQuestionsVO> getAvailableQuestions(String interviewerId,
                                                       Integer questionNum,
                                                       Collection<String> excludeIds) {

        if (StringUtils.isBlank(interviewerId) || questionNum == null || questionNum <= 0) {
            return new ArrayList<>();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("interviewerId", interviewerId);
        map.put("num", questionNum);
        if (excludeIds != null && !excludeIds.isEmpty()) {
            map.put("excludeIds", excludeIds);
        }

        // Single query: the database does the randomisation and the limit. Asking for more
        // questions than the bank holds simply returns the whole bank.
        List<InitQuestionsVO> questions = questionLibMapperCustom.queryRandomQuestions(map);
        return questions == null ? new ArrayList<>() : questions;
    }

    @Override
    public Map<String, String> getReferenceAnswers(Collection<String> questionIds) {

        if (questionIds == null || questionIds.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> ids = questionIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }

        List<QuestionLib> rows = questionLibMapper.selectBatchIds(ids);
        Map<String, String> referenceAnswers = new HashMap<>();
        for (QuestionLib row : rows) {
            if (row != null && StringUtils.isNotBlank(row.getReferenceAnswer())) {
                referenceAnswers.put(row.getId(), row.getReferenceAnswer());
            }
        }
        return referenceAnswers;
    }
}
