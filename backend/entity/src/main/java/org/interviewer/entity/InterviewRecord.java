package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * Interview record table
 * </p>
 */
@TableName("interview_record")
public class InterviewRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * Candidate id
     */
    private String candidateId;

    /**
     * Job name, snapshot name, original field may be changed
     */
    private String jobName;

    /**
     * Candidate's answer content
     */
    private String answerContent;

    /**
     * Total time spent on the entire interview, unit: seconds
     */
    private Integer takeTime;

    /**
     * Interview result details
     */
    private String result;

    private LocalDateTime createTime;

    private LocalDateTime updatedTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getAnswerContent() {
        return answerContent;
    }

    public void setAnswerContent(String answerContent) {
        this.answerContent = answerContent;
    }

    public Integer getTakeTime() {
        return takeTime;
    }

    public void setTakeTime(Integer takeTime) {
        this.takeTime = takeTime;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "InterviewRecord{" +
                "id = " + id +
                ", candidateId = " + candidateId +
                ", jobName = " + jobName +
                ", answerContent = " + answerContent +
                ", takeTime = " + takeTime +
                ", result = " + result +
                ", createTime = " + createTime +
                ", updatedTime = " + updatedTime +
                "}";
    }
}