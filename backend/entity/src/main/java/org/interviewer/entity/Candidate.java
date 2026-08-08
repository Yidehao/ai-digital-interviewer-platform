package org.interviewer.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * Candidate table
 * </p>
 */
public class Candidate implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * Real name (encryption required)
     */
    private String realName;

    /**
     * Candidate ID number (SSN)
     */
    private String identityNum;

    /**
     * Candidate mobile phone number
     */
    private String mobile;

    /**
     * Gender, 1:Male 0:Female 2:Not specified
     */
    private Integer sex;

    /**
     * Candidate photo
     */
    private String face;

    /**
     * Email
     */
    private String email;

    /**
     * Birthday
     */
    private LocalDate birthday;

    /**
     * Country
     */
    private String country;

    /**
     * State
     */
    private String state;

    /**
     * City
     */
    private String city;

    /**
     * County
     */
    private String county;

    /**
     * Address
     */
    private String address;

    /**
     * Applied job primary key id
     */
    private String jobId;

    /**
     * Remarks
     */
    private String remark;

    /**
     * Created time
     */
    private LocalDateTime createdTime;

    /**
     * Updated time
     */
    private LocalDateTime updatedTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getIdentityNum() {
        return identityNum;
    }

    public void setIdentityNum(String identityNum) {
        this.identityNum = identityNum;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public String getFace() {
        return face;
    }

    public void setFace(String face) {
        this.face = face;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    @Override
    public String toString() {
        return "Candidate{" +
                "id = " + id +
                ", realName = " + realName +
                ", identityNum = " + identityNum +
                ", mobile = " + mobile +
                ", sex = " + sex +
                ", face = " + face +
                ", email = " + email +
                ", birthday = " + birthday +
                ", country = " + country +
                ", state = " + state +
                ", city = " + city +
                ", county = " + county +
                ", address = " + address +
                ", jobId = " + jobId +
                ", remark = " + remark +
                ", createdTime = " + createdTime +
                ", updatedTime = " + updatedTime +
                "}";
    }
}