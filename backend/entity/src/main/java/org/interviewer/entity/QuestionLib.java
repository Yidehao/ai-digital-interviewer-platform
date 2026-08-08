package org.interviewer.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * Interview question library table (each digital interviewer corresponds to
 * some interview questions)
 * </p>
 */
@TableName("question_lib")
public class QuestionLib implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * Interview question (text content)
     */
    private String question;

    /**
     * Reference answer
     */
    private String referenceAnswer;

    /**
     * Address corresponding to the interview digital person
     */
    private String aiSrc;

    /**
     * Assigned digital interviewer id, each job needs a corresponding interviewer
     * to conduct interviews
     */
    private String interviewerId;

    /**
     * 1: Enable this question
     * 0: Disable this question
     */
    private Integer isOn;

    private LocalDateTime createTime;

    private LocalDateTime updatedTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }

    public String getAiSrc() {
        return aiSrc;
    }

    public void setAiSrc(String aiSrc) {
        this.aiSrc = aiSrc;
    }

    public String getInterviewerId() {
        return interviewerId;
    }

    public void setInterviewerId(String interviewerId) {
        this.interviewerId = interviewerId;
    }

    public Integer getIsOn() {
        return isOn;
    }

    public void setIsOn(Integer isOn) {
        this.isOn = isOn;
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
        return "QuestionLib{" +
                "id = " + id +
                ", question = " + question +
                ", referenceAnswer = " + referenceAnswer +
                ", aiSrc = " + aiSrc +
                ", interviewerId = " + interviewerId +
                ", isOn = " + isOn +
                ", createTime = " + createTime +
                ", updatedTime = " + updatedTime +
                "}";
    }
}