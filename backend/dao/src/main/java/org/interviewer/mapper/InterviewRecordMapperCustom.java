package org.interviewer.mapper;

import org.interviewer.entity.vo.InterviewRecordVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface InterviewRecordMapperCustom {

    public List<InterviewRecordVO> queryList(@Param("paramMap") Map<String, Object> map);

}
