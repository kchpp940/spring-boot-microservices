package com.safalifter.userservice.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class FeignClientsHealthIndicator implements HealthIndicator {

    private static final List<String> FEIGN_SERVICES = List.of("file-storage");
    private static final String LIVENESS_ENDPOINT = "/actuator/health/liveness";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;

    @Autowired
    private DiscoveryClient discoveryClient;

    public FeignClientsHealthIndicator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean allUp = true;

        for (String serviceName : FEIGN_SERVICES) {
            try {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

                if (instances == null || instances.isEmpty()) {
                    builder.withDetail(serviceName, "UNAVAILABLE (no instances in Eureka)");
                    allUp = false;
                    log.warn("Feign service {}: no instances found in Eureka", serviceName);
                    continue;
                }

                ServiceInstance instance = instances.get(0);
                String healthUrl = instance.getUri().toString() + LIVENESS_ENDPOINT;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .timeout(TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    builder.withDetail(serviceName, "READY (instances: " + instances.size() + ")");
                } else {
                    builder.withDetail(serviceName, "NOT_READY (HTTP " + statusCode + ", instances: " + instances.size() + ")");
                    allUp = false;
                    log.warn("Feign service {}: readiness check returned HTTP {}", serviceName, statusCode);
                }

            } catch (Exception e) {
                log.error("Feign service {} health check failed: {}", serviceName, e.getMessage());
                builder.withDetail(serviceName, "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                allUp = false;
            }
        }

        builder.withDetail("strategy", "call downstream /actuator/health/liveness (avoids readiness chain)");
        builder.withDetail("checkedServices", FEIGN_SERVICES.size());

        if (!allUp) {
            return Health.down()
                    .withDetails(builder.build().getDetails())
                    .build();
        }

        return builder.build();
    }
}
