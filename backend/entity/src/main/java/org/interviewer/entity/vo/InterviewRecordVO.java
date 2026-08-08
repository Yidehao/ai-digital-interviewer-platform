package org.interviewer.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class InterviewRecordVO {

    private String interviewRecordId;
    private String candidateId;

    private String result;
    private String answerContent;
    private Integer takeTime;
    private LocalDate createTime;

    private String realName;
    private String identityNum;
    private String mobile;
    private Integer sex;
    private String email;

    private String jobId;
    private String jobName;

}
