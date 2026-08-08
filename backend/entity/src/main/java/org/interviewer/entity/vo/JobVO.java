package org.interviewer.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class JobVO {

    private String jobId;
    private String jobName;
    private String jobDesc;
    private Integer status;
    private String interviewerId;
    private String prompt;
    private String interviewerName;
    private LocalDateTime createTime;
    private LocalDateTime updatedTime;

}
