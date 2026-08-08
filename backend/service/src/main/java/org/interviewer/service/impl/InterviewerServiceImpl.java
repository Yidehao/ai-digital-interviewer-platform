package org.interviewer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.interviewer.exception.GraceException;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.mapper.InterviewerMapper;
import org.interviewer.entity.Interviewer;
import org.interviewer.entity.bo.InterviewerBO;
import org.interviewer.service.InterviewerService;
import org.interviewer.service.JobService;
import org.interviewer.service.QuestionLibService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InterviewerServiceImpl
 **/
@Service
public class InterviewerServiceImpl implements InterviewerService {

    @Resource
    private InterviewerMapper interviewerMapper;

    @Resource
    private JobService jobService;

    @Resource
    private QuestionLibService questionLibService;

    @Override
    public void createOrUpdate(InterviewerBO interviewerBO) {

        Interviewer interviewer = new Interviewer();
        BeanUtils.copyProperties(interviewerBO, interviewer);
        interviewer.setUpdatedTime(LocalDateTime.now());

        if (StringUtils.isBlank(interviewer.getId())) {
            interviewer.setCreateTime(LocalDateTime.now());
            interviewerMapper.insert(interviewer);
        } else {
            interviewerMapper.updateById(interviewer);
        }

    }

    @Override
    public List<Interviewer> queryAll() {
        return interviewerMapper.selectList(
                new QueryWrapper<Interviewer>()
                        .orderByDesc("updated_time")
        );
    }

    @Override
    public void delete(String interviewerId) {

        // Before deleting interviewer, need to check if there are jobs and question libraries in use (multi-table association requires deleting associated data)
        boolean jobFlag = jobService.isJobContainInterviewer(interviewerId);
        boolean questionFlag = questionLibService.isQuestionLibContainInterviewer(interviewerId);

        if (jobFlag || questionFlag) {
            GraceException.display(ResponseStatusEnum.CAN_NOT_DELETE_INTERVIEWER);
        }

        interviewerMapper.deleteById(interviewerId);
    }



}