package org.interviewer.mapper;

import org.interviewer.entity.vo.JobVO;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface JobMapperCustom {

    public List<JobVO> queryJobList(@Param("paramMap") Map<String, Object> map);

    public List<HashMap<String, String>> queryNameList(@Param("paramMap") Map<String, Object> map);
}
