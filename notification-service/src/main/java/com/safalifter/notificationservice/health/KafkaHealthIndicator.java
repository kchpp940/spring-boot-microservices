package com.safalifter.notificationservice.health;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    @Value("${spring.kafka.bootstrap-servers:${spring.kafka.consumer.bootstrap-servers:}}")
    private String bootstrapServers;

    private static final long TIMEOUT_SECONDS = 5;

    @Override
    public Health health() {
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            return Health.down()
                    .withDetail("status", "not_configured")
                    .withDetail("error", "spring.kafka.bootstrap-servers not configured")
                    .build();
        }

        try (AdminClient adminClient = createAdminClient()) {
            ListTopicsOptions options = new ListTopicsOptions().timeoutMs((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            adminClient.listTopics(options).names().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("status", "connected")
                    .build();
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("status", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private AdminClient createAdminClient() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        return AdminClient.create(props);
    }
}
