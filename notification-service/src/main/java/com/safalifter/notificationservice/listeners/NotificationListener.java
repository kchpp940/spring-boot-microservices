package com.safalifter.notificationservice.listeners;

import com.safalifter.notificationservice.config.trace.TraceIdUtil;
import com.safalifter.notificationservice.request.SendNotificationRequest;
import com.safalifter.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {
    private final NotificationService notificationService;

    @KafkaListener(topics = {"${spring.kafka.topic.name}"}, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(final SendNotificationRequest request) {
        try {
            String traceId = request.getTraceId();
            if (traceId == null || traceId.isEmpty()) {
                log.debug("Kafka message has no traceId, generating new one for backwards compatibility");
            }
            TraceIdUtil.setTraceId(traceId);
            log.info("Consumed Kafka message: userId={}, offerId={}, type={}, traceId={}",
                    request.getUserId(), request.getOfferId(), request.getNotificationType(), TraceIdUtil.getTraceId());
            notificationService.save(request);
        } finally {
            TraceIdUtil.clearTraceId();
        }
    }
}