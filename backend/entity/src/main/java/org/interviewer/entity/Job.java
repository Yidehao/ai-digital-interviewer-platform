package org.interviewer.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * Job information table
 * </p>
 */
public class Job implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * Job name
     */
    private String jobName;

    /**
     * Job description
     */
    private String jobDesc;

    /**
     * 1: Job enabled
     * 2: Job disabled
     */
    private Integer status;

    /**
     * Assigned digital interviewer id, each job needs a corresponding interviewer to conduct interviews
     */
    private String interviewerId;

    /**
     * Prefix prompt for the interview results of this job sent to the AI model (e.g. Ollama).
     */
    private String prompt;

    private LocalDateTime createTime;

    private LocalDateTime updatedTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getInterviewerId() {
        return interviewerId;
    }

    public void setInterviewerId(String interviewerId) {
        this.interviewerId = interviewerId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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
        return "Job{" +
                "id = " + id +
                ", jobName = " + jobName +
                ", jobDesc = " + jobDesc +
                ", status = " + status +
                ", interviewerId = " + interviewerId +
                ", prompt = " + prompt +
                ", createTime = " + createTime +
                ", updatedTime = " + updatedTime +
                "}";
    }
}