package com.safalifter.gateway.config;

import com.safalifter.gateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
    private final JwtAuthenticationFilter filter;

    @Value("${gateway.routes.user-service.uri:lb://user-service}")
    private String userServiceUri;

    @Value("${gateway.routes.job-service.uri:lb://job-service}")
    private String jobServiceUri;

    @Value("${gateway.routes.notification-service.uri:lb://notification-service}")
    private String notificationServiceUri;

    @Value("${gateway.routes.auth-service.uri:lb://auth-service}")
    private String authServiceUri;

    @Value("${gateway.routes.file-storage.uri:lb://file-storage}")
    private String fileStorageUri;

    public GatewayConfig(JwtAuthenticationFilter filter) {
        this.filter = filter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r.path("/v1/user/**")
                        .filters(f -> f.filter(filter))
                        .uri(userServiceUri))

                .route("job-service", r -> r.path("/v1/job-service/**")
                        .filters(f -> f.filter(filter))
                        .uri(jobServiceUri))

                .route("notification-service", r -> r.path("/v1/notification/**")
                        .filters(f -> f.filter(filter))
                        .uri(notificationServiceUri))

                .route("auth-service", r -> r.path("/v1/auth/**")
                        .uri(authServiceUri))

                .route("file-storage", r -> r.path("/v1/file-storage/**")
                        .filters(f -> f.filter(filter))
                        .uri(fileStorageUri))
                .build();
    }
}