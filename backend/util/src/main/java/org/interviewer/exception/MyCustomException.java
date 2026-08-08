package org.interviewer.exception;

import org.interviewer.grace.result.ResponseStatusEnum;

/**
 * Custom exception
 * Purpose: Unified exception handling
 *          Facilitate decoupling, decouple exception information from interceptors, services, controllers, etc.
 *          Not limited by service return types
 */
public class MyCustomException extends RuntimeException {

    private ResponseStatusEnum responseStatusEnum;

    public MyCustomException(ResponseStatusEnum responseStatusEnum) {
        super("Exception status code: " + responseStatusEnum.status() +
                " Exception message: " + responseStatusEnum.msg());
        this.responseStatusEnum = responseStatusEnum;
    }

    public ResponseStatusEnum getResponseStatusEnum() {
        return responseStatusEnum;
    }
    public void setResponseStatusEnum(ResponseStatusEnum responseStatusEnum) {
        this.responseStatusEnum = responseStatusEnum;
    }
}
