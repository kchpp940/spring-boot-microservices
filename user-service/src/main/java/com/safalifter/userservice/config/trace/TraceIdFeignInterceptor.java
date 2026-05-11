package com.safalifter.userservice.config.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIdUtil.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            template.header(TraceIdUtil.TRACE_ID_HEADER, traceId);
        }
    }
}
