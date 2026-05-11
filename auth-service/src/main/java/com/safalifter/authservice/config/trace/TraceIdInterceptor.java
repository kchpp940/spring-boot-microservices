package com.safalifter.authservice.config.trace;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TraceIdUtil.TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.generateTraceId();
        }
        TraceIdUtil.setTraceId(traceId);
        response.setHeader(TraceIdUtil.TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TraceIdUtil.clearTraceId();
    }
}
