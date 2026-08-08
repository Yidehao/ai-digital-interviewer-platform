package org.interviewer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import org.interviewer.base.BaseInfoProperties;
import org.interviewer.enums.YesOrNo;
import org.interviewer.mapper.QuestionLibMapper;
import org.interviewer.mapper.QuestionLibMapperCustom;
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

        // 1. Get interviewer responsible for interviewing the candidate
        String jobId = candidateService.getDetail(candidateId).getJobId();
        String interviewerId = jobService.getDetail(jobId).getInterviewerId();

        // 2. Get total number of interview questions based on interviewer
        Long questionCounts = questionLibMapper.selectCount(
            new QueryWrapper<QuestionLib>()
                    .eq("interviewer_id", interviewerId)
        );

        // 题库为空时直接返回空列表
        if (questionCounts == null || questionCounts == 0) {
            return new ArrayList<>();
        }

        // 3. 若题库题目数少于请求数量，则全部返回；否则随机选取 questionNum 道
        int actualNum = (int) Math.min(questionNum, questionCounts);
        List<Long> randomList = new ArrayList<>();

        if (actualNum >= questionCounts) {
            // 全部题目都要返回：0 到 questionCounts-1，然后打乱顺序
            for (long i = 0; i < questionCounts; i++) {
                randomList.add(i);
            }
            Collections.shuffle(randomList);
        } else {
            // 随机选取 actualNum 个不重复的索引
            Random random = new Random();
            Set<Long> indexSet = new HashSet<>();
            while (indexSet.size() < actualNum) {
                indexSet.add(random.nextLong(questionCounts));
            }
            randomList.addAll(indexSet);
            Collections.shuffle(randomList);
        }

        // 4. Get interview questions from database based on index
        List<InitQuestionsVO> questionList = new ArrayList<>();
        for (Long l : randomList) {
            Map<String, Object> map = new HashMap<>();
            map.put("indexNum", l);

            InitQuestionsVO question = questionLibMapperCustom.queryRandomQuestion(map);
            questionList.add(question);
        }

        return questionList;
    }
}
