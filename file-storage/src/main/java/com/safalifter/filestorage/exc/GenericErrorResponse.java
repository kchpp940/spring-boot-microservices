package com.safalifter.filestorage.exc;

import com.safalifter.filestorage.config.trace.TraceIdUtil;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Builder
@Getter
public class GenericErrorResponse extends RuntimeException {
    private final String message;
    private final HttpStatus httpStatus;
    private final String traceId;

    @java.beans.ConstructorProperties({"message", "httpStatus", "traceId"})
    GenericErrorResponse(String message, HttpStatus httpStatus, String traceId) {
        super(message);
        this.message = message;
        this.httpStatus = httpStatus;
        this.traceId = traceId != null ? traceId : TraceIdUtil.getTraceId();
    }

    public GenericErrorResponse(String message, HttpStatus httpStatus) {
        this(message, httpStatus, TraceIdUtil.getTraceId());
    }
}
