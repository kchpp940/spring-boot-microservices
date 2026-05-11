package com.safalifter.jobservice.config;

import com.safalifter.jobservice.client.CustomErrorDecoder;
import com.safalifter.jobservice.config.trace.TraceIdFeignInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new TraceIdFeignInterceptor();
    }
}
