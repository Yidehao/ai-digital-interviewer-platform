package org.interviewer.exception;

import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GraceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GraceExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public GraceJSONResult returnMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("Upload exceeded the configured maximum size", e);
        return GraceJSONResult.exception(ResponseStatusEnum.FILE_MAX_SIZE_500KB_ERROR);
    }

    @ExceptionHandler(MyCustomException.class)
    @ResponseBody
    public GraceJSONResult returnMyCustomException(MyCustomException e) {
        log.warn("Business exception: {}", e.getResponseStatusEnum().msg(), e);
        return GraceJSONResult.exception(e.getResponseStatusEnum());
    }

    /**
     * Catch-all so that an unexpected exception still comes back in the standard envelope.
     *
     * Without this, anything not matched above escapes as a Spring whitelabel error page with a
     * non-200 status, which every client here mis-parses - they all read result.data.status.
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public GraceJSONResult returnUnhandledException(Exception e) {
        log.error("Unhandled exception", e);
        return GraceJSONResult.exception(ResponseStatusEnum.SYSTEM_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public GraceJSONResult returnNotValidException(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        Map<String, String> errors = getErrors(result);
        return GraceJSONResult.errorMap(errors);
    }

    public Map<String, String> getErrors(BindingResult result) {

        Map<String, String> map = new HashMap<>();

        List<FieldError> errorList = result.getFieldErrors();
        for (FieldError fe : errorList) {
            // Field name corresponding to the error
            String field = fe.getField();
            // Error message
            String message = fe.getDefaultMessage();

            map.put(field, message);
        }

        return map;
    }

}
