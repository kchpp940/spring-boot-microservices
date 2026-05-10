package com.safalifter.jobservice.model;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity(name = "favorites")
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"userId", "jobId"})
})
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Favorite extends BaseEntity {
    private String userId;
    private String jobId;
}
