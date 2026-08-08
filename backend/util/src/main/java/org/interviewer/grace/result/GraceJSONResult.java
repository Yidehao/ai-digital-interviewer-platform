package org.interviewer.grace.result;

import java.util.Map;

/**
 * Custom response data type enum upgraded version
 * Custom response data structure
 * This class can be provided to H5/iOS/Android/WeChat Official Account/Mini Program for use
 * After the frontend receives this type of data (json object), it can implement related functions according to business needs
 */
public class GraceJSONResult {

    // Response business status code
    private Integer status;

    // Response message
    private String msg;

    // Whether successful
    private Boolean success;

    // Response data, can be Object, List or Map, etc.
    private Object data;

    /**
     * Success return with data, directly pass data to OK method
     * @param data
     * @return
     */
    public static GraceJSONResult ok(Object data) {
        return new GraceJSONResult(data);
    }
    /**
     * Success return without data, directly call ok method, data does not need to be passed (actually null)
     * @return
     */
    public static GraceJSONResult ok() {
        return new GraceJSONResult(ResponseStatusEnum.SUCCESS);
    }
    public GraceJSONResult(Object data) {
        this.status = ResponseStatusEnum.SUCCESS.status();
        this.msg = ResponseStatusEnum.SUCCESS.msg();
        this.success = ResponseStatusEnum.SUCCESS.success();
        this.data = data;
    }


    /**
     * Error return, directly call error method, of course you can also customize errors in ResponseStatusEnum and then return
     * @return
     */
    public static GraceJSONResult error() {
        return new GraceJSONResult(ResponseStatusEnum.FAILED);
    }

    /**
     * Error return, map contains multiple error messages, can be used for form or BO object validation, return all errors uniformly
     * @param map
     * @return
     */
    public static GraceJSONResult errorMap(Map map) {
        return new GraceJSONResult(ResponseStatusEnum.BO_FAILED, map);
    }

    /**
     * Error return, directly return error message
     * @param msg
     * @return
     */
    public static GraceJSONResult errorMsg(String msg) {
        return new GraceJSONResult(ResponseStatusEnum.FAILED, msg);
    }

    /**
     * Error return, token exception, some common ones can be uniformly defined here
     * @return
     */
    public static GraceJSONResult errorTicket() {
        return new GraceJSONResult(ResponseStatusEnum.TICKET_INVALID);
    }

    /**
     * Custom error range, need to pass in a custom enum, can customize in ResponseStatusEnum.java and then pass in
     * @param responseStatus
     * @return
     */
    public static GraceJSONResult errorCustom(ResponseStatusEnum responseStatus) {
        return new GraceJSONResult(responseStatus);
    }
    public static GraceJSONResult exception(ResponseStatusEnum responseStatus) {
        return new GraceJSONResult(responseStatus);
    }

    public GraceJSONResult(ResponseStatusEnum responseStatus) {
        this.status = responseStatus.status();
        this.msg = responseStatus.msg();
        this.success = responseStatus.success();
    }
    public GraceJSONResult(ResponseStatusEnum responseStatus, Object data) {
        this.status = responseStatus.status();
        this.msg = responseStatus.msg();
        this.success = responseStatus.success();
        this.data = data;
    }
    public GraceJSONResult(ResponseStatusEnum responseStatus, String msg) {
        this.status = responseStatus.status();
        this.msg = msg;
        this.success = responseStatus.success();
    }

    public GraceJSONResult() {
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}