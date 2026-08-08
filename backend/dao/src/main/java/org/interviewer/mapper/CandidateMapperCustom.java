package org.interviewer.mapper;

import org.interviewer.entity.bo.CandidateBO;
import org.interviewer.entity.vo.CandidateVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface CandidateMapperCustom {

    public List<CandidateVO> queryCandidateList(@Param("paramMap") Map<String, Object> map);

}
