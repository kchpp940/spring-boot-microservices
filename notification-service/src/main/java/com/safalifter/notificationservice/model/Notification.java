package com.safalifter.notificationservice.model;

import com.safalifter.notificationservice.enums.NotificationType;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "notifications")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;

    private String userId;
    private String offerId;
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @CreationTimestamp
    private LocalDateTime creationTimestamp;
}
