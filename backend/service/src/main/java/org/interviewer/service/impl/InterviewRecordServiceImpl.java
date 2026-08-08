package org.interviewer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import org.interviewer.base.BaseInfoProperties;
import org.interviewer.mapper.InterviewRecordMapper;
import org.interviewer.mapper.InterviewRecordMapperCustom;
import org.interviewer.entity.InterviewRecord;
import org.interviewer.entity.vo.CandidateVO;
import org.interviewer.entity.vo.InterviewRecordVO;
import org.interviewer.service.InterviewRecordService;
import org.interviewer.utils.PagedGridResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InterviewRecordServiceImpl
 **/
@Service
public class InterviewRecordServiceImpl extends BaseInfoProperties implements InterviewRecordService {

    @Resource
    private InterviewRecordMapper interviewRecordMapper;

    @Resource
    private InterviewRecordMapperCustom interviewRecordMapperCustom;

    @Override
    public void save(InterviewRecord interviewRecord) {
        interviewRecordMapper.insert(interviewRecord);
    }

    @Override
    public boolean isCandidateRecordExist(String candidateId) {

        List<InterviewRecord> list = interviewRecordMapper.selectList(
                new QueryWrapper<InterviewRecord>()
                        .eq("candidate_id",candidateId)
        );

        if (list.isEmpty() || list.size() == 0) return false;

        return true;
    }

    @Override
    public PagedGridResult queryList(String realName, String mobile, Integer page, Integer pageSize) {

        PageHelper.startPage(page, pageSize);

        Map<String, Object> map = new HashMap<>();
        map.put("realName", realName);
        map.put("mobile", mobile);

        List<InterviewRecordVO> list = interviewRecordMapperCustom.queryList(map);
        return setterPagedGrid(list, page);
    }
}
