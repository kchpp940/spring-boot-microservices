package com.safalifter.userservice.model;

import com.safalifter.userservice.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;

@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferences {

    @Builder.Default
    private boolean offerEnabled = true;

    @Builder.Default
    private boolean jobUpdateEnabled = true;

    @Builder.Default
    private boolean systemMessageEnabled = true;

    public boolean isEnabled(NotificationType type) {
        switch (type) {
            case OFFER:
                return offerEnabled;
            case JOB_UPDATE:
                return jobUpdateEnabled;
            case SYSTEM_MESSAGE:
                return systemMessageEnabled;
            default:
                return true;
        }
    }

    public void setPreference(NotificationType type, boolean enabled) {
        switch (type) {
            case OFFER:
                this.offerEnabled = enabled;
                break;
            case JOB_UPDATE:
                this.jobUpdateEnabled = enabled;
                break;
            case SYSTEM_MESSAGE:
                this.systemMessageEnabled = enabled;
                break;
        }
    }
}
