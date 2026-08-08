package org.interviewer.exception;

import org.interviewer.grace.result.ResponseStatusEnum;

/**
 * Gracefully handle exceptions, unified encapsulation
 */
public class GraceException {

    public static void display(ResponseStatusEnum statusEnum) {
        throw new MyCustomException(statusEnum);
    }

}
