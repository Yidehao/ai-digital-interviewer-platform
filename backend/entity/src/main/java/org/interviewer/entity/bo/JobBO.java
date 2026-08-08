package org.interviewer.entity.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class JobBO {

    private String id;
    private String jobName;
    private String jobDesc;
    private Integer status;
    private String interviewerId;
    private String prompt;

}
